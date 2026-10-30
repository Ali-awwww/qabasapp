# تطوير أيقونات الشريط السفلي والمؤثرات الصوتية المخصصة (3D Glassmorphic Icons & Tab-Specific SFX)

ترقية تصميم وتفاعل الشريط السفلي للتنقل بإدخال أيقونات هندسية ثلاثية الأبعاد بلمسات زجاجية وذهبية ملكية (`3D Glassmorphic Geometric Icons`) وتخصيص بصمة صوتية توليدية وفريدة لجميع تبويبات الشريط السفلي عند النقر (`Tab-Specific Procedural SFX`).

---

## القرارات المؤكدة (Confirmed Decisions)

> [!IMPORTANT]
> بناءً على إجابات الاستطلاع مع المستخدم:

- **نمط الأيقونات البصري**: أيقونات زجاجية ثلاثية الأبعاد بلمسات هندسية ذهبية متوهجة (`3D Glassmorphic with Gold Accents`).
- **المؤثرات الصوتية**: نغمات صوتية توليدية حديثة ومخصصة تميز كل تبويب عن الآخر عند النقر والانتخاب (`Procedural Tab-Specific Tones`).

---

## 1. النظرة العامة والهدف

إضفاء لمسة جمالية فاخرة وفريدة على تجربة الملاحة الرئيسية للتطبيق من خلال:
1. بناء حاويات زجاجية ثلاثية الأبعاد للأيقونات بها حواف وتفاصيل هندسية ذهبية متوهجة.
2. إضافة دالة `playTabRouteSound(route: AppState)` داخل `AppSoundManager.kt` تُميّز كل شاشة بنغمة ناعمة خاصة بها (مثلاً: نغمة ترحيبية للرئيسية، نغمة سيبرانية رقمية للوكيل، نغمة حركية للريلز، نغمة إبداعية للاستوديو، نغمة روحانية للمشكاة، ونغمة ميكانيكية للإعدادات).

---

## 2. تجربة المستخدم والتصميم البصري (UX & Visual Architecture)

### 1. تصميم الأيقونات ثلاثية الأبعاد (3D Glassmorphic Icon Containers):
- **الحاوية الزجاجية (3D Glass Capsule):**
  - كبسولة زجاجية مجسمة لكل أيقونة بحواف محفورة وتدرج ظلي متدرج العمق.
  - إطار هندسي نيون ذهبي (`GoldPrimary` / `GoldSecondary`) يزداد سطوعاً وتألقاً عند التحديد.
  - هالة توهج مشعة خلف الأيقونة النشطة مع بريق كوني ناعم.
- **الحركة ثلاثية الأبعاد:**
  - دوران وانزلاق عمودي طفيف للأيقونة عند تحديدها بديناميكية النوابض `spring()`.

### 2. البصمات الصوتية التوليدية المخصصة لكل تبويب (Tab-Specific Procedural SFX):
- **تبويب الرئيسية (`HOME`)**: نغمة ترحيبية دافئة ورنانة (`Warm Resonance Tone`).
- **تبويب الوكيل الذكي (`AI_STUDIO_AGENT`)**: نبضة سيبرانية رقمية مستقبلية (`Cyber Synth Pulse`).
- **تبويب الريلز (`REELS`)**: نقرة حركية إيقاعية مشجعة للوسائط (`Kinetic Media Pop`).
- **تبويب الاستوديو (`STUDIO_GATEWAY`)**: جرس إبداعي متناغم (`Creative Spark Chime`).
- **تبويب المشكاة (`HASANAT_HUB`)**: صدى روحاني فاخر ونقرة خشبية هادئة (`Spiritual Echo Tone`).
- **تبويب الإعدادات (`SETTINGS`)**: نقرة ميكانيكية دقيقة (`Precision Mechanical Click`).

---

## 3. الهندسة والتكافؤ البرمجي (Technical Implementation)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        QabasBottomNavigation                           │
├────────────────────────────────────────────────────────────────────────┤
│  ForEach Tab -> onClick Event                                         │
│       │                                                                │
│       ├─► AppSoundManager.playTabRouteSound(route)                      │
│       │      ├─► HOME -> Warm Resonance Tone (PCM)                     │
│       │      ├─► AGENT -> Cyber Synth Pulse (PCM)                      │
│       │      ├─► REELS -> Kinetic Media Pop (PCM)                      │
│       │      ├─► STUDIO -> Creative Spark Chime (PCM)                  │
│       │      ├─► HASANAT -> Spiritual Echo Tone (PCM)                  │
│       │      └─► SETTINGS -> Precision Mechanical Click (PCM)          │
│       │                                                                │
│       ├─► Haptic Feedback Trigger                                      │
│       └─► Render 3D Glassmorphic Icon Badge + Geometric Gold Accents  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. خطة الخطوات التنفيذية

1. تحديث `AppSoundManager.kt` بإضافة التوليد الإجرائي للترددات الصوتية الستة الخاصة بكل تبويب وتوفير الدالة `playTabRouteSound(route: AppState)`.
2. تحديث `QabasBottomNavigation` في `MainActivity.kt` لإضافة الحاويات الزجاجية ثلاثية الأبعاد واللمسات الهندسية الذهبية واستدعاء الصوت المخصص فور النقر.
3. إجراء فحص التجميع والتحقق عبر `compile_applet`.
