package com.qabas.app

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Batch 6 Priority 3 — shared Google sign-in completion path.
 *
 * `GoogleAuthHelper` (Credential Manager + id token) and `GoogleSignInButton`
 * existed with no caller on the auth screens. Both `LoginScreen` and
 * `RegisterScreen` now call [signIn] instead of duplicating the flow.
 *
 * The flow is, in order:
 * 1. If the Google web client id is not configured, report [NotConfigured]
 *    — the screens show "تسجيل Google غير مهيأ بعد — تواصل مع المطور" and
 *    offer the developer setup dialog. Never a hard failure, never a fake
 *    success. The owner must set GOOGLE_WEB_CLIENT_ID (see `.env.example`
 *    and `app/build.gradle.kts`); no client id is invented here.
 * 2. Credential Manager returns a Google id token.
 * 3. Supabase Auth signs in with that id token (`loginWithGoogle`).
 * 4. The existing post-login path runs: `ensureUser` (creates the `users`
 *    row with a generated custom id / account number for first-time Google
 *    users), the active-session claim, role sync, local account registry and
 *    the `qabas_prefs` session fields — the same fields the email login
 *    writes, so the rest of the app cannot tell how the user signed in.
 * 5. If the user arrived as a guest, their local data is migrated to the
 *    new account id ([GuestUpgradeManager]).
 * 6. The email confirmation state is refreshed (Google confirms the address
 *    itself, so this is normally already true).
 *
 * GitHub OAuth stays developer-only: no GitHub button is added to any auth
 * screen by this flow.
 */
object GoogleSignInFlow {
    private const val TAG = "GoogleSignInFlow"

    /** Shown when Google sign-in is not configured yet — the owner must set the client id. */
    const val NOT_CONFIGURED_MESSAGE = "تسجيل Google غير مهيأ بعد — تواصل مع المطور"

    sealed interface Outcome {
        data class Success(
            val isAdmin: Boolean,
            val email: String,
            val emailConfirmed: Boolean,
            val guestMigrated: Boolean
        ) : Outcome

        data object NotConfigured : Outcome
        data object Cancelled : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun signIn(context: Context): Outcome = withContext(Dispatchers.IO) {
        if (!GoogleAuthHelper.isWebClientIdConfigured(context)) {
            return@withContext Outcome.NotConfigured
        }

        val wasGuest = GuestUpgradeManager.shouldMigrate(context)

        val googleResult = GoogleAuthHelper.signInWithGoogle(context)
        val googleCredentials = googleResult.getOrElse { error ->
            return@withContext when (error) {
                is GetCredentialCancellationException -> Outcome.Cancelled
                else -> Outcome.Failed(error.localizedMessage ?: "تعذر إتمام تسجيل الدخول عبر Google")
            }
        }

        val loginResult = SupabaseRepository.getInstance()
            .loginWithGoogle(googleCredentials.idToken)
        if (loginResult.isFailure) {
            Log.w(TAG, "Supabase Google login failed: ${loginResult.exceptionOrNull()?.message}")
            return@withContext Outcome.Failed(
                loginResult.exceptionOrNull()?.localizedMessage ?: "تعذر إتمام تسجيل الدخول عبر Google"
            )
        }

        val authRepository = SupabaseRepository.getInstance()
        val account = authRepository.authState.value.authenticatedSessionSnapshot()
            ?: return@withContext Outcome.Failed("تعذر تجهيز حالة الحساب الموثقة. تحقق من الاتصال ثم أعد المحاولة.")
        val authUid = account.userId
        val authEmail = account.email?.trim()?.lowercase(java.util.Locale.ROOT)?.takeIf { it.isNotBlank() }
            ?: return@withContext Outcome.Failed("تعذر قراءة بريد الحساب من حالة المصادقة. أعد المحاولة.")

        val displayName = googleCredentials.displayName?.takeIf { it.isNotBlank() }
            ?: authEmail.substringBefore('@')
        val cloudUser = SupabaseServices.Database.ensureUser(
            externalId = authUid,
            email = authEmail,
            name = displayName
        )
        val cloudCustomId = cloudUser?.customId?.takeIf { it.isNotBlank() }
        val accountNumber = cloudUser?.accountNumber?.takeIf { it.isNotBlank() }
        if (cloudCustomId == null || accountNumber == null) {
            runCatching { SupabaseServices.Auth.logout() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            return@withContext Outcome.Failed("بيانات الحساب الموثقة غير مكتملة. لم يتم إنهاء تسجيل الدخول.")
        }

        when (ActiveSessionManager.claim(context, authUid)) {
            ActiveSessionClaimResult.CLAIMED -> Unit
            ActiveSessionClaimResult.ACTIVE_ELSEWHERE -> {
                runCatching { ActiveSessionManager.endLocalSession(context) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                return@withContext Outcome.Failed("هذا الحساب نشط على جهاز آخر. سجّل الخروج منه ثم أعد المحاولة.")
            }
            ActiveSessionClaimResult.PROFILE_MISSING -> {
                runCatching { ActiveSessionManager.endLocalSession(context) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                return@withContext Outcome.Failed("تعذر العثور على ملف حسابك في السحابة. أعد المحاولة لاحقًا أو تواصل مع الدعم.")
            }
            ActiveSessionClaimResult.UNAVAILABLE -> {
                runCatching { ActiveSessionManager.endLocalSession(context) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                return@withContext Outcome.Failed("تعذر التحقق من جلستك الآن. تحقق من الإنترنت ثم أعد المحاولة.")
            }
        }

        val cloudRole = SupabaseServices.Auth.getUserRoleFromMetadata()
        val finalIsDeveloper = SupabaseServices.Auth.isDeveloperRole(cloudRole) ||
            CloudServices.isOwnerAccount(authEmail)
        val finalIsAdmin = account.isAdmin
        val userType = when {
            finalIsDeveloper -> "مطور"
            finalIsAdmin -> "خاص"
            else -> "مجاني"
        }
        val effectiveLoginName = cloudUser.name?.takeIf { it.isNotBlank() } ?: displayName
        val nameWithAccount = UserIdentifierManager.formatDisplayNameWithAccount(effectiveLoginName, accountNumber)

        UserIdentifierManager.saveCustomId(context, cloudCustomId, authEmail)
        UserIdentifierManager.saveAccountNumber(context, authEmail, accountNumber)
        LocalAccountRegistry.registerOrUpdateAccount(
            context = context,
            account = LocalAccountRegistry.AccountRecord(
                id = authUid,
                name = effectiveLoginName,
                email = authEmail,
                customId = cloudCustomId,
                accountNumber = accountNumber,
                type = userType,
                status = "active"
            )
        )

        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putBoolean("is_guest", false)
            putString("user_email", authEmail)
            putString("user_name", effectiveLoginName)
            putString("user_display_name", effectiveLoginName)
            putString("user_custom_id", cloudCustomId)
            putString("user_account_number", accountNumber)
            putString("user_name_with_account", nameWithAccount)
            remove("saved_login_email")
            apply()
        }

        CloudServices.syncAdminClaim(context)
        val emailConfirmed = SupabaseServices.Auth.refreshEmailConfirmationState() ?: true

        var guestMigrated = false
        if (wasGuest) {
            runCatching {
                GuestUpgradeManager.migrateGuestDataToAccount(
                    context = context,
                    newUserId = authUid,
                    newEmail = authEmail,
                    emailConfirmed = emailConfirmed
                )
                guestMigrated = true
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure { Log.w(TAG, "guest migration after Google sign-in failed: ${it.message}") }
        }

        Outcome.Success(
            isAdmin = finalIsAdmin,
            email = authEmail,
            emailConfirmed = emailConfirmed,
            guestMigrated = guestMigrated
        )
    }
}
