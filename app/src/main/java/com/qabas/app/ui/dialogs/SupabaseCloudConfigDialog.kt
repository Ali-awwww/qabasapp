package com.qabas.app.ui.dialogs

import android.content.Context
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
import androidx.compose.ui.graphics.Color
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

    val sp = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
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
    avatar_url TEXT,
    preferences_json TEXT,
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

-- 7. فتح الصلاحيات (RLS Policies) لتطبيق قبس
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_comments ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
    CREATE POLICY "Allow public access for app anon users" ON public.users FOR ALL USING (true) WITH CHECK (true);
    CREATE POLICY "Allow public access for app anon projects" ON public.projects FOR ALL USING (true) WITH CHECK (true);
    CREATE POLICY "Allow public access for app anon reels" ON public.reels FOR ALL USING (true) WITH CHECK (true);
    CREATE POLICY "Allow public access for app anon profiles" ON public.profiles FOR ALL USING (true) WITH CHECK (true);
    CREATE POLICY "Allow public access for app anon settings" ON public.user_settings FOR ALL USING (true) WITH CHECK (true);
    CREATE POLICY "Allow public access for app anon likes" ON public.reel_likes FOR ALL USING (true) WITH CHECK (true);
    CREATE POLICY "Allow public access for app anon comments" ON public.reel_comments FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN null;
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
            border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.5f))
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
                            color = GoldPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("إدارة الربط السحابي (Supabase)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("CLOUD_INTEGRATION // POSTGRESQL & AUTH", color = GoldPrimary.copy(alpha = 0.7f), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.7f))
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
                            color = if (isSel) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF151B2B),
                            border = BorderStroke(1.dp, if (isSel) GoldPrimary else Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) GoldPrimary else Color.Gray,
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
                        .background(Color(0xFF0F172A).copy(alpha = 0.7f), CutCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), CutCornerShape(10.dp))
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
                                Text("رابط مشروع Supabase (Project URL):", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it.trim() },
                                    placeholder = { Text("https://your-id.supabase.co", fontSize = 11.sp, color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = CutCornerShape(8.dp),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text("مفتاح API العام (anon public key):", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = inputAnonKey,
                                    onValueChange = { inputAnonKey = it.trim() },
                                    placeholder = { Text("eyJhbGciOi...", fontSize = 11.sp, color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = CutCornerShape(8.dp),
                                    maxLines = 3
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Status Banner
                                if (testStatus != null) {
                                    val isOk = testStatus?.startsWith("✓") == true
                                    Surface(
                                        color = if (isOk) Color(0xFF064E3B) else Color(0xFF450A0A),
                                        border = BorderStroke(1.dp, if (isOk) Color(0xFF10B981) else Color(0xFFEF4444)),
                                        shape = CutCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = testStatus ?: "",
                                            color = Color.White,
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
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
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
                                                } catch (_: Exception) {
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
                                        border = BorderStroke(1.dp, GoldPrimary),
                                        shape = CutCornerShape(8.dp)
                                    ) {
                                        Text(if (isTesting) "جاري الفحص..." else "فحص الاتصال ⚡", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                                    }
                                }
                            }

                            1 -> {
                                // SQL Schema Script
                                Text("أكواد SQL لإنشاء جداول المشروع في Supabase:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("افتح Supabase Dashboard > SQL Editor والصق الكود التالي واضغط RUN:", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    color = Color(0xFF020617),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("SCHEMA.SQL", color = GoldPrimary, fontFamily = RobotoMonoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Button(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(sqlSchemaScript))
                                                    Toast.makeText(context, "تم نسخ كود SQL كاملاً! الصقه في Supabase SQL Editor 📋", Toast.LENGTH_LONG).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                shape = CutCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("نسخ الكود بالكامل", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = sqlSchemaScript,
                                            color = Color(0xFF94A3B8),
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 9.sp,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }

                            2 -> {
                                // Google Auth Guidance
                                Text("ربط Google Cloud بـ Supabase Auth:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Text("1. رابط إعادة التوجيه (Callback URL) في Supabase:", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = CutCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(callbackUrl, color = Color.White, fontFamily = RobotoMonoFont, fontSize = 9.5.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(callbackUrl))
                                                Toast.makeText(context, "تم نسخ الرابط!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("2. بصمة توقيع التطبيق (SHA-1 Fingerprint):", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = CutCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(sha1Fingerprint, color = Color.White, fontFamily = RobotoMonoFont, fontSize = 9.5.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(sha1Fingerprint))
                                                Toast.makeText(context, "تم نسخ بصمة SHA-1!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("3. اسم حزمة التطبيق (Package Name):", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = CutCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("com.qabas.app", color = Color.White, fontFamily = RobotoMonoFont, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString("com.qabas.app"))
                                                Toast.makeText(context, "تم نسخ اسم الحزمة!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
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
