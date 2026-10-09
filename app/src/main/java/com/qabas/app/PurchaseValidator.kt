package com.qabas.app

import kotlinx.coroutines.delay
import kotlin.random.Random

object PurchaseValidator {
    /**
     * يحاكي التحقق من إيصال الشراء مع خوادم الواجهة الخلفية (Backend).
     * في تطبيق حقيقي، سيتم إرسال الرمز (Token) إلى الخادم للتحقق منه عبر Google Play Developer API.
     */
    suspend fun verifyPurchase(itemId: String, purchaseToken: String): Boolean {
        // محاكاة تأخير الشبكة والاتصال بالخادم (Backend Validation)
        delay(1500)

        // محاكاة فشل متعمد للرموز غير الصالحة
        if (purchaseToken == "INVALID_TOKEN") {
            return false
        }

        // محاكاة نسبة نجاح 98% لعمليات الشراء (لجعلها واقعية وتغطية حالات فشل الشبكة)
        return Random.nextFloat() > 0.02f
    }
}
