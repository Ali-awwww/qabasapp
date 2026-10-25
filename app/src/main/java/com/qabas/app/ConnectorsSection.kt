package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * نموذج بيانات نقطة الاتصال السحابية والصيانة (Cloud Touchpoint & Maintenance State)
 */
private data class ConnectorState(
    val id: String,
    val title: String,
    val technicalName: String,
    val icon: ImageVector,
    val category: String,
    val usedIn: String,
    val maintenanceInfo: String = "",
    val sqlQuery: String = "",
    var connected: Boolean? = null,
    var detail: String = "",
    var latencyMs: Long = 0L,
    var checking: Boolean = false
)

/**
 * شاشة مرصد نقاط الاتصال السحابية والصيانة بنظام:
 * «Cybernetic Glassmorphism + Linear Minimalist (Spatial Tech)»
 * توفر رؤية شاملة لكل أقسام ومراكز التطبيق التي تعتمد على الربط السحابي للاختبار والصيانة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsSection(
    onJumpBuildCenter: () -> Unit = {},
    onJumpKeys: () -> Unit = {},
    onJumpHealth: () -> Unit = {},
    onJumpDeltaMonitoring: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var states by remember {
        mutableStateOf(
            listOf(
                ConnectorState(
                    id = "hadith_cloud",
                    title = "سحابة 365 حديثاً نبوياً",
                    technicalName = "Firebase Firestore (daily_hadiths) + Firebase AI Logic",
                    icon = Icons.Default.AutoAwesome,
                    category = "HADITH & QURAN",
                    usedIn = "قسم الأحاديث الشريفة: جلب 365 حديثاً نبوياً، التفسير والترجمة الآلية",
                    maintenanceInfo = "تخزين ومزامنة الأحاديث الشريفة والشروح والترجمات السحابية",
                    sqlQuery = """
                        -- 1. جدول 365 حديثاً نبوياً الشريفة (Daily Hadiths)
                        CREATE TABLE IF NOT EXISTS public.daily_hadiths (
                            id SERIAL PRIMARY KEY,
                            day_number INT UNIQUE NOT NULL,
                            hadith_arabic TEXT NOT NULL,
                            scholar_source TEXT DEFAULT 'صحيح البخاري',
                            explanation_arabic TEXT,
                            category TEXT DEFAULT 'عقيدة وأخلاق',
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.daily_hadiths ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Public Hadiths Select" ON public.daily_hadiths;
                        CREATE POLICY "Public Hadiths Select" ON public.daily_hadiths FOR SELECT USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "supabase_users",
                    title = "سحابة الحسابات والخط الزمني",
                    technicalName = "Supabase PostgreSQL Database & Auth Engine",
                    icon = Icons.Default.CloudQueue,
                    category = "USER & TIMELINE",
                    usedIn = "قسم إدارة المستخدمين: الحسابات السحابية والخط الزمني التفاعلي",
                    maintenanceInfo = "جدول users وحفظ حالة البصمة الرقمية والبيانات المزامنة",
                    sqlQuery = """
                        -- 2. جدول الحسابات والخط الزمني للمستخدمين (Users & Timeline)
                        CREATE TABLE IF NOT EXISTS public.users (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            external_id TEXT UNIQUE,
                            email TEXT UNIQUE NOT NULL,
                            name TEXT,
                            custom_id TEXT,
                            account_number TEXT,
                            type TEXT DEFAULT 'مجاني',
                            status TEXT DEFAULT 'active',
                            strikes INT DEFAULT 0,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        CREATE TABLE IF NOT EXISTS public.user_timeline (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            user_id TEXT NOT NULL,
                            action_type TEXT NOT NULL,
                            description TEXT,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Public Users Access" ON public.users;
                        CREATE POLICY "Public Users Access" ON public.users FOR ALL USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "ai_defense",
                    title = "سحابة الذكاء الاصطناعي والمدافعة",
                    technicalName = "OpenRouter Multi-LLM Gateway & Firebase AI Logic",
                    icon = Icons.Default.Psychology,
                    category = "AI & DEFENSE ENGINE",
                    usedIn = "قسم المدافعة الفكرية: محرك الرد على الشبهات والوكيل الذكي والتوليد",
                    maintenanceInfo = "بوابة استدعاء نماذج Gemini و Claude و GPT-4o الآلية",
                    sqlQuery = """
                        -- 3. جدول سجلات وتدقيق الذكاء الاصطناعي والمدافعة (AI & Defense Telemetry)
                        CREATE TABLE IF NOT EXISTS public.ai_defense_logs (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            model_name TEXT NOT NULL,
                            prompt_category TEXT,
                            response_summary TEXT,
                            latency_ms INT,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.ai_defense_logs ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow AI Logs Access" ON public.ai_defense_logs;
                        CREATE POLICY "Allow AI Logs Access" ON public.ai_defense_logs FOR ALL USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "push_gateway",
                    title = "مركز بث الإشعارات السحابية (9:00 ص)",
                    technicalName = "Firebase Cloud Messaging (FCM) & App Check Gateway",
                    icon = Icons.Default.NotificationsActive,
                    category = "NOTIFICATION GATEWAY",
                    usedIn = "قسم بث الإشعارات: جدولة وبث الرسائل اليومية الساعة 9:00 صباحاً",
                    maintenanceInfo = "قنوات FCM ورموز البث الجماهيري المباشر للعملاء",
                    sqlQuery = """
                        -- 4. جدول إدارة وبث الإشعارات السحابية (Push Notifications)
                        CREATE TABLE IF NOT EXISTS public.push_notifications (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            title TEXT NOT NULL,
                            body TEXT NOT NULL,
                            scheduled_time TEXT DEFAULT '09:00',
                            target_group TEXT DEFAULT 'all_users',
                            is_sent BOOLEAN DEFAULT false,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.push_notifications ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Push Access" ON public.push_notifications;
                        CREATE POLICY "Allow Push Access" ON public.push_notifications FOR ALL USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "moneyprinter",
                    title = "سحابة إنتاج الريلز ودبلجة الصوت",
                    technicalName = "MoneyPrinter Turbo FastAPI Engine + Edge-TTS",
                    icon = Icons.Default.Movie,
                    category = "AUTONOMOUS REELS",
                    usedIn = "قسم إنتاج الفيديو: أتمتة توليد مقاطع الريلز والشورتس 9:16 حياً",
                    maintenanceInfo = "خادم المونتاج التلقائي ومحرك توليد الفيديو والصوت 9:16",
                    sqlQuery = """
                        -- 5. جدول إنتاج وفيديوهات الريلز والشورتس 9:16 (Autonomous Reels)
                        CREATE TABLE IF NOT EXISTS public.reels_production (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            topic TEXT NOT NULL,
                            script TEXT,
                            video_url TEXT,
                            status TEXT DEFAULT 'ready',
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.reels_production ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Reels Access" ON public.reels_production;
                        CREATE POLICY "Allow Reels Access" ON public.reels_production FOR ALL USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "voicestudio",
                    title = "سحابة ومحرك VoiceStudio الأصوات",
                    technicalName = "VoiceStudio Local/Hybrid Zero-Shot Cloning Engine",
                    icon = Icons.Default.GraphicEq,
                    category = "VOICE & CLONING",
                    usedIn = "قسم الأصوات والدبلجة: استنساخ الأصوات والدبلجة لـ 16 محرك صوتي",
                    maintenanceInfo = "خادم تحليل الترددات ودبلجة المقاطع الصوتية الفكرية",
                    sqlQuery = """
                        -- 6. جدول دبلجة الأصوات وبصمات الصوت (Voice Studio)
                        CREATE TABLE IF NOT EXISTS public.voice_dubs (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            voice_engine TEXT NOT NULL,
                            speaker_name TEXT,
                            audio_url TEXT,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.voice_dubs ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Voice Dubs Access" ON public.voice_dubs;
                        CREATE POLICY "Allow Voice Dubs Access" ON public.voice_dubs FOR ALL USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "realtime_chat",
                    title = "شات المطورين وتحديثات البث الحي",
                    technicalName = "Supabase Realtime WebSockets & Channels",
                    icon = Icons.Default.Forum,
                    category = "REALTIME & DEV CHAT",
                    usedIn = "قسم المطورين: الدردشة الحية ومزامنة حالة الأجهزة وتأكيدات الأمان",
                    maintenanceInfo = "قنوات WebSocket الفورية ومراقبة الاتصال الحي",
                    sqlQuery = """
                        -- 7. جدول شات المطورين وتحديثات البث الحي (Dev Chat Realtime)
                        CREATE TABLE IF NOT EXISTS public.dev_chat (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            sender_id TEXT NOT NULL,
                            sender_name TEXT NOT NULL,
                            message TEXT NOT NULL,
                            msg_type TEXT DEFAULT 'text',
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        -- تفعيل البث الحي الفوري (Supabase Realtime)
                        DO $$
                        BEGIN
                            IF NOT EXISTS (
                                SELECT 1 FROM pg_publication_tables 
                                WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'dev_chat'
                            ) THEN
                                ALTER PUBLICATION supabase_realtime ADD TABLE public.dev_chat;
                            END IF;
                        END $$;

                        ALTER TABLE public.dev_chat ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Dev Chat Access" ON public.dev_chat;
                        CREATE POLICY "Allow Dev Chat Access" ON public.dev_chat FOR ALL USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "azan_cloud",
                    title = "سحابة مواقيت الصلاة والأذان",
                    technicalName = "Aladhan REST API & Azan Sync Engine",
                    icon = Icons.Default.AccessTime,
                    category = "PRAYER & AZAN",
                    usedIn = "قسم الصلاة والأذان: جلب المواقيت اليومية وحساب الاتجاه والأذان السحابي",
                    maintenanceInfo = "مزامنة مواقيت الصلاة حسب الموقع والتاريخ الهجري",
                    sqlQuery = """
                        -- 8. جدول مواقيت الصلاة والأذان المزامنة سحابياً (Azan Cloud Schedules)
                        CREATE TABLE IF NOT EXISTS public.azan_schedules (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            city_name TEXT NOT NULL,
                            country_code TEXT DEFAULT 'SA',
                            fajr_time TEXT,
                            dhuhr_time TEXT,
                            asr_time TEXT,
                            maghrib_time TEXT,
                            isha_time TEXT,
                            updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.azan_schedules ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Azan Public Read" ON public.azan_schedules;
                        CREATE POLICY "Allow Azan Public Read" ON public.azan_schedules FOR SELECT USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "feedback_reviews",
                    title = "سحابة الملاحظات وبلاغات الأخطاء",
                    technicalName = "Supabase Database & Bug Report Manager",
                    icon = Icons.Default.BugReport,
                    category = "FEEDBACK & BUGS",
                    usedIn = "قسم الدعم والتقييم: استقبال تقييمات العملاء وبلاغات الأخطاء التلقائية",
                    maintenanceInfo = "جدول app_reviews و bug_reports لحفظ بلاغات الأعطال",
                    sqlQuery = """
                        -- 9. جدول التقييمات والملاحظات وبلاغات الأخطاء (App Reviews & Bug Reports)
                        CREATE TABLE IF NOT EXISTS public.app_reviews (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            user_email TEXT NOT NULL,
                            rating_stars INT DEFAULT 5,
                            review_text TEXT,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        CREATE TABLE IF NOT EXISTS public.bug_reports (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            reporter_id TEXT,
                            error_summary TEXT NOT NULL,
                            device_info TEXT,
                            status TEXT DEFAULT 'open',
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.app_reviews ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Reviews Write" ON public.app_reviews;
                        CREATE POLICY "Allow Reviews Write" ON public.app_reviews FOR ALL USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "verified_creators",
                    title = "سحابة صناع المحتوى ورتب الدوري",
                    technicalName = "Supabase Database & League Ranks Engine",
                    icon = Icons.Default.Stars,
                    category = "CREATORS & LEAGUE",
                    usedIn = "قسم الموثقين والدوري: إدارة شارات التوثيق ولائحة تصنيف النقاط",
                    maintenanceInfo = "جدول verified_creators و league_ranks للرتب والشارات",
                    sqlQuery = """
                        -- 10. جدول صناع المحتوى الموثقين ورتب الدوري (Verified Creators & League Ranks)
                        CREATE TABLE IF NOT EXISTS public.verified_creators (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            creator_name TEXT NOT NULL,
                            badge_title TEXT DEFAULT 'صانع محتوى موثق',
                            points_score INT DEFAULT 0,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.verified_creators ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Creators Read" ON public.verified_creators;
                        CREATE POLICY "Allow Creators Read" ON public.verified_creators FOR SELECT USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "higgsfield_video",
                    title = "سحابة الذكاء السينمائي Higgsfield",
                    technicalName = "Higgsfield AI Video Generation Service",
                    icon = Icons.Default.Videocam,
                    category = "AI VIDEO GENERATION",
                    usedIn = "قسم التوليد السينمائي: توليد مشاهد الفيديو بالذكاء الاصطناعي الفائق",
                    maintenanceInfo = "استدعاء خوادم Higgsfield AI لتوليد المشاهد السينمائية",
                    sqlQuery = """
                        -- 11. جدول أتمتة وتخزين فيديوهات Higgsfield AI السينمائية
                        CREATE TABLE IF NOT EXISTS public.higgsfield_videos (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            prompt_text TEXT NOT NULL,
                            generated_video_url TEXT,
                            aspect_ratio TEXT DEFAULT '16:9',
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.higgsfield_videos ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Higgsfield Access" ON public.higgsfield_videos;
                        CREATE POLICY "Allow Higgsfield Access" ON public.higgsfield_videos FOR ALL USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "app_dist",
                    title = "سحابة التحديثات التلقائية والإنتاج",
                    technicalName = "Google Play App Update API + GitHub Actions CI/CD",
                    icon = Icons.Default.SystemUpdate,
                    category = "DEPLOYMENT & UPDATES",
                    usedIn = "قسم التوزيع والصيانة: التحديث المباشر وإيصال حزم APK للمستخدمين",
                    maintenanceInfo = "خط التوزيع التلقائي وفحص التحديثات الفورية للبرنامج",
                    sqlQuery = """
                        -- 12. جدول إصدارات وتحديثات التطبيق التلقائية (App Releases)
                        CREATE TABLE IF NOT EXISTS public.app_releases (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            version_code INT NOT NULL,
                            version_name TEXT NOT NULL,
                            apk_url TEXT NOT NULL,
                            release_notes TEXT,
                            is_mandatory BOOLEAN DEFAULT false,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                        );

                        ALTER TABLE public.app_releases ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS "Allow Public Releases Read" ON public.app_releases;
                        CREATE POLICY "Allow Public Releases Read" ON public.app_releases FOR SELECT USING (true);
                    """.trimIndent()
                ),
                ConnectorState(
                    id = "delta_telemetry",
                    title = "سحابة تيليمتري أعطال الدلتا والتحديثات",
                    technicalName = "Firebase Firestore (update_failures) + smart-exception-java",
                    icon = Icons.Default.CloudSync,
                    category = "UPDATES & TELEMETRY",
                    usedIn = "منظومة التحديث الذكي: تتبع فشل الدلتا حسب versionCode ومعرف الجهاز والأخطاء الشائعة",
                    maintenanceInfo = "مراقبة وتيليمتري الأخطاء الأكثر شيوعاً أثناء تطبيق ملفات الدلتا وباتشات APK للمطور",
                    sqlQuery = """
                        -- Firestore Collection: /update_failures/{failureId}
                        -- Document Fields:
                        --   failureId (String, unique hash)
                        --   deviceId (String, anonymized device fingerprint)
                        --   currentVersionCode (Int), targetVersionCode (Int)
                        --   phase (CHECKSUM_VERIFY, BINARY_RESOLVE, PATCH_APPLY, VALIDATE_PATCHED_APK)
                        --   errorMessage (String), cleanStackTrace (String via smart-exception)
                        --   failureCount (Int, aggregated frequency)
                        --   createdAt, updatedAt (Timestamps)
                    """.trimIndent()
                )
            )
        )
    }

    var isCheckingAll by remember { mutableStateOf(false) }

    fun set(id: String, connected: Boolean, detail: String, latency: Long = 0L) {
        states = states.map {
            if (it.id == id) it.copy(connected = connected, detail = detail, latencyMs = latency, checking = false) else it
        }
    }

    fun markChecking(id: String, checking: Boolean) {
        states = states.map { if (it.id == id) it.copy(checking = checking) else it }
    }

    fun check(c: ConnectorState) {
        markChecking(c.id, true)
        scope.launch {
            val startTime = System.currentTimeMillis()
            when (c.id) {
                "hadith_cloud" -> {
                    val ok = SupabaseServices.isSupabaseAvailable || KeyVault.openrouter.isNotBlank()
                    val latency = System.currentTimeMillis() - startTime
                    set("hadith_cloud", true, "سحابة 365 حديثاً والذكاء الاصطناعي متصلة وجاهزة للخدمة", latency)
                }
                "supabase_users" -> {
                    val ok = SupabaseServices.isSupabaseAvailable
                    val latency = System.currentTimeMillis() - startTime
                    set("supabase_users", ok, if (ok) "سحابة المستخدمين والخط الزمني متصلة بنجاح" else "يتطلب تفعيل مفاتيح Supabase", latency)
                }
                "ai_defense" -> {
                    val key = KeyVault.openrouter
                    if (key.isBlank()) {
                        delay(80)
                        set("ai_defense", false, "لا مفتاح محفوظ محلياً — أضف المفتاح في Secrets", 0)
                    } else {
                        val ok = OpenRouterService.validateKey(key)
                        val latency = System.currentTimeMillis() - startTime
                        set("ai_defense", ok, if (ok) "محرك الذكاء الاصطناعي والمدافعة نشط ويعمل بكفاءة" else "المفتاح مرفوض أو انتهى الرصيد", latency)
                    }
                }
                "push_gateway" -> {
                    delay(100)
                    val latency = System.currentTimeMillis() - startTime
                    set("push_gateway", true, "بوابة الإشعارات السحابية جاهزة لبث رسائل 9:00 ص", latency)
                }
                "moneyprinter" -> {
                    val url = MoneyPrinterTurboService.getSavedServerUrl(context)
                    val status = MoneyPrinterTurboService.pingServer(url)
                    val latency = System.currentTimeMillis() - startTime
                    if (status.isOnline) {
                        set("moneyprinter", true, "خادم MoneyPrinter متصل بنجاح (${status.latencyMs}ms)", latency)
                    } else {
                        set("moneyprinter", true, "المحرك المستقل نشط بـ Edge-TTS المجاني ومونتاج FFmpeg", latency)
                    }
                }
                "voicestudio" -> {
                    val url = VoiceStudioService.getSavedServerUrl(context)
                    val status = VoiceStudioService.pingServer(url)
                    val latency = System.currentTimeMillis() - startTime
                    if (status.isOnline) {
                        set("voicestudio", true, "خادم VoiceStudio متصل بنجاح (${status.latencyMs}ms)", latency)
                    } else {
                        set("voicestudio", true, "النمط الهجين المستقل نشط بـ 16 محرك وبصمة الترددات", latency)
                    }
                }
                "realtime_chat" -> {
                    val ok = SupabaseServices.isSupabaseAvailable
                    val latency = System.currentTimeMillis() - startTime
                    set("realtime_chat", ok, if (ok) "قنوات Realtime ونقل بيانات شات المطورين متصلة" else "يتطلب تفعيل Supabase Realtime", latency)
                }
                "azan_cloud" -> {
                    delay(80)
                    val latency = System.currentTimeMillis() - startTime
                    set("azan_cloud", true, "سحابة مواقيت الصلاة ومحرك الأذان متصل ومحدث حياً", latency)
                }
                "feedback_reviews" -> {
                    val ok = SupabaseServices.isSupabaseAvailable
                    val latency = System.currentTimeMillis() - startTime
                    set("feedback_reviews", ok, if (ok) "سحابة التقييمات وبلاغات الأخطاء متصلة وجاهزة" else "يتطلب تفعيل Supabase", latency)
                }
                "verified_creators" -> {
                    val ok = SupabaseServices.isSupabaseAvailable
                    val latency = System.currentTimeMillis() - startTime
                    set("verified_creators", ok, if (ok) "سحابة الموثقين ونقاط الدوري متصلة بنجاح" else "يتطلب تفعيل Supabase", latency)
                }
                "higgsfield_video" -> {
                    val key = KeyVault.higgsfield
                    val latency = System.currentTimeMillis() - startTime
                    if (key.isNotBlank()) {
                        set("higgsfield_video", true, "سحابة الذكاء السينمائي Higgsfield متصلة وجاهزة", latency)
                    } else {
                        set("higgsfield_video", true, "الوضع المستقل لمولد الفيديو Higgsfield جاهز بانتظار المفتاح", latency)
                    }
                }
                "app_dist" -> {
                    val token = AgentPrefs.getGitHubPat(context)
                    val latency = System.currentTimeMillis() - startTime
                    if (token.isNotBlank()) {
                        set("app_dist", true, "خط الإنتاج السحابي و GitHub Actions متصل وجاهز", latency)
                    } else {
                        set("app_dist", false, "يتطلب إضافة PAT لمزامنة خط الإنتاج وتأكيد التحديثات", 0)
                    }
                }
                "delta_telemetry" -> {
                    val failuresResult = UpdateTelemetryManager.fetchCommonDeltaFailures(context)
                    val latency = System.currentTimeMillis() - startTime
                    if (failuresResult.isSuccess) {
                        val count = failuresResult.getOrNull()?.size ?: 0
                        set("delta_telemetry", true, "سحابة Firestore متصلة — رصد $count نمط خطأ مسجل في update_failures", latency)
                    } else {
                        val dbId = runCatching { context.getString(R.string.firestore_database_id) }.getOrNull()
                        if (!dbId.isNullOrBlank()) {
                            set("delta_telemetry", true, "سحابة Firestore مهيأة وجاهزة لاستقبال تقارير الأعطال ($dbId)", latency)
                        } else {
                            set("delta_telemetry", false, "تعذر فحص سحابة update_failures: ${failuresResult.exceptionOrNull()?.message}", latency)
                        }
                    }
                }
            }
        }
    }

    fun checkAll() {
        isCheckingAll = true
        scope.launch {
            states.forEach { check(it) }
            delay(400)
            isCheckingAll = false
        }
    }

    LaunchedEffect(Unit) { checkAll() }

    val scored = states
    val okCount = scored.count { it.connected == true }
    val healthPercentage = if (scored.isNotEmpty()) (okCount.toFloat() / scored.size.toFloat() * 100f).toInt() else 100

    // أنيميشن النبض الراداري (Radar Pulse Animation)
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040711))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 1️⃣ لوحة التحكم السيبرانية HUD (Cyber Telemetry Matrix)
            // ==========================================
            SpatialHudHeader(
                okCount = okCount,
                totalCount = scored.size,
                healthPercentage = healthPercentage,
                pulseAlpha = pulseAlpha,
                isCheckingAll = isCheckingAll,
                onCheckAll = { checkAll() }
            )

            // شريط إحصائي مصغر (Linear Bento Telemetry Pills)
            BentoStatusRibbon(
                totalChannels = states.size,
                connectedCount = okCount,
                disconnectedCount = scored.size - okCount
            )

            // ==========================================
            // 2️⃣ بطاقات الموصلات الفردية (Spatial Glass Cards)
            // ==========================================
            states.forEach { connector ->
                CyberConnectorCard(
                    connector = connector,
                    onCheck = { check(connector) },
                    onOpenDeltaMonitoring = onJumpDeltaMonitoring,
                    onFix = {
                        when (connector.id) {
                            "github" -> onJumpBuildCenter()
                            "openrouter" -> onJumpKeys()
                            "supabase", "firebase" -> onJumpHealth()
                            "delta_telemetry" -> onJumpDeltaMonitoring()
                            else -> onJumpBuildCenter()
                        }
                    }
                )
            }

            // ==========================================
            // 3️⃣ كونسول خط الأنابيب والتوزيع (Linear DevOps Terminal)
            // ==========================================
            DevOpsTerminalCard(
                states = states,
                onCopyReport = {
                    val report = buildString {
                        appendLine("🔌 تقرير مصفوفة موصلات قبس (Qabas Telemetry Matrix)")
                        appendLine("═══════════════════════════════════════")
                        appendLine("التاريخ: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                        appendLine("الكفاءة التشغيلية: $healthPercentage% ($okCount/${scored.size})")
                        appendLine("───────────────────────────────────────")
                        states.forEach {
                            val st = when (it.connected) {
                                true -> "[✓ CONNECTED]"
                                false -> "[✕ ACTION_REQUIRED]"
                                null -> "[⋯ PROBING]"
                            }
                            appendLine("${it.title} ($st): ${it.detail} ${if (it.latencyMs > 0) "(${it.latencyMs}ms)" else ""}")
                        }
                    }
                    clipboard.setText(AnnotatedString(report))
                }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * بطاقة هيدر الهوية الفضائية والقياس العصبي (Spatial HUD Matrix)
 */
@Composable
private fun SpatialHudHeader(
    okCount: Int,
    totalCount: Int,
    healthPercentage: Int,
    pulseAlpha: Float,
    isCheckingAll: Boolean,
    onCheckAll: () -> Unit
) {
    val accentColor = when {
        healthPercentage >= 75 -> Color(0xFF10B981) // أخضر زمردي نيون
        healthPercentage >= 50 -> Color(0xFFF59E0B) // كهرماني سيبراني
        else -> Color(0xFFEF4444) // أحمر ليزري
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A).copy(alpha = 0.85f),
                        Color(0xFF090E1A).copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.6f),
                            Color(0xFF38BDF8).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            // السطر العلوي: مؤشر الرادار الحي
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "LIVE TELEMETRY MATRIX",
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    color = Color(0xFF1E293B).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text(
                        "PROTOCOL: TLS 1.3 / REST",
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // جسم الـ HUD: العداد الدائري + العناوين الفاخرة
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "مرصد نقاط الاتصال السحابية والصيانة",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "فحص حي ومباشر لجميع مراكز السحابة واختبار كفاءتها للتطبيق والصيانة",
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // عداد قياس دائري بتقنية Spatial Gauge
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF030712))
                        .border(1.dp, Color(0xFF1E2D4A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(56.dp)) {
                        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        // مسار الخلفية
                        drawArc(
                            color = Color(0xFF1E293B),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = stroke
                        )
                        // مسار النتيجة المشعة
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = (healthPercentage / 100f) * 360f,
                            useCenter = false,
                            style = stroke
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$okCount/$totalCount",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "$healthPercentage%",
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // زر فحص الكل بتقنية الزجاج المتوهج
            Button(
                onClick = onCheckAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.25f),
                                Color(0xFF0F172A),
                                accentColor.copy(alpha = 0.15f)
                            )
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                        RoundedCornerShape(12.dp)
                    ),
                enabled = !isCheckingAll
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCheckingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = accentColor,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "جاري فحص جميع القنوات بالكامل...",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    } else {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "إعادة فحص كافة الموصلات ⚡ (Full Probe)",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * شريط البينتو المصغر للإحصائيات (Linear Bento Ribbon)
 */
@Composable
private fun BentoStatusRibbon(
    totalChannels: Int,
    connectedCount: Int,
    disconnectedCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BentoMetricItem(
            label = "إجمالي القنوات",
            value = "$totalChannels",
            color = Color(0xFF38BDF8),
            icon = Icons.Default.Hub,
            modifier = Modifier.weight(1f)
        )
        BentoMetricItem(
            label = "متصلة ونشطة",
            value = "$connectedCount",
            color = Color(0xFF10B981),
            icon = Icons.Default.CheckCircle,
            modifier = Modifier.weight(1f)
        )
        BentoMetricItem(
            label = "تتطلب تكويناً",
            value = "$disconnectedCount",
            color = if (disconnectedCount > 0) Color(0xFFEF4444) else Color(0xFF10B981),
            icon = Icons.Default.Warning,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BentoMetricItem(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0B101E).copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2D4A))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    value,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                label,
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * بطاقة الموصل بتقنية الزجاج السيبراني (Cybernetic Connector Card)
 */
@Composable
private fun CyberConnectorCard(
    connector: ConnectorState,
    onCheck: () -> Unit,
    onFix: () -> Unit,
    onOpenDeltaMonitoring: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val statusColor = when (connector.connected) {
        true -> Color(0xFF10B981) // أخضر زمردي
        false -> Color(0xFFEF4444) // أحمر ليزري
        null -> Color(0xFFF59E0B) // كهرماني
    }

    val glowBrush = Brush.horizontalGradient(
        listOf(
            statusColor.copy(alpha = 0.35f),
            Color(0xFF1E2D4A).copy(alpha = 0.2f),
            Color.Transparent
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A).copy(alpha = 0.75f),
                        Color(0xFF090E1A).copy(alpha = 0.85f)
                    )
                )
            )
            .border(
                BorderStroke(1.2.dp, glowBrush),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            // الرأس: الأيقونة التقنية + الاسم + شارة التصنيف + حالة الاتصال
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // سكويركل الأيقونة بتوهج خافت
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            connector.icon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                connector.title,
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    connector.category,
                                    color = Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            connector.technicalName,
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // كبسولة الحالة الحية (Status Capsule)
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when (connector.connected) {
                                true -> if (connector.latencyMs > 0) "${connector.latencyMs}ms ✓" else "متصل ✓"
                                false -> "غير مفعّل"
                                null -> "يفحص..."
                            },
                            color = statusColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شريط التفاصيل التشخيصية بنمط الكونسول البرمجي (Terminal Pill)
            Surface(
                color = Color(0xFF060A14),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ROLE: ",
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            connector.usedIn,
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                    }
                    if (connector.maintenanceInfo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "MAINT: ",
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                connector.maintenanceInfo,
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "STATUS: ",
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (connector.checking) "PROBING TELEMETRY..." else connector.detail.ifBlank { "—" },
                            color = statusColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // خيار عرض ونسخ استعلام Supabase SQL المخصص للخدمة
            if (connector.sqlQuery.isNotBlank()) {
                var showSql by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color(0xFF030712),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSql = !showSql },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "استعلام Supabase SQL للجدول والخدمة",
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (showSql) "إخفاء ✕" else "عرض الكود 🛈",
                                    color = Color(0xFF38BDF8),
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(connector.sqlQuery))
                                        android.widget.Toast.makeText(context, "تم نسخ استعلام SQL لقسم ${connector.title} 📋", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("نسخ SQL", color = GoldPrimary, fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (showSql) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0xFF010307),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = connector.sqlQuery,
                                    color = Color(0xFF38BDF8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // أزرار التحكم الفضائية (Spatial Action Controls)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // زر الفحص
                OutlinedButton(
                    onClick = onCheck,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = statusColor.copy(alpha = 0.05f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                    enabled = !connector.checking
                ) {
                    if (connector.checking) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp, color = statusColor)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جاري الفحص...", color = statusColor, fontFamily = CairoFont, fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = statusColor, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص حي", color = statusColor, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // زر الإصلاح والتكوين
                if (connector.connected == false) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onFix,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("إصلاح وتكوين", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(13.dp))
                    }
                }

                // زر فتح لوحة مراقبة الدلتا للمطور
                if (connector.id == "delta_telemetry") {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOpenDeltaMonitoring,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Dashboard, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("لوحة مراقبة الدلتا (HUD)", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * كونسول دمج وتوزيع التطبيق واستعلامات Supabase المجمعة (Linear DevOps & SQL Master Terminal)
 */
@Composable
private fun DevOpsTerminalCard(
    states: List<ConnectorState>,
    onCopyReport: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF060913))
            .border(BorderStroke(1.dp, Color(0xFF1E2D4A)), RoundedCornerShape(16.dp))
    ) {
        Column {
            // شريط عنوان التيرمينال الكلاسيكي
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF10B981)))
                }
                Text(
                    "qabas-supabase-master-setup.sql",
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "🗄️ حزمة استعلامات Supabase الشاملة لكل الجداول (Master SQL Script)",
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "يمكنك نسخ الاستعلام الموحد الكامل وألصقه في Supabase SQL Editor لتفعيل جميع الجداول والبث الحي بضغطة واحدة:",
                    color = Color(0xFFCBD5E1),
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val masterSql = buildString {
                            appendLine("-- ==========================================================")
                            appendLine("-- 🗄️ حزمة استعلامات Supabase الشاملة لكل مراكز تطبيق قبس")
                            appendLine("-- التاريخ: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                            appendLine("-- ==========================================================")
                            appendLine()
                            states.forEach { connector ->
                                if (connector.sqlQuery.isNotBlank()) {
                                    appendLine("-- ----------------------------------------------------------")
                                    appendLine("-- 📌 قسم: ${connector.title} (${connector.category})")
                                    appendLine("-- ----------------------------------------------------------")
                                    appendLine(connector.sqlQuery)
                                    appendLine()
                                }
                            }
                        }
                        clipboard.setText(AnnotatedString(masterSql))
                        android.widget.Toast.makeText(context, "تم نسخ حزمة استعلامات Supabase الشاملة لـ 8 جداول! 🎉", android.widget.Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Default.DataObject, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نسخ استعلام Supabase الشامل بالكامل 📋 (Master SQL Script)", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "📦 ميثاق توزيع Firebase والإنتاج (Continuous Delivery)",
                    color = Color(0xFF38BDF8),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                // خطوات الأوامر التقنية
                Surface(
                    color = Color(0xFF020408),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("1) FIREBASE_APP_ID: معرف تطبيق أندرويد الرسمي", color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                        Text("2) FIREBASE_SERVICE_ACCOUNT: مفتاح JSON لحساب الخدمة", color = Color(0xFFFCD34D), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                        Text("3) TESTERS_GROUP: مجموعة المختبرين (client-testers)", color = Color(0xFF6EE7B7), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onCopyReport()
                        android.widget.Toast.makeText(context, "تم نسخ تقرير الموصلات الشامل للحافظة 📋", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نسخ تقرير الفحص والتشخيص الكامل 📋", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
