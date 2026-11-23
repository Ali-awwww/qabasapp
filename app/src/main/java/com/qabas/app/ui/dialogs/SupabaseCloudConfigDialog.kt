package com.qabas.app.ui.dialogs

import com.qabas.app.AgentPrefs
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.GoogleAuthHelper
import com.qabas.app.SupabaseConfig
import com.qabas.app.SupabaseServices
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * نافذة تشخيص وإعدادات الاتصال السحابي بـ Supabase وقاعدة البيانات
 */
@Composable
fun SupabaseCloudConfigDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val sp = remember(context) { AgentPrefs.instance(context) }
    var inputUrl by remember { mutableStateOf(sp.getString("supabase_url", "")?.ifBlank { SupabaseConfig.url } ?: SupabaseConfig.url) }
    var inputAnonKey by remember { mutableStateOf(sp.getString("supabase_anon_key", "")?.ifBlank { SupabaseConfig.key } ?: SupabaseConfig.key) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: الإعدادات والاتصال, 1: كود SQL للجداول, 2: ربط Google

    val callbackUrl = GoogleAuthHelper.SUPABASE_GOOGLE_CALLBACK_URL
    val sha1Fingerprint = "B9:4C:DE:BF:D3:73:F2:F1:D6:8E:6D:0B:5B:14:65:43:E4:F6:06:49"

    val sqlSchemaScript = """
-- 1. جدول المستخدمين والصلاحيات
CREATE TABLE IF NOT EXISTS public.users (
    id TEXT PRIMARY KEY,
    external_id TEXT,
    email TEXT,
    name TEXT,
    type TEXT DEFAULT 'free',
    status TEXT DEFAULT 'active',
    strikes INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- 2. جدول تفضيلات وإعدادات المستخدم
CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id TEXT PRIMARY KEY,
    preferences_json TEXT NOT NULL,
    updated_at_millis BIGINT DEFAULT 0
);

-- 3. جدول البروفايل الموحد
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY,
    display_name TEXT,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    role TEXT DEFAULT 'user',
    preferences_json TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at_millis BIGINT DEFAULT 0
);

-- 4. جدول المشاريع والمونتاج السحابي
CREATE TABLE IF NOT EXISTS public.projects (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT DEFAULT 'draft',
    cost DOUBLE PRECISION DEFAULT 0.0,
    tags JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- 5. جدول الريلز ومقاطع الفيديو القصيرة
CREATE TABLE IF NOT EXISTS public.reels (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT,
    tags TEXT[] DEFAULT ARRAY[]::TEXT[],
    likes_count INT DEFAULT 0,
    comments_count INT DEFAULT 0,
    shares_count INT DEFAULT 0,
    views_count INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- 6. تفاعلات وتعليقات الريلز
CREATE TABLE IF NOT EXISTS public.reel_likes (
    id TEXT PRIMARY KEY,
    reel_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
    UNIQUE(reel_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.reel_comments (
    id TEXT PRIMARY KEY,
    reel_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    comment_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- 6A. علاقات متابعة صناع المحتوى
CREATE TABLE IF NOT EXISTS public.creator_follows (
    follower_id TEXT NOT NULL,
    creator_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
    PRIMARY KEY (follower_id, creator_id)
);
CREATE INDEX IF NOT EXISTS creator_follows_creator_idx ON public.creator_follows (creator_id);
CREATE INDEX IF NOT EXISTS creator_follows_follower_idx ON public.creator_follows (follower_id);

-- 7. سياسات RLS بأقل صلاحية (ق52)
-- نسخة ثانية من سياسات FOR ALL USING (true) التي قُتلت في ConnectorsSection في ق34
-- بقيت هنا: أي مفتاح anon كان يقرأ/يكتب/يحذف كل صفوف users وprojects وreels وprofiles
-- وuser_settings وreel_comments — منها بريد المستخدمين في users.
-- السياسات أدناه مطابقة للترحيلات الحية في supabase/migrations
-- (users: 20261103130000_canonical_cloud_user_identity، وprojects: 202611120001،
-- وprofiles: 20261106220000/20261106221000/20261106222000، وreels وreel_comments: 202610050003)
-- مع إضافتين يوافقهما استعمال العميل الفعلي (بلا سياسة حية لهما بعد):
-- حذف الريلز الخاصة، وتحديث التعليق الخاص.
-- ملاحظة: id وuser_id نصوص (TEXT) في هذا المخطط، فقارَن auth.uid() بعد تحويله لنص.
-- أولاً: إسقاط السياسات المفتوحة القديمة لمن لصق السكربت السابق في مشروعه —
-- بلا هذا الإسقاط تبقى السياسة المفتوحة سارية بجانب الجديدة.
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.creator_follows ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public access for app anon users" ON public.users;
DROP POLICY IF EXISTS "Allow public access for app anon projects" ON public.projects;
DROP POLICY IF EXISTS "Allow public access for app anon reels" ON public.reels;
DROP POLICY IF EXISTS "Allow public access for app anon profiles" ON public.profiles;
DROP POLICY IF EXISTS "Allow public access for app anon settings" ON public.user_settings;
DROP POLICY IF EXISTS "Allow public access for app anon comments" ON public.reel_comments;

-- users: الصاحب فقط (قراءة/إدراج/تحديث) + قراءة/تحديث المشرفين عبر app_metadata.
-- بلا سياسة حذف: حذف الحساب إجراء دورة حياة لا يُعرَّض للعميل (كما في الترحيل الحي)؛
-- ومسح العميل الاحتياطي deleteOwnAccountRows يبقى أفضل جهد ويُسجَّل تحذيراً عند الرفض.
DROP POLICY IF EXISTS users_select_own ON public.users;
CREATE POLICY users_select_own ON public.users FOR SELECT TO authenticated USING ((SELECT auth.uid())::text = id);
DROP POLICY IF EXISTS users_insert_own ON public.users;
CREATE POLICY users_insert_own ON public.users FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid())::text = id);
DROP POLICY IF EXISTS users_update_own ON public.users;
CREATE POLICY users_update_own ON public.users FOR UPDATE TO authenticated USING ((SELECT auth.uid())::text = id) WITH CHECK ((SELECT auth.uid())::text = id);
DROP POLICY IF EXISTS users_admin_select ON public.users;
CREATE POLICY users_admin_select ON public.users FOR SELECT TO authenticated USING (lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner'));
DROP POLICY IF EXISTS users_admin_update ON public.users;
CREATE POLICY users_admin_update ON public.users FOR UPDATE TO authenticated USING (lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner')) WITH CHECK (lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner'));

-- projects: الصاحب فقط (قراءة/إدراج/تحديث/حذف)؛ وgetFeed يعود بملكية المستخدم وحده كما في الحي.
REVOKE ALL ON TABLE public.projects FROM anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.projects TO authenticated;
GRANT ALL ON TABLE public.projects TO service_role;
DROP POLICY IF EXISTS projects_select_own ON public.projects;
CREATE POLICY projects_select_own ON public.projects FOR SELECT TO authenticated USING (user_id = auth.uid()::text);
DROP POLICY IF EXISTS projects_insert_own ON public.projects;
CREATE POLICY projects_insert_own ON public.projects FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid()::text);
DROP POLICY IF EXISTS projects_update_own ON public.projects;
CREATE POLICY projects_update_own ON public.projects FOR UPDATE TO authenticated USING (user_id = auth.uid()::text) WITH CHECK (user_id = auth.uid()::text);
DROP POLICY IF EXISTS projects_delete_own ON public.projects;
CREATE POLICY projects_delete_own ON public.projects FOR DELETE TO authenticated USING (user_id = auth.uid()::text);

-- profiles: الصاحب فقط؛ والإدراج يشترط role='user' (التطبيق يدرج به دائماً ولا يرفّع الأدوار أبداً).
GRANT SELECT ON TABLE public.profiles TO authenticated;
GRANT INSERT ON TABLE public.profiles TO authenticated;
GRANT UPDATE (email, full_name, avatar_url) ON TABLE public.profiles TO authenticated;
DROP POLICY IF EXISTS profiles_select_own ON public.profiles;
CREATE POLICY profiles_select_own ON public.profiles FOR SELECT TO authenticated USING (id = (SELECT auth.uid())::text);
DROP POLICY IF EXISTS profiles_insert_own ON public.profiles;
CREATE POLICY profiles_insert_own ON public.profiles FOR INSERT TO authenticated WITH CHECK (id = (SELECT auth.uid())::text AND role = 'user');
DROP POLICY IF EXISTS profiles_update_own ON public.profiles;
CREATE POLICY profiles_update_own ON public.profiles FOR UPDATE TO authenticated USING (id = (SELECT auth.uid())::text) WITH CHECK (id = (SELECT auth.uid())::text);

-- user_settings: الصاحب فقط (upsert يحتاج إدراجاً وتحديثاً؛ والحذف لمسح الحساب الاحتياطي).
DROP POLICY IF EXISTS user_settings_select_own ON public.user_settings;
CREATE POLICY user_settings_select_own ON public.user_settings FOR SELECT TO authenticated USING (user_id = auth.uid()::text);
DROP POLICY IF EXISTS user_settings_insert_own ON public.user_settings;
CREATE POLICY user_settings_insert_own ON public.user_settings FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid()::text);
DROP POLICY IF EXISTS user_settings_update_own ON public.user_settings;
CREATE POLICY user_settings_update_own ON public.user_settings FOR UPDATE TO authenticated USING (user_id = auth.uid()::text) WITH CHECK (user_id = auth.uid()::text);
DROP POLICY IF EXISTS user_settings_delete_own ON public.user_settings;
CREATE POLICY user_settings_delete_own ON public.user_settings FOR DELETE TO authenticated USING (user_id = auth.uid()::text);

-- reels: قراءة النشطة للجميع؛ وكتابة الصاحب فقط (حذف الخاصة إضافة بلا سياسة حية بعد).
DROP POLICY IF EXISTS "Anyone can view active reels" ON public.reels;
CREATE POLICY "Anyone can view active reels" ON public.reels FOR SELECT USING (is_active = TRUE);
DROP POLICY IF EXISTS "Users can create their own reels" ON public.reels;
CREATE POLICY "Users can create their own reels" ON public.reels FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can update their own reels" ON public.reels;
CREATE POLICY "Users can update their own reels" ON public.reels FOR UPDATE USING (auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can delete their own reels" ON public.reels;
CREATE POLICY "Users can delete their own reels" ON public.reels FOR DELETE USING (auth.uid()::text = user_id::text);

-- reel_comments: قراءة للجميع؛ وكتابة/تحديث/حذف الصاحب فقط (التحديث إضافة بلا سياسة حية بعد).
DROP POLICY IF EXISTS "Anyone can view comments" ON public.reel_comments;
CREATE POLICY "Anyone can view comments" ON public.reel_comments FOR SELECT USING (TRUE);
DROP POLICY IF EXISTS "Authenticated users can comment" ON public.reel_comments;
CREATE POLICY "Authenticated users can comment" ON public.reel_comments FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can update their own comments" ON public.reel_comments;
CREATE POLICY "Users can update their own comments" ON public.reel_comments FOR UPDATE USING (auth.uid()::text = user_id::text) WITH CHECK (auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can delete their own comments" ON public.reel_comments;
CREATE POLICY "Users can delete their own comments" ON public.reel_comments FOR DELETE USING (auth.uid()::text = user_id::text);

-- Keep social relationship rows private; users can read/write only their own rows.
DROP POLICY IF EXISTS "Allow public access for app anon likes" ON public.reel_likes;
DROP POLICY IF EXISTS "Allow public access for app anon creator follows" ON public.creator_follows;
DROP POLICY IF EXISTS "Anyone can view likes" ON public.reel_likes;
DROP POLICY IF EXISTS reel_likes_public_read ON public.reel_likes;
DROP POLICY IF EXISTS creator_follows_public_read ON public.creator_follows;

DROP POLICY IF EXISTS reel_likes_select_own ON public.reel_likes;
CREATE POLICY reel_likes_select_own ON public.reel_likes
    FOR SELECT TO authenticated USING ((SELECT auth.uid())::text = user_id);
DROP POLICY IF EXISTS reel_likes_insert_own ON public.reel_likes;
CREATE POLICY reel_likes_insert_own ON public.reel_likes
    FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid())::text = user_id);
DROP POLICY IF EXISTS reel_likes_update_own ON public.reel_likes;
CREATE POLICY reel_likes_update_own ON public.reel_likes
    FOR UPDATE TO authenticated
    USING ((SELECT auth.uid())::text = user_id)
    WITH CHECK ((SELECT auth.uid())::text = user_id);
DROP POLICY IF EXISTS reel_likes_delete_own ON public.reel_likes;
CREATE POLICY reel_likes_delete_own ON public.reel_likes
    FOR DELETE TO authenticated USING ((SELECT auth.uid())::text = user_id);

DROP POLICY IF EXISTS creator_follows_select_own ON public.creator_follows;
CREATE POLICY creator_follows_select_own ON public.creator_follows
    FOR SELECT TO authenticated USING ((SELECT auth.uid())::text = follower_id);
DROP POLICY IF EXISTS creator_follows_insert_self ON public.creator_follows;
CREATE POLICY creator_follows_insert_self ON public.creator_follows
    FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid())::text = follower_id);
DROP POLICY IF EXISTS creator_follows_update_self ON public.creator_follows;
CREATE POLICY creator_follows_update_self ON public.creator_follows
    FOR UPDATE TO authenticated
    USING ((SELECT auth.uid())::text = follower_id)
    WITH CHECK ((SELECT auth.uid())::text = follower_id);
DROP POLICY IF EXISTS creator_follows_delete_self ON public.creator_follows;
CREATE POLICY creator_follows_delete_self ON public.creator_follows
    FOR DELETE TO authenticated USING ((SELECT auth.uid())::text = follower_id);

REVOKE ALL ON TABLE public.reel_likes FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.creator_follows FROM PUBLIC, anon;
GRANT SELECT, INSERT, DELETE ON TABLE public.reel_likes TO authenticated;
GRANT UPDATE (reel_id, user_id) ON TABLE public.reel_likes TO authenticated;
GRANT SELECT, INSERT, DELETE ON TABLE public.creator_follows TO authenticated;
GRANT UPDATE (follower_id, creator_id, created_at) ON TABLE public.creator_follows TO authenticated;

CREATE INDEX IF NOT EXISTS creator_follows_creator_idx
    ON public.creator_follows (creator_id);
CREATE OR REPLACE FUNCTION public.get_creator_follower_count(p_creator_id text)
RETURNS bigint LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog SET row_security = off
AS $$
    SELECT CASE
        WHEN p_creator_id = (SELECT auth.uid())::text
          OR EXISTS (
              SELECT 1 FROM public.profiles AS p
              JOIN public.reels AS r ON r.user_id = p.id
              WHERE p.id = p_creator_id AND r.is_active IS TRUE
          )
        THEN (SELECT count(*) FROM public.creator_follows AS f WHERE f.creator_id = p_creator_id)
        ELSE 0::bigint
    END;
$$;
REVOKE ALL ON FUNCTION public.get_creator_follower_count(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_creator_follower_count(text) TO anon, authenticated;
DO $$ BEGIN
    IF to_regprocedure('public.sync_reel_likes_count()') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.sync_reel_likes_count() FROM PUBLIC, anon, authenticated';
    END IF;
END $$;
    """.trimIndent()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = CutCornerShape(16.dp),
            color = DeepSlate,
            border = BorderStroke(1.2.dp, PresetPrimary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = PresetPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("إدارة الربط السحابي (Supabase)", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("CLOUD_INTEGRATION // POSTGRESQL & AUTH", color = PresetPrimary.copy(alpha = 0.7f), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dual Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("بيانات الربط" to 0, "أكواد SQL للجداول" to 1, "إعداد Google Auth" to 2).forEach { (label, idx) ->
                        val isSel = selectedTab == idx
                        Surface(
                            modifier = Modifier.weight(1f).clickable { selectedTab = idx },
                            shape = CutCornerShape(8.dp),
                            color = if (isSel) PresetPrimary.copy(alpha = 0.15f) else CardSurface,
                            border = BorderStroke(1.dp, if (isSel) PresetPrimary else TextPrimary.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) PresetPrimary else TextTertiary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(DeepSlate.copy(alpha = 0.7f), CutCornerShape(10.dp))
                        .border(1.dp, TextPrimary.copy(alpha = 0.05f), CutCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // Connection Form
                                Text("رابط مشروع Supabase (Project URL):", color = TextPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it.trim() },
                                    placeholder = { Text("https://your-id.supabase.co", fontSize = 11.sp, color = TextTertiary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PresetPrimary,
                                        unfocusedBorderColor = TextPrimary.copy(alpha = 0.15f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = CutCornerShape(8.dp),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text("مفتاح API العام (anon public key):", color = TextPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = inputAnonKey,
                                    onValueChange = { inputAnonKey = it.trim() },
                                    placeholder = { Text("eyJhbGciOi...", fontSize = 11.sp, color = TextTertiary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PresetPrimary,
                                        unfocusedBorderColor = TextPrimary.copy(alpha = 0.15f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = CutCornerShape(8.dp),
                                    maxLines = 3
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Status Banner
                                if (testStatus != null) {
                                    val isOk = testStatus?.startsWith("✓") == true
                                    Surface(
                                        color = if (isOk) ThemeSuccess.copy(alpha = 0.25f) else ThemeDestructive.copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, if (isOk) ThemeSuccess else ThemeDestructive),
                                        shape = CutCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = testStatus ?: "",
                                            color = TextPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                }

                                // Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            isSaving = true
                                            sp.edit()
                                                .putString("supabase_url", inputUrl)
                                                .putString("supabase_anon_key", inputAnonKey)
                                                .apply()
                                            SupabaseConfig.resetClient()
                                            isSaving = false
                                            Toast.makeText(context, "تم حفظ الإعدادات وتحديث عميل السحابة بنجاح ☁️", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                                        shape = CutCornerShape(8.dp)
                                    ) {
                                        Text("حفظ الإعدادات 💾", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            isTesting = true
                                            testStatus = "جاري فحص الاتصال بالخادم..."
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val start = System.currentTimeMillis()
                                                val available = SupabaseServices.isSupabaseAvailable
                                                if (!available) {
                                                    withContext(Dispatchers.Main) {
                                                        testStatus = "✗ تعذر الاتصال: يرجى التأكد من كتابة الرابط ومفتاح anon بشكل صحيح."
                                                        isTesting = false
                                                    }
                                                    return@launch
                                                }
                                                val pingSuccess = try {
                                                    val reels = SupabaseServices.Profiles.getActiveReels()
                                                    reels != null
                                                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                                                    false
                                                }
                                                val elapsed = System.currentTimeMillis() - start
                                                withContext(Dispatchers.Main) {
                                                    if (pingSuccess) {
                                                        testStatus = "✓ الاتصال السحابي ممتاز! زمن الاستجابة: ${elapsed}ms"
                                                    } else {
                                                        testStatus = "✓ الرابط متصل بنجاح، لكن بعض الجداول قد تحتاج إلى تنفيذ كود SQL المرفق في التبويب الثاني."
                                                    }
                                                    isTesting = false
                                                }
                                            }
                                        },
                                        enabled = !isTesting,
                                        modifier = Modifier.weight(1f),
                                        border = BorderStroke(1.dp, PresetPrimary),
                                        shape = CutCornerShape(8.dp)
                                    ) {
                                        Text(if (isTesting) "جاري الفحص..." else "فحص الاتصال ⚡", color = PresetPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                                    }
                                }
                            }

                            1 -> {
                                // SQL Schema Script
                                Text("أكواد SQL لإنشاء جداول المشروع في Supabase:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("افتح Supabase Dashboard > SQL Editor والصق الكود التالي واضغط RUN:", color = TextTertiary, fontFamily = CairoFont, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    color = DeepSlate,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("SCHEMA.SQL", color = PresetPrimary, fontFamily = RobotoMonoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Button(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(sqlSchemaScript))
                                                    Toast.makeText(context, "تم نسخ كود SQL كاملاً! الصقه في Supabase SQL Editor 📋", Toast.LENGTH_LONG).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                                                shape = CutCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("نسخ الكود بالكامل", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = sqlSchemaScript,
                                            color = TextSecondary,
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 9.sp,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }

                            2 -> {
                                // Google Auth Guidance
                                Text("ربط Google Cloud بـ Supabase Auth:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Text("1. رابط إعادة التوجيه (Callback URL) في Supabase:", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                                Surface(
                                    color = ThemeSecondarySurface,
                                    shape = CutCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(callbackUrl, color = TextPrimary, fontFamily = RobotoMonoFont, fontSize = 9.5.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(callbackUrl))
                                                Toast.makeText(context, "تم نسخ الرابط!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("2. بصمة توقيع التطبيق (SHA-1 Fingerprint):", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                                Surface(
                                    color = ThemeSecondarySurface,
                                    shape = CutCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(sha1Fingerprint, color = TextPrimary, fontFamily = RobotoMonoFont, fontSize = 9.5.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(sha1Fingerprint))
                                                Toast.makeText(context, "تم نسخ بصمة SHA-1!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("3. اسم حزمة التطبيق (Package Name):", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                                Surface(
                                    color = ThemeSecondarySurface,
                                    shape = CutCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("com.aistudio.qabas.kxmpzq", color = TextPrimary, fontFamily = RobotoMonoFont, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString("com.aistudio.qabas.kxmpzq"))
                                                Toast.makeText(context, "تم نسخ اسم الحزمة!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
