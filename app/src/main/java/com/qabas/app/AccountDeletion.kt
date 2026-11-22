package com.qabas.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Batch 6 Priority 3 — real account deletion.
 *
 * The About screen used to claim full account deletion while no delete path
 * existed at all. Deletion is now real, and the UI reports exactly what
 * happened — it never claims completion for a step that did not run:
 *
 * 1. The server-side `delete-account` Edge Function
 *    (`supabase/functions/delete-account/index.ts`, deployed by the owner —
 *    see `supabase/seed/delete_account_APPLY.md`) is authoritative: using
 *    the service_role key it deletes the caller's Auth user and their
 *    user-owned rows (`users`, `user_settings`, `user_api_keys`,
 *    `user_push_tokens`, `saved_items`, `library_reading_state`, and their
 *    reel likes/bookmarks/history/comments). The anon key cannot delete its
 *    own Auth user, so without this function deletion can never be complete.
 * 2. The client additionally deletes its own rows directly
 *    (`SupabaseServices.Auth.deleteOwnAccountRows`) as a fallback and as
 *    defence-in-depth; each table is reported separately because RLS may
 *    legitimately deny some deletes.
 * 3. Local account data is cleared (session fields in `qabas_prefs` and the
 *    account-scoped preferences file) and the user is signed out.
 *
 * What is NOT deleted, stated honestly in the confirmation dialog: content
 * the user published as public (their reels stay public unless they delete
 * them first) and device-local files (library positions/bookmarks live in
 * `qabas_library_reading` on the device). The export-first step reuses the
 * existing JSON backup export (`UserPreferencesManager.exportBackupJson`).
 *
 * Real Supabase TOTP enrolment (MFA) is a separate follow-up and is not
 * claimed anywhere here.
 */
object AccountDeletionService {

    data class DeletionResult(
        /** The server function deleted the Auth user. Only this means "account deleted". */
        val authUserDeleted: Boolean,
        /** Per-table client-side row deletion results (best-effort fallback). */
        val ownRowsDeleted: Map<String, Boolean>,
        /** Local account data was cleared and the session signed out. */
        val localCleared: Boolean,
        /** Human-readable honest summary of what actually happened. */
        val summary: String
    )

    suspend fun deleteAccount(context: Context): DeletionResult = withContext(Dispatchers.IO) {
        val userId = SupabaseServices.Auth.getCurrentUserId()
            ?: return@withContext DeletionResult(
                authUserDeleted = false,
                ownRowsDeleted = emptyMap(),
                localCleared = false,
                summary = "لا توجد جلسة مسجّلة — لا يوجد حساب لحذفه على هذا الجهاز."
            )

        val serverDeleted = SupabaseServices.Auth.invokeDeleteAccountFunction()
        val ownRowsDeleted = SupabaseServices.Auth.deleteOwnAccountRows(userId)

        val localCleared = runCatching {
            clearLocalAccountData(context)
            SupabaseServices.Auth.logout()
            true
        }.getOrDefault(false)

        val summary = when {
            serverDeleted -> "تم حذف الحساب نهائياً من الخادم: حُذف حساب المصادقة وملفك الشخصي وإعداداتك وعناصرك المحفوظة وحالة القراءة ومفاتيحك. سُجّل خروجك من هذا الجهاز."
            ownRowsDeleted.values.any { it } -> "حُذف جزء من بيانات حسابك من السحابة، لكن حذف حساب المصادقة نفسه لم يكتمل لأن دالة الحذف على الخادم غير منشورة بعد. أعد المحاولة بعد نشر الدالة (supabase/seed/delete_account_APPLY.md) أو تواصل مع المطور. تم تسجيل خروجك محلياً."
            else -> "تعذّر حذف الحساب من الخادم. لم يُحذف حساب المصادقة. تحقق من الاتصال وأعد المحاولة، أو تواصل مع المطور."
        }
        DeletionResult(
            authUserDeleted = serverDeleted,
            ownRowsDeleted = ownRowsDeleted,
            localCleared = localCleared,
            summary = summary
        )
    }

    /**
     * Clears account-bound local data only. Device-local content that is not
     * tied to the account (library positions/bookmarks, hadith bookmarks,
     * theme/appearance) is left alone — it belongs to the device, not to the
     * deleted account.
     */
    fun clearLocalAccountData(context: Context) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("user_email", null)
        prefs.edit().apply {
            remove("is_logged_in")
            remove("is_guest")
            remove("is_admin")
            remove("is_developer")
            remove("is_relative")
            remove("user_id")
            remove("user_email")
            remove("user_name")
            remove("user_display_name")
            remove("user_custom_id")
            remove("user_tag")
            remove("user_account_number")
            remove("user_name_with_account")
            remove("saved_login_email")
            remove("email_confirmed")
            remove("auth_token")
            remove("refresh_token")
            remove("user_api_key")
            remove("guest_upgrade_pending")
            remove("guest_data_migrated_to")
            apply()
        }
        if (!email.isNullOrBlank()) {
            runCatching {
                AccountScopedPreferences.forEmail(context, email).edit().clear().apply()
            }
        }
    }
}

/**
 * "Delete account" card for Account Settings. Export-first (existing JSON
 * backup export), then an explicit confirmation dialog, then the real
 * deletion with an honest result message. Shown only to signed-in users.
 */
@Composable
fun AccountDeletionCard(modifier: Modifier = Modifier) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isExporting = true
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching { UserPreferencesManager.exportBackupJson(context) }.getOrNull()
            }
            if (json != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
                Toast.makeText(context, "تم تصدير نسختك الاحتياطية بنجاح", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "تعذر إنشاء النسخة الاحتياطية", Toast.LENGTH_LONG).show()
            }
            isExporting = false
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = theme.destructive, modifier = Modifier.size(28.dp)) },
            title = {
                Text(
                    text = "حذف الحساب نهائياً؟",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "سيُحذف حساب المصادقة وملفك الشخصي وإعداداتك وعناصرك المحفوظة وحالة القراءة ومفاتيحك نهائياً، ولا يمكن التراجع. المقاطع التي نشرتها تبقى محتوىً عاماً ما لم تحذفها بنفسك قبل ذلك. صدّر نسخة احتياطية أولاً إن أردت الاحتفاظ ببياناتك.",
                    color = theme.textPrimary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                ApplePrimaryButton(
                    text = if (isDeleting) "جاري الحذف..." else "نعم، احذف حسابي نهائياً",
                    isLoading = isDeleting,
                    onClick = {
                        if (isDeleting) return@ApplePrimaryButton
                        isDeleting = true
                        scope.launch {
                            val result = AccountDeletionService.deleteAccount(context)
                            isDeleting = false
                            showConfirmDialog = false
                            resultMessage = result.summary
                            Toast.makeText(context, result.summary, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }, enabled = !isDeleting) {
                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 13.sp)
                }
            },
            containerColor = theme.cardSurface
        )
    }

    Surface(
        color = theme.destructive.copy(alpha = if (theme.isDark) 0.14f else 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, theme.destructive.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = theme.destructive, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "حذف الحساب نهائياً",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Text(
                text = "يحذف حسابك وملفك الشخصي وبياناتك السحابية نهائياً. صدّر نسخة احتياطية (JSON) أولاً إن أردت الاحتفاظ ببياناتك.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            AppleSecondaryButton(
                text = if (isExporting) "جاري التصدير..." else "تصدير نسخة احتياطية أولاً (JSON)",
                enabled = !isExporting && !isDeleting,
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportLauncher.launch("qabas_backup_$stamp.json")
                }
            )
            ApplePrimaryButton(
                text = "حذف الحساب نهائياً",
                enabled = !isDeleting && !isExporting,
                isLoading = isDeleting,
                onClick = { showConfirmDialog = true }
            )
            if (resultMessage != null) {
                Text(
                    text = resultMessage.orEmpty(),
                    color = theme.textPrimary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
