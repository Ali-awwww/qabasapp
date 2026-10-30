package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class RedemptionResult {
    SUCCESS_PROMO,
    SUCCESS_GIFT_CARD,
    INVALID_CODE,
    ALREADY_USED,
    EXPIRED,
    LIMIT_REACHED
}

/**
 * مدير الهدايا والأكواد.
 *
 * مصدر الحقيقة الوحيد للأكواد الصالحة هو Supabase (جدول promo_codes).
 * الأكواد المخصصة التي يضيفها المطوّر من لوحة الإدارة تُكتب في Supabase أولاً،
 * ثم تُخزن نسخة محلية مختصرة في SharedPreferences لتجنّب رحلات شبكة إضافية
 * عند الاسترداد المتكرر. كما يُحمَّل الكاش السحابي عند الإقلاع.
 */
class GiftManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("gift_manager_prefs", Context.MODE_PRIVATE)
    private val accountService = AppServices.getAccountService(context)

    // كاش الأكواد الصالحة المحمَّل من Supabase مرة واحدة. يُحدَّث بعد كل كتابة جديدة.
    @Volatile
    private var validPromoCodes: Map<String, Long> = emptyMap()
    @Volatile
    private var validGiftCards: Map<String, Int> = emptyMap()

    init {
        // تحميل أولي متزامن عند الإقلاع لتفادي سباق بين init والقراءة.
        runCatching {
            runBlocking {
                loadValidCodesFromCloud()
            }
        }.onFailure {
            Log.e(TAG, "Failed initial cloud load: ${it.message}", it)
        }
    }

    /**
     * يجلب الأكواد الصالحة من Supabase ويحدّث الكاش المحلي.
     * يستدعى عند الإقلاع (داخل init) وبعد كل عملية إضافة.
     */
    suspend fun loadValidCodesFromCloud() {
        if (SupabaseServices.isSupabaseAvailable) {
            runCatching {
                val rows = SupabaseServices.Database.getValidPromoCodes()
                if (rows.isNotEmpty()) {
                    val promos = mutableMapOf<String, Long>()
                    val gifts = mutableMapOf<String, Int>()
                    for (row in rows) {
                        val c = row.code.uppercase(Locale.US)
                        if (row.type == "GIFT") {
                            gifts[c] = row.value.toInt()
                        } else {
                            promos[c] = row.value * 24L * 60L * 60L * 1000L
                        }
                    }
                    validPromoCodes = promos
                    validGiftCards = gifts
                    Log.d(TAG, "Loaded ${promos.size} promo codes and ${gifts.size} gift cards from Supabase.")
                    return
                }
            }.onFailure { Log.w(TAG, "Supabase promo load failed: ${it.message}") }
        }

        validPromoCodes = emptyMap()
        validGiftCards = emptyMap()
        Log.d(TAG, "Supabase unavailable, using empty codes cache.")
    }

    private companion object {
        private const val TAG = "GiftManager"
    }

    /**
     * إضافة كود ترقية جديد.
     * - يكتب في Supabase أولاً مع await (لا fire-and-forget).
     * - يُحدّث الكاش المحلي عند النجاح.
     * - يحتفظ بنسخة محلية مختصرة في SharedPreferences كاحتياط.
     * - يُرجع true عند النجاح، false عند فشل الكتابة السحابية.
     */
    suspend fun addCustomPromoCode(code: String, durationDays: Int): Boolean {
        val cleanCode = code.trim().uppercase(Locale.US)
        var saved = false
        if (SupabaseServices.isSupabaseAvailable) {
            saved = SupabaseServices.Database.savePromoCode(cleanCode, "PROMO", durationDays.toLong())
        }
        if (!saved && !SupabaseServices.isSupabaseAvailable) {
            saved = true
        }
        if (!saved) return false
        // تحديث الكاش المحلي فوراً لإمكانية الاسترداد دون انتظار التحميل الكامل
        validPromoCodes = validPromoCodes + (cleanCode to durationDays.toLong() * 24L * 60L * 60L * 1000L)
        // نسخة احتياطية محلية
        val customPromos = prefs.getStringSet("custom_promos", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        customPromos.add("$cleanCode:$durationDays")
        prefs.edit().putStringSet("custom_promos", customPromos).apply()
        Log.d(TAG, "Promo code ($cleanCode) saved successfully.")
        return true
    }

    /**
     * إضافة بطاقة هدايا جديدة.
     * - يكتب في Supabase أولاً مع await (لا fire-and-forget).
     * - يُحدّث الكاش المحلي عند النجاح.
     * - يُرجع true عند النجاح، false عند فشل الكتابة السحابية.
     */
    suspend fun addCustomGiftCard(code: String, points: Int): Boolean {
        val cleanCode = code.trim().uppercase(Locale.US)
        var saved = false
        if (SupabaseServices.isSupabaseAvailable) {
            saved = SupabaseServices.Database.savePromoCode(cleanCode, "GIFT", points.toLong())
        }
        if (!saved && !SupabaseServices.isSupabaseAvailable) {
            saved = true
        }
        if (!saved) return false
        // تحديث الكاش المحلي فوراً
        validGiftCards = validGiftCards + (cleanCode to points)
        // نسخة احتياطية محلية
        val customGifts = prefs.getStringSet("custom_gifts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        customGifts.add("$cleanCode:$points")
        prefs.edit().putStringSet("custom_gifts", customGifts).apply()
        Log.d(TAG, "Gift card ($cleanCode) saved successfully.")
        return true
    }

    fun getCustomPromoCodes(): Map<String, Long> {
        val customPromos = prefs.getStringSet("custom_promos", mutableSetOf()) ?: mutableSetOf()
        val map = mutableMapOf<String, Long>()
        for (item in customPromos) {
            val parts = item.split(":")
            if (parts.size == 2) {
                map[parts[0]] = parts[1].toLongOrNull()?.times(24 * 60 * 60 * 1000L) ?: 0L
            }
        }
        return map
    }

    fun getCustomGiftCards(): Map<String, Int> {
        val customGifts = prefs.getStringSet("custom_gifts", mutableSetOf()) ?: mutableSetOf()
        val map = mutableMapOf<String, Int>()
        for (item in customGifts) {
            val parts = item.split(":")
            if (parts.size == 2) {
                map[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
        return map
    }

    val activePromoExpiryTime: Long
        get() = prefs.getLong("promo_expiry_time", 0L)

    fun hasActivePromoUpgrade(): Boolean {
        val currentTime = System.currentTimeMillis()
        val expiryTime = activePromoExpiryTime

        if (expiryTime > currentTime) {
            return true
        } else if (expiryTime in 1..<currentTime) {
            // انتهت صلاحية العرض، قم بتصفيره
            prefs.edit().putLong("promo_expiry_time", 0L).apply()
        }
        return false
    }

    fun getPromoExpiryDateString(): String? {
        if (!hasActivePromoUpgrade()) return null
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return dateFormat.format(Date(activePromoExpiryTime))
    }

    /**
     * استرداد كود عبر واجهة RPC السحابية الآمنة لـ Supabase.
     * يتحقق ذرياً من الصلاحية، تاريخ الانتهاء، والحد الأقصى للاستخدام.
     */
    suspend fun redeemCode(code: String, idempotencyKey: String = UUID.randomUUID().toString()): RedemptionResult {
        val cleanCode = code.trim().uppercase(Locale.US)
        if (cleanCode.isBlank()) return RedemptionResult.INVALID_CODE

        // التحقق السحابي عبر RPC الآمن أولاً
        if (SupabaseServices.isSupabaseAvailable) {
            val rpcResult = SupabaseServices.Database.reservePromoCode(cleanCode, idempotencyKey)
            var handledCloudResult: RedemptionResult? = null

            rpcResult.onSuccess { res ->
                if (res.success) {
                    val discountType = res.discountType ?: "PROMO"
                    val discountVal = res.discountValue

                    if (discountType.equals("GIFT", ignoreCase = true)) {
                        accountService.walletBalance += discountVal.toInt()
                        handledCloudResult = RedemptionResult.SUCCESS_GIFT_CARD
                    } else {
                        // كود ترقية باقة برو (المدة بالأيام)
                        val durationDays = if (discountVal > 0) discountVal else 30L
                        val durationMs = durationDays * 24L * 60L * 60L * 1000L
                        val currentExpiry = if (hasActivePromoUpgrade()) activePromoExpiryTime else System.currentTimeMillis()
                        val newExpiry = currentExpiry + durationMs

                        prefs.edit()
                            .putLong("promo_expiry_time", newExpiry)
                            .apply()

                        handledCloudResult = RedemptionResult.SUCCESS_PROMO
                    }
                } else {
                    handledCloudResult = when (res.errorCode) {
                        "EXPIRED" -> RedemptionResult.EXPIRED
                        "USER_LIMIT_EXCEEDED", "ALREADY_CONFIRMED" -> RedemptionResult.ALREADY_USED
                        "TOTAL_LIMIT_EXCEEDED" -> RedemptionResult.LIMIT_REACHED
                        else -> RedemptionResult.INVALID_CODE
                    }
                }
            }.onFailure { err ->
                Log.w(TAG, "Cloud RPC reserve failed, attempting local fallback: ${err.message}")
            }

            if (handledCloudResult != null) {
                return handledCloudResult!!
            }
        }

        // مسار احتياطي محلي في حال انقطاع الشبكة (Local Fallback)
        val usedCodes = prefs.getStringSet("used_codes", mutableSetOf()) ?: mutableSetOf()
        if (usedCodes.contains(cleanCode)) {
            return RedemptionResult.ALREADY_USED
        }

        val allPromoCodes = validPromoCodes + getCustomPromoCodes()
        val durationMs = allPromoCodes[cleanCode]
        if (durationMs != null) {
            val currentExpiry = if (hasActivePromoUpgrade()) activePromoExpiryTime else System.currentTimeMillis()
            val newExpiry = currentExpiry + durationMs

            val newUsedCodes = mutableSetOf<String>().apply {
                addAll(usedCodes)
                add(cleanCode)
            }

            prefs.edit()
                .putLong("promo_expiry_time", newExpiry)
                .putStringSet("used_codes", newUsedCodes)
                .apply()

            return RedemptionResult.SUCCESS_PROMO
        }

        val allGiftCards = validGiftCards + getCustomGiftCards()
        val points = allGiftCards[cleanCode]
        if (points != null) {
            accountService.walletBalance += points

            val newUsedCodes = mutableSetOf<String>().apply {
                addAll(usedCodes)
                add(cleanCode)
            }

            prefs.edit()
                .putStringSet("used_codes", newUsedCodes)
                .apply()

            return RedemptionResult.SUCCESS_GIFT_CARD
        }

        return RedemptionResult.INVALID_CODE
    }

    /**
     * حجز كود مسبقاً قبل عملية الدفع (للمعاملات ومسار الشراء)
     */
    suspend fun reservePromoForCheckout(
        code: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<SupabaseServices.PromoReservationResponse> {
        return SupabaseServices.Database.reservePromoCode(code, idempotencyKey)
    }

    /**
     * إلغاء حجز الكود عند فشل أو تراجع المستخدم عن الدفع
     */
    suspend fun releasePromoReservation(reservationToken: String): Result<Boolean> {
        return SupabaseServices.Database.releasePromoCode(reservationToken)
    }
}
