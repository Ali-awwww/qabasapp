# تطوير قسم المحراب (الصلاة والمواقيت) — تصميم هندسي نقي

## الملف الرئيسي
`app/src/main/java/com/qabas/app/PrayerTimesScreen.kt` — أُعيد بناء قسم المحراب بالكامل

## نظام التصميم الجديد
`app/src/main/java/com/qabas/app/ui/geometry/GeometricDesignSystem.kt` (جديد)

| المكوّن | الوظيفة |
|---|---|
| `Geo` | ثوابت الشبكة: أنصاف أقطار (18/12/26dp)، مسافات (4/8/14/20/28dp)، أحجام أيقونات، سماكة حدود |
| `GeoColor` | لوحة ألوان مرتبطة بالثيم (لا ألوان مثبّتة) + ألوان الأقسام + تدرّجات |
| `GeoShape` | الأشكال القياسية: Card / CardSm / CardLg / Button / Chip / Circle |
| `GeoCard` | بطاقة موحّدة (نصف قطر واحد + حدود شعرية) |
| `GeoIconBadge` | دائرة أيقونة + إطار hairline |
| `GeoHeader` | رأس قسم بمؤشر عمودي + إجراء اختياري |
| `GeoChip` | شريحة موحّدة (محدد/غير محدد) |
| `GeoButton` | زر موحّد (Filled / Outline / Ghost) |
| `GeoProgressRing` | حلقة تقدم هندسية نظيفة (بدون مؤثرات ثقيلة) |
| `GeoMiniStat` | رقم + تسمية مضغوطة |
| `GeoDivider` | فاصل |

## ما أُصلح (عيوب حقيقية)

### 1. تسريب التدرّج خارج حدود البطاقة
`HeroPrayerCountdownCard` كان يرسم `Brush.verticalGradient` داخل `Box` **بلا قصّ**، فكان التدرّج
يفيض فوق الزوايا المدوّرة. الآن: `clip(CardLg)` → `background(gradient, CardLg)` → `border`.

### 2. ميزة معطّلة: تخصيص تنبيه الصلاة
`onConfigPrayer` كان **معلَناً في توقيع `TimetableAndControlsTab` لكنه لا يُستدعى أبداً**،
و`selectedPrayerForConfig` كان يُضبط ولا يُقرأ — أي أن نص «اضغط لتخصيص التنبيه» كان وعداً كاذباً.
الآن: الضغط على أي صف صلاة يفتح `PrayerNotificationConfigDialog` (تفعيل/تعطيل + نمط الصوت + صوت المؤذن)
ويعيد الجدولة فوراً.

### 3. مصدران للحقيقة في تفضيلات الصلاة
`PrayerAlarmManager.scheduleAllPrayers` كان يقرأ من `qabas_notification_settings` بينما
`PrayerTimesScreen` و`scheduleSinglePrayer` يكتبان/يقرآن `qabas_prayer_prefs`.
الآن: **مصدر واحد** (`qabas_prayer_prefs`)، و`NotificationPreferencesManager` صار واجهة مكتوبة فوقه.

### 4. نمط صوت غير مدعوم (فشل صامت)
نافذة التخصيص كانت تستخدم `SHORT_AZAN` وهي **غير موجودة** في `PrayerAzanReceiver`
(الأنماط المدعومة: `FULL_AZAN`, `TAKBEER_ONLY`, `NOTIFICATION_ONLY`, `SILENT`)
— أي أن الإشعار كان سيفشل بصمت. الآن الأنماط مطابقة تماماً.

### 5. انضغاط النص
`PrayerCheckCard` و`SunnahCheckCard` كانا يضعان `Text` بلا وزن بجانب `FilterChip`،
فيُسحق النص على الشاشات الضيقة (320–360dp) — مخالفة صريحة لـ`CORE_OPERATIONAL_PROMPT`.
الآن: `weight(1f, fill = false)` + `maxLines` + `overflow`.

## إزالة التداخل والزخرفة

| قبل | بعد |
|---|---|
| 4 أنصاف أقطار مختلفة (14/16/20/24dp) | `GeoShape` موحّد |
| ~10 ألوان مثبّتة (`0xFF151B2B`, `0xFF1E293B`, `0xFF0F172A`…) | رموز الثيم |
| إيموجي مزدوج في التبويبات والقائمة | أيقونات Material فقط |
| إيموجي `🕋 🔥 📱 ☁️ 📌 💡 ⬇️` في الواجهة | `🕋`→أيقونة مسجد، `🔥`→حلقة، الباقي→أيقونات |
| مؤثر نبض متكرر (infiniteTransition) | حلقة ثابتة هندسية |
| تبويبات بنص فقط | تبويبات بأيقونة ولون لكل قسم |

## الملفات المعدّلة

| الملف | التغيير |
|---|---|
| `PrayerTimesScreen.kt` | إعادة بناء كاملة لقسم المحراب (18 composable) |
| `ui/geometry/GeometricDesignSystem.kt` | **جديد** |
| `NotificationPreferencesManager.kt` | إنشاء + توحيد المخزن + ثوابت أنماط الصوت |
| `PrayerAlarmManager.kt` | إزالة بوابة مزدوجة المصدر + `isPrayerEnabled` عامة |

## التحقق المنفَّذ

- ✅ توازن الأقواس في الملفات الأربعة (فحص برمجي)
- ✅ لا توجد دوال مكرّرة (18 تعريفاً فريداً)
- ✅ لا استدعاءات خارجية لـ`PrayerCheckCard`/`SunnahCheckCard` خارج الملف
- ✅ `PrayerTimesScreen` يُستدعى من `AppNavigation` بتوقيع متوافق
- ✅ 20+ أيقونة مؤكَّدة وجودها في المشروع
- ✅ `AzanVoice` و`TransferCounterBar` متوافقان مع الاستدعاءات الجديدة

## غير مُتحقَّق منه

- ⚠️ **لم يُشغَّل `./gradlew compileDebugKotlin`** — لا يوجد تأكيد فعلي من نجاح الترجمة.
- ⚠️ `Icons.Default.Nightlight` (غير `NightlightRound`) لم تُستخدم سابقاً في المشروع.
