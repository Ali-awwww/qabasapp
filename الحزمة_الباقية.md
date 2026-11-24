# وثيقة الحزمة الباقية — مساعد المسلم (Qabasapp)
## سجل المهام والمكونات والخدمات المتبقية للاكتمال الكامل 100%

**تاريخ التحديث:** 17 نوفمبر 2026  
**الهوية المعتمدة:** Apple Human Interface Guidelines (Clean Light & Dark)  
**القاعدة الأساسية:** النزاهة الهندسية التامة — لا وظائف وهمية، وربط حقيقي بكافة الخدمات مع حصرية Supabase.

---

## 📊 ملخص الحالة العامة للمشروع

| النطاق | النسبة المنجزة | الحالة |
|---|---|---|
| **النظام البصري والمظهر (Apple HIG Light & Dark)** | 100% | مكتمل ومفعّل |
| **هيكل التطبيق والتنقل الموحد (5 تبويبات قياسية)** | 100% | مكتمل ومفعّل |
| **المنظومة الإسلامية الروحانية (11 شاشة)** | 100% | مكتملة ومربوطة محلياً وسحابياً |
| **منظومة الريلز والتفاعل والنشر (Reels Suite)** | 100% | مكتملة ومربوطة بـ Supabase |
| **الحسابات والمشاريع والمحفوظات والمكافآت** | 100% | مكتملة بعد معالجة المفاتيح والأزرار |
| **حزمة التنزيلات والتحديثات والسياسات (Utility Suite)** | 60% | جاري الاستكمال (5 شاشات) |
| **استوديوهات الإنتاج والمونتاج (Studio & Editors)** | 20% | تحت التنفيذ المباشر |
| **لوحات المطورين والوكلاء الأذكياء (Dev Hub & AI)** | 30% | تحت التنفيذ المباشر |
| **إجمالي الإنجاز الكلي للمشروع** | **~72%** | **المتبقي ~28%** |

---

## 🗂️ تفصيل مكونات «الحزمة الباقية» حسب الأولوية

### 1️⃣ الحزمة الأولى: مركز التنزيلات والتحديثات والسياسات العامة (Utility & Governance Suite)
**الملفات المشمولة:**
1. `/app/src/main/java/com/qabas/app/DownloadsHubScreen.kt`
   - **المهام المطلوبة:**
     - إعادة التصميم بالكامل بنمط Apple HIG للوضعين الفاتح والداكن (`AppleTopBar`, `AppleCard`, `AppleSearchBar`).
     - إزالة الأشكال الحادة `CutCornerShape` واستبدال شارات "LIVE" و"NEW ⚡" بمكونات Apple القياسية.
     - الحفاظ على كامل منطق فحص وتنزيل نماذج الذكاء المحلي (GGUF) وتحديثات التطبيق التلقائية (OTA).
     - ضمان مساحات اللمس القياسية (>= 48.dp) لكافة أزرار التنزيل والمشاركة والحذف.
2. `/app/src/main/java/com/qabas/app/UpdateHistoryScreen.kt`
   - **المهام المطلوبة:**
     - نقل الواجهة من `DeepSlate` إلى `QabasTheme.current` للوضعين الفاتح والداكن.
     - استبدال البطاقات القديمة بـ `AppleCard` مع تنظيم ملاحظات الإصدار والتثبيت المباشر.
3. `/app/src/main/java/com/qabas/app/QabasRulesScreen.kt`
   - **المهام المطلوبة:**
     - إزالة الخلفيات السيبرانية والشبكة النيونية واستبدالها بنمط iOS Grouped Inset.
     - تفعيل شريط البحث المعطل (`searchQuery`) لتصفية القواعد لحظياً.
     - حفظ إقرار المستخدم بالموافقة على القواعد في `SharedPreferences` محلياً بدل المتغير المؤقت `isAcknowledged`.
4. `/app/src/main/java/com/qabas/app/EnterprisePortalScreen.kt`
   - **المهام المطلوبة:**
     - إعادة تصميم الشاشة بأسلوب Apple HIG مع دعم الوضعين الفاتح والداكن.
     - إزالة القائمة النموذجية الوهمية للرعايات (`activeRetainers` الثابتة) وربطها بالطلبات الحقيقية المحفوظة.
     - حفظ وتصدير طلبات المؤسسات والجمعيات محلياً وإرسالها عبر البريد أو نسخها بشكل رسمي بدلاً من التعليق الصامت.
5. `/app/src/main/java/com/qabas/app/DeveloperContactScreen.kt`
   - **المهام المطلوبة:**
     - توحيد واجهة المحادثات ولوحة رسائل المطورين وفق أسلوب Apple Messaging / iOS Chat.
     - ضمان عمل قنوات المراسلة مع Supabase في الوضعين الفاتح والداكن دون إجبار المستخدم على الألوان الداكنة.

---

### 2️⃣ الحزمة الثانية: شاشات الصلاحيات والأذان والأدوات (Permissions & System Alerts)
**الملفات المشمولة:**
1. `/app/src/main/java/com/qabas/app/AppPermissionsHubScreen.kt` و`AppPermissionsLauncher.kt` و`AppPermissionLauncher.kt`
   - توحيد واجهة إدارة الأذونات بأسلوب بطاقات الخصوصية في iOS (Privacy & Permissions Cards).
   - توضيح أسباب كل صلاحية بوقار وشفافية ودعم الوضعين الفاتح والداكن.
2. `/app/src/main/java/com/qabas/app/AdhanFullScreenActivity.kt`
   - شاشة الأذان بملء الشاشة مع التنبيه الروحاني العالي، مع دعم تباين قوي في الإضاءة الليلية والنهارية.
3. `/app/src/main/java/com/qabas/app/MuslimToolsDialogs.kt`
   - تحويل حوارات الأدوات الإسلامية (القبلة، مواقيت السفر، التنبيهات) إلى أوراق سفلية قياسية (`Apple Translucent Bottom Sheet`).

---

### 3️⃣ الحزمة الثالثة: استوديوهات الإنتاج والمونتاج والإخراج (Studio & Editors Suite)
**الملفات المشمولة:**
1. **بوابة الاستوديو الشاملة:**
   - `/app/src/main/java/com/qabas/app/StudioGatewayScreen.kt`
   - `/app/src/main/java/com/qabas/app/UnifiedMasterStudioScreen.kt`
   - **المهام:** تحويل الاستوديو إلى مركز إنتاج iOS حديث يتيح الوصول لكافة الأدوات المتاحة مع تعطيل الأدوات التي تفتقر لمفاتيح خارجية بدلاً من إغلاق الشاشات بأخطاء.
2. **خط أنابيب الإدخال والاستيراد (Input Pipeline):**
   - `InputScreen.kt`, `InputScreenSections.kt`
   - `VideoImportSection.kt`, `ImagesImportSection.kt`, `IdeaInputSection.kt`, `AudioImportSection.kt`
   - **المهام:** استبدال عقود اختيار الملفات القديمة بعقد `ActivityResultContracts.PickVisualMedia` الصديق لسياسات Google Play (بدون أذونات تخزين واسعة)، ودعم الوضعين الفاتح والداكن.
3. **مراحل المعالجة والمراجعة والتصدير:**
   - `UnderstandingScreen.kt`, `ResourceReviewScreen.kt`, `ProcessingScreen.kt`, `ReviewScreen.kt`, `CinematicExportScreen.kt`
   - **المهام:** تحديث مؤشرات التقدم، بطاقات المراجعة، ومفاتيح التصدير السينمائي بأسلوب Apple مع عرض جودة الفيديو الحقيقية.
4. **الاستوديوهات المتخصصة:**
   - `VoiceStudioScreen.kt` (استوديو الصوتيات والتسجيل والتعليق الصوتي الصافي).
   - `PhotoStudioScreen.kt` (استوديو بطاقات وتصاميم الصور الدعوية).
   - `TeleprompterScreen.kt` (الملقن الذكي لقراءة النصوص القرآنية والحديثية بتدفق سلس).
   - `QasasStorytellerScreen.kt` (سرد القصص والتاريخ الإسلامي).
   - `AdvancedEditScreen.kt` (المحرر المتقدم والطبقات والقص).
   - `ClipExtractorScreen.kt` (مستخرج المقاطع والدرر).
   - `CentralMediaBrowserScreen.kt` و`TranscriptVideoEditorScreen.kt` و`StyleSelectionScreen.kt` و`TasteProfileScreen.kt`.

---

### 4️⃣ الحزمة الرابعة: لوحات المطورين والوكلاء الأذكياء (Dev Hub & AI Agents)
**الملفات المشمولة:**
1. **لوحات التشخيص والمراقبة:**
   - `DeveloperDashboardScreen.kt`, `ApiDocsScreen.kt`, `DeveloperKeyStatusHub.kt`, `AppDoctorSection.kt`, `CrashLogsHubScreen.kt`
   - `ConnectorsSection.kt`, `DiagnosticsDashboardSection.kt`, `AuditLogSection.kt`, `ProductionPipelineSection.kt`, `EnhancedUsersSection.kt`
   - `RevenueDashboard.kt`, `EngagementAnalyticsChart.kt`, `AgencyMonetizationHub.kt`, `GithubXdeltaHubScreen.kt`
   - **المهام:** إزالة أي محاولة لاستدعاء Firestore متبقية، واعتماد Supabase كمنصة سحابية وحيدة، مع تحويل الواجهات إلى لوحات iOS الإحصائية النظيفة.
2. **واجهات الوكلاء والذكاء الاصطناعي:**
   - `AIAssistantScreen.kt`, `AiStudioAgentScreen.kt`, `AiSmartDirectorScreen.kt`, `DirectorChatScreen.kt`, `AiModelLatencySwitcherScreen.kt`
   - `AiModelLatencyRouter.kt`, `AiEditorSection.kt`, `AgentDialogs.kt`, `AgentHudKit.kt`, `AgentMissionCards.kt`, `AgentExecutionAnimation.kt`
   - **المهام:**
     - تصحيح كافة مسميات نماذج Gemini غير الصالحة في المنظومة (مثل `gemini-3.8-flash` و`gemini-3.5-flash`) واستبدالها بنماذج معتمدة صالحة (`gemini-2.5-flash`).
     - تطبيق هوية Apple HIG الفاتحة والداكنة على نوافذ المحادثة ومؤشرات التفكير.

---

### 5️⃣ الحزمة الخامسة: الحوارات والمكونات المشتركة (Shared Dialogs & Polish)
**الملفات المشمولة:**
- `DiagnosticScanDialog.kt`, `OnDeviceModelDownloadDialog.kt`, `SmartPublishScheduleDialog.kt`, `QabasExitConfirmationDialog.kt`
- `SpatialDevSettingsDialog.kt`, `SpatialAiRemediationApprovalDialog.kt`, `SpatialBuildTestLabDialog.kt`, `SpatialDevIntegrationGuideDialog.kt`
- `SharedComponents.kt`, `DesignDesignKit.kt`, `AppRealVersionFooterBadge.kt`
- **المهام:** توحيد كافة الحوارات التنبيهية لتكون بأسلوب `AppleCard` / `AlertDialog` متناسق تماماً مع الوضع الفاتح والداكن ومساحات لمس >= 48.dp.

---

## 🎯 خطة العمل المباشرة لإكمال الحزمة
1. إنهاء **الحزمة الأولى (مركز التنزيلات والسياسات)** فوراً (`DownloadsHubScreen.kt` و`UpdateHistoryScreen.kt` و`QabasRulesScreen.kt` و`EnterprisePortalScreen.kt` و`DeveloperContactScreen.kt`).
2. إنهاء **الحزمة الثانية والثالثة (الصلاحيات والاستوديوهات والمحررين)**.
3. إنهاء **الحزمة الرابعة والخامسة (لوحات المطورين والوكلاء والحوارات)** مع تنظيف مسميات النماذج المتبقية.
4. إجراء فحص التدقيق الساكن النهائي وتوليد التقرير الختامي الشامل.
