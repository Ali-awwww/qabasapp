# CORE OPERATIONAL CONSTITUTION & STRICT DIRECTIVES
# دستور العمل الصارم والقوانين الجوهرية لأي تحديث أو مهمة برمجية

> **إشعار إلزامي (MANDATORY DIRECTIVE):**
> هذا الملف يمثل الدستور الأساسي والقانون الصارم لجميع المساعدين والمطورين (AI Agents & Developers) العاملين على هذا المشروع.
> **يجب قراءة هذا الملف والالتزام التام بكل بند فيه قبل البدء بأي تعديل أو تحديث للكود.**

---

## 1. The Fundamental Creed: Absolute Honesty (الصدق المطلق ومنع الكذب نهائياً)
Lying is strictly forbidden within the operational framework of our application, a fundamental principle that underscores our commitment to ensuring that all interactions and data exchanges are grounded in honesty and integrity. This commitment is not merely a guideline but a cornerstone of our operational ethos, fostering an environment where trust and transparency prevail.

- يمنع منعاً باتاً الكذب، أو إعطاء وعود زائفة، أو الادعاء بإنجاز شيء لم يتم إنجازه بالكامل واختباره.
- كل تقرير أو استجابة يجب أن تكون صادقة وشفافة ودقيقة بنسبة 100%.

**معيار قابل للقياس:** أي ادعاء بـ«تم الإصلاح» يجب أن يُوثق بـ:
- أمر البناء الذي تم تشغيله ونتيجته
- اسم الملف المعدل مع رقم السطر
- حالة الاختبارات (إن وجدت)

---

## 2. 100% Completion Rule & Session Continuity (إكمال العمل بنسبة 100% وطلب الإذن للاستمرار)
If you embark on a task but find yourself unable to complete it within a single session, it is essential to request permission to continue working on it until you achieve a full 100% completion. This approach not only promotes thoroughness and diligence but also ensures that no detail is overlooked, thereby enhancing the overall quality of the work produced.

- لا تترك مهمة معلقة أو نصف مكتملة دون طلب الإذن الصريح للاستمرار في الجلسات اللاحقة حتى الوصول إلى إنجاز تام بنسبة 100%.
- الإتقان التام والتدقيق في كل التفاصيل شرط غير قابل للمساومة.

**معيار «Done» قابل للقياس:**
- البناء ينجح (`./gradlew assembleDebug` أو `compileDebugKotlin`)
- لا توجد تحذيرات جديدة في الكود المعدل
- الملفات المعدلة مدرجة في `git status`
- لا توجد بيانات تجريبية متبقية

---

## 3. Proactive Initiative & Filling Missing Components (المبادرة وسد الثغرات بذكاء)
Should I instruct you to request something that lacks the necessary components for a feature, you are expected to proactively seek out similar features that can fill in the gaps, demonstrating resourcefulness and initiative in your problem-solving process. This proactive stance is vital in navigating the complexities of our application, as it reflects an understanding of the interconnectedness of features and the importance of adaptability in achieving functional outcomes.

- عند طلب ميزة تنقصها بعض المكونات أو المتطلبات، لا تتوقف ولا تشتكِ، بل بادر فوراً بالبحث عن ميزات وأنماط مشابهة وسد النواقص بابتكار واحترافية.

**معيار قابل للقياس:** عند اكتشاف مكون ناقص:
- البحث في الكود عن أنماط مشابهة أولاً
- إن لم يوجد، اقترح الحل الأبسط الذي يعمل
- لا تنتظر تعليمات إضافية

---

## 4. Immediate Flaw Rectification (المسؤولية الفورية عن إصلاح أي عيب)
In the event that you identify a flaw within the application, it is your responsibility to address and rectify it promptly, maintaining the overall quality and functionality of the system. This responsibility not only safeguards the integrity of our application but also contributes to a culture of continuous improvement and accountability.

- عند ملاحظة أي خلل أو عيب أو خطأ برمجي أو تداخل في الواجهات أثناء العمل، يقع على عاتقك إصلاحه وتصحيحه فوراً دون تجاهل.

**معيار قابل للقياس:** عند اكتشاف عيب:
- إصلاحه فوراً إن كان في نطاق العمل الحالي
- إن كان خارج النطاق، تسجيله في `bd` كمهمة جديدة
- لا تمرر عيب دون إصلاح أو تسجيل

---

## 5. Elimination of Dummy/Experimental Data (الحذف الصارم للبيانات الوهمية والتجريبية)
Furthermore, any dummy or experimental data generated during testing phases will be systematically deleted to preserve the integrity of our data environment. This meticulous attention to data management is crucial in ensuring that our operational landscape remains free from clutter and confusion, allowing for seamless functionality and user experience.

- أي بيانات تجريبية أو وهمية أُنشئت أثناء الفحص أو الاختبار يجب حذفها وتطهير بيئة البيانات منها للحفاظ على نظافة ومصداقية التطبيق.

**معيار قابل للقياس:** قبل إنهاء أي مهمة:
- تشغيل `git diff` للتأكد من عدم وجود بيانات تجريبية
- البحث عن `TODO`، `FIXME`، `dummy`، `test_data` في الكود المعدل
- حذف أي ملفات مؤقتة أُنشئت أثناء العمل

---

## 6. Strict Command Discipline & Prohibition of Substitution (الانضباط الصارم للأوامر ومنع الاستبدال)
It is imperative to adhere to the prohibition against executing commands by substitution; only the designated terminal commands that are available should be utilized to ensure consistency and reliability in our operations. This strict adherence to protocol is essential in maintaining the robustness of our application and ensuring that all users can rely on its performance without encountering unexpected anomalies.

- يمنع استخدام أوامر مبدلة أو غير مصرح بها أو التحايل بالأوامر.
- استخدم فقط الأدوات والأوامر النظامية المتاحة والمعتمدة بدقة.

**القائمة البيضاء للأوامر:**
- `bd` — إدارة المهام
- `git` — إدارة الكود (commit, push, pull, status, diff, add)
- `./gradlew` — البناء والاختبارات
- `find`، `grep`، `rg` — البحث في الكود
- `cat`، `head`، `tail` — قراءة الملفات

**ممنوع صراحةً:**
- `rm -rf` بدون تأكيد
- `git push --force`
- `git reset --hard`
- أي أمر يستبدل أداة النظام

---

## 7. Design System: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
As you navigate through this system on a new screen, or if you find a screen that suits this design while browsing or executing the **Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)**, remember that each interaction is an opportunity to uphold these principles and contribute to a cohesive, efficient, and innovative operational environment.

- اعتماد مبادئ التصميم المتطورة (Cybernetic Glassmorphism + Linear Minimalist / Spatial HUD Tech) أينما تناسب الشاشات المستقبلية والتقنية في النظام، مع الحفاظ على روح الهوية الأصيلة المتناسقة والشفافية التفاعلية الفائقة.

**معيار قابل للقياس:** أي شاشة جديدة يجب أن تستخدم:
- `CardSurface` كخلفية أساسية
- `GoldPrimary` للعناصر التفاعلية
- `CairoFont` للنصوص العربية
- `NotoSansFont` للنصوص الإنجليزية
- زوايا `RoundedCornerShape(16.dp)` أو أكثر

---

## 8. Typography, Adaptive Layouts & Anti-Vertical-Squishing Law (قانون التخطيط التكيفي ومنع ضغط وتشويه النصوص)
It is strictly prohibited to produce constrained, squished, or vertically-wrapped single-word text artifacts (such as badges, tags, or status labels breaking awkwardly character-by-character or word-by-word into vertical columns due to improper parent Row/Column constraints). 

Whenever developing or updating Compose UI elements, every assistant and developer MUST enforce the following safeguards:
1. **Badges and Tags Protection:** All auxiliary tags, pills, chips, and badges inside horizontal rows MUST have `softWrap = false`, `maxLines = 1`, and adequate horizontal padding, or be wrapped with `Modifier.wrapContentWidth()`. They must never be compressed vertically or squeezed out by adjacent text.
2. **Text Weight Distribution in Rows:** In any `Row` containing a title and a badge or action icon, the expandable title MUST use `Modifier.weight(1f, fill = false)` with `maxLines = 1` and `overflow = TextOverflow.Ellipsis`, ensuring the primary text yields space gracefully to fixed badges without crushing them.
3. **Responsive DP Boundaries:** Avoid unconstrained horizontal nesting or hardcoded zero-flex boundaries. Use `widthIn(max = ...)` for dialogs and modals, and ensure all text labels are tested against narrow viewport widths (e.g. 320dp - 360dp) to prevent accidental text stacking.
4. **Immediate Rectification of Visual Defects:** Any observed vertical squishing, clipped labels, or awkward line breaks must be treated as a critical UI defect and fixed immediately before completing the session.

**دالة جاهزة للـ Badge:**
```kotlin
@Composable
fun StrictBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.6.dp, color),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
```

**معيار قابل للقياس:** أي badge جديد يجب أن:
- يستخدم `StrictBadge` أو يتبع نفس النمط
- لا يتجاوز `maxLines = 1`
- لا يُضغط في `Row` بدون `weight(1f, fill = false)` للنص المجاور
