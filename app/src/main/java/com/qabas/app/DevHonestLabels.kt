package com.qabas.app

/**
 * ثوابت وسم الصدق للوحة المطوّر.
 * أي رقم غير آتٍ من مصدر حي يجب أن يُعرض مع هذه الوسوم.
 */
object DevHonestLabels {
    const val DEMO_BADGE = "بيانات تجريبية — ليست من Analytics الحي"
    const val KEY_STATUS_ONLY = "حالة المفتاح فقط — ليس استهلاك استخدام حقيقي"
    const val LOCAL_NOTIF = "إشعار محلي على هذا الجهاز — ليس FCM لكل المستخدمين"
    const val KPI_ESTIMATE = "تقديرات / قيم ثابتة للعرض — ليست قياسات حية"
}
