package com.qabas.app

import android.content.Context

enum class UserTier(val title: String, val description: String) {
    DEVELOPER("مطور ومؤسسات", "إدارة كاملة، تحكم، وخدمات التطبيقات التخصيصية"),
    BYOK("اكتفاء ذاتي", "استخدام غير محدود عبر مفاتيح API الخاصة بك"),
    FREE("مجاني (كامل)", "حد يومي: 5 فيديوهات + 5 صور (تتجدد كل 24 ساعة)")
}

data class TierPermissions(
    val canUseSmartDirector: Boolean,
    val canExport4K: Boolean,
    val hasWatermark: Boolean,
    val dailyVideoLimit: Int,
    val dailyImageLimit: Int
)

class TierStateManager(private val context: Context) {
    private val accountService = AppServices.getAccountService(context)

    val currentTier: UserTier
        get() {
            // 1. Developer / Admin / Enterprise
            if (accountService.isDeveloperOrAdmin) return UserTier.DEVELOPER
            
            // 2. BYOK (Bring Your Own Key / Self Sufficiency)
            if (accountService.hasCustomKeys) return UserTier.BYOK
            
            // 3. Free (Fully functional with 5 videos & 5 images per day)
            return UserTier.FREE
        }

    val permissions: TierPermissions
        get() = when (currentTier) {
            UserTier.DEVELOPER -> TierPermissions(
                canUseSmartDirector = true,
                canExport4K = true,
                hasWatermark = false,
                dailyVideoLimit = Int.MAX_VALUE,
                dailyImageLimit = Int.MAX_VALUE
            )
            UserTier.BYOK -> TierPermissions(
                canUseSmartDirector = true,
                canExport4K = true,
                hasWatermark = false,
                dailyVideoLimit = Int.MAX_VALUE,
                dailyImageLimit = Int.MAX_VALUE
            )
            UserTier.FREE -> TierPermissions(
                canUseSmartDirector = true,
                canExport4K = true,
                hasWatermark = false,
                dailyVideoLimit = AccountService.MAX_DAILY_VIDEOS,
                dailyImageLimit = AccountService.MAX_DAILY_IMAGES
            )
        }

    fun canGenerateVideo(): Boolean {
        if (currentTier == UserTier.DEVELOPER || currentTier == UserTier.BYOK) return true
        return accountService.canGenerateVideo()
    }

    fun canGenerateImage(): Boolean {
        if (currentTier == UserTier.DEVELOPER || currentTier == UserTier.BYOK) return true
        return accountService.canGenerateImage()
    }

    fun recordVideoGeneration() {
        if (currentTier == UserTier.FREE) {
            accountService.incrementVideoUsage()
        }
    }

    fun recordImageGeneration() {
        if (currentTier == UserTier.FREE) {
            accountService.incrementImageUsage()
        }
    }
}

