package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*

/**
 * 🛰️ شاشة / نافذة شارة الإرشادات ودليل الربط والاستخدام الفضائي
 * Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@Composable
fun SpatialDevIntegrationGuideDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onTestConnection: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    // نبض الإطار السيبراني المتوهج
    val infiniteTransition = rememberInfiniteTransition(label = "guide_hud_pulse")
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guide_pulse_val"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030712).copy(alpha = 0.88f))
                .padding(horizontal = 14.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .border(
                        1.4.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF06B6D4).copy(alpha = borderPulse),
                                GoldPrimary.copy(alpha = borderPulse * 0.8f),
                                Color(0xFF10B981).copy(alpha = borderPulse)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .testTag("spatial_dev_guide_dialog"),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = Color(0xFF090D1A).copy(alpha = 0.96f),
                borderColor = Color(0xFF06B6D4).copy(alpha = 0.35f),
                borderWidth = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // ═══════════════════════════════════════════════════
                    // 1. ترويسة الـ HUD والشارة الفضائية
                    // ═══════════════════════════════════════════════════
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A))
                                .border(1.5.dp, Brush.sweepGradient(listOf(Color(0xFF06B6D4), GoldPrimary, Color(0xFF10B981))), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "دليل الربط والتشغيل الفضائي 🛰️",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF06B6D4).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF06B6D4).copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        "HUD_GUIDE",
                                        color = Color(0xFF06B6D4),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                "إرشادات ربط الذكاء الاصطناعي، مستودع GitHub، وتفعيل المعالجة الذاتية تحت تفويضك",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ═══════════════════════════════════════════════════
                    // 2. شريط التبويبات الفضائي (Spatial Segmented HUD Tabs)
                    // ═══════════════════════════════════════════════════
                    val tabs = listOf(
                        "🤖 ربط الذكاء الاصطناعي",
                        "🐙 ربط GitHub",
                        "🛡️ بوابة التفويض",
                        "⚡ أدوات القسم"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Surface(
                                modifier = Modifier.clickable {
                                    AppSoundManager.playClick()
                                    selectedTab = index
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF06B6D4).copy(alpha = 0.22f) else Color(0xFF0F172A).copy(alpha = 0.6f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF06B6D4) else Color.White.copy(alpha = 0.1f)
                                )
                            ) {
                                Text(
                                    title,
                                    color = if (isSelected) Color(0xFF22D3EE) else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ═══════════════════════════════════════════════════
                    // 3. محتوى التبويبات مع شرح تفصيلي وعملي
                    // ═══════════════════════════════════════════════════
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (selectedTab) {
                                0 -> AiIntegrationGuideTab(context = context, onOpenSettings = onOpenSettings)
                                1 -> GitHubIntegrationGuideTab(context = context, onOpenSettings = onOpenSettings)
                                2 -> ConsentGateGuideTab()
                                3 -> DevToolsAndUsageGuideTab(context = context)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ═══════════════════════════════════════════════════
                    // 4. أزرار الإجراءات السريعة في الأسفل
                    // ═══════════════════════════════════════════════════
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenSettings()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE))
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فتح الإعدادات ⚙️", fontFamily = CairoFont, fontSize = 11.5.sp)
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                        ) {
                            Text("فهمت وأبدأ الاستخدام ✓", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// مكونات التبويبات الإرشادية (Sub-Tabs)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AiIntegrationGuideTab(
    context: Context,
    onOpenSettings: () -> Unit
) {
    GuideSectionCard(
        title = "🛰️ كيف ترتبط بي وبنماذج الذكاء الاصطناعي المتعددة؟",
        badge = "MULTI_AI_CORE"
    ) {
        Text(
            "تم ربط هذا القسم بمحرك ذكاء اصطناعي متعدد المزودات (`AiModelRegistry` و `GatewayAiService`) يدعم نماذج OpenRouter, DeepSeek V3/R1, Groq, OpenAI GPT-4o, Google Gemini, والنقاط المخصصة (Custom OpenAI Endpoints).",
            color = Color(0xFFE2E8F0),
            fontFamily = CairoFont,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        StepGuideItem(
            stepNumber = "١",
            title = "اختيار أو تسجيل نموذجك المفضل (OpenRouter / DeepSeek / Groq / ...)",
            desc = "من إعدادات القسم، يمكنك التبديل بين النماذج المسجلة بنقرة واحدة، أو تسجيل أي نموذج مخصص عبر إدخال الـ Model ID ومفتاح الـ API الخاص بك."
        )

        StepGuideItem(
            stepNumber = "٢",
            title = "فحص الاستجابة وزمن التأخير (Ping Latency Test)",
            desc = "يتيح لك النظام اختبار اتصال النموذج المختار حياً لقياس زمن الاستجابة بالميلي ثانية (ms) والتأكد من جاهزية المحرك."
        )

        StepGuideItem(
            stepNumber = "٣",
            title = "التشخيص البرمجي واقتراح الحلول مع صمام الأمان",
            desc = "عند الضغط على [🤖 إصلاح وتفويض GitHub]، يحلل النموذج المختار المشكلة ويقترح كود التصحيح، ولا يتم تطبيقه إلا بموافقتك الصريحة."
        )
    }

    GuideCodeSnippetCard(
        title = "📋 نموذج إعدادات النموذج المسجل (AiModelConfig JSON)",
        code = "{\n  \"name\": \"DeepSeek R1 / OpenRouter\",\n  \"provider\": \"OPENROUTER\",\n  \"modelIdentifier\": \"deepseek/deepseek-r1\",\n  \"apiKey\": \"sk-or-v1-...\",\n  \"temperature\": 0.7\n}"
    )
}

@Composable
private fun GitHubIntegrationGuideTab(
    context: Context,
    onOpenSettings: () -> Unit
) {
    GuideSectionCard(
        title = "🐙 كيف تربط مستودع GitHub وتنسخ وتفرّع الشفرة؟",
        badge = "REPO_VAULT_&_CLONE"
    ) {
        Text(
            "خزانة المستودعات `RepoVaultManager` تتيح للتطبيق التواصل مع مستودع GitHub الخاص بك بأمان كامل لقراءة الملفات، تفريع المستودعات (Fork)، استنساخ الشفرة (Clone/Duplicate)، وتجهيز طلبات السحب (Pull Requests).",
            color = Color(0xFFE2E8F0),
            fontFamily = CairoFont,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        StepGuideItem(
            stepNumber = "١",
            title = "استخراج رمز الوصول الشخصي (GitHub PAT)",
            desc = "توجه إلى GitHub -> Settings -> Developer Settings -> Personal access tokens وأنشئ رمزاً بصلاحيات `repo` و `workflow`."
        )

        StepGuideItem(
            stepNumber = "٢",
            title = "حفظ المستودع في خزانة التطبيق",
            desc = "افتح إعدادات القسم وأدخل اسم المالك واسم المستودع (مثال: `owner/qabas-app`) والصق الرمز، ثم اضغط على [اختبار وحفظ ⚡]."
        )

        StepGuideItem(
            stepNumber = "٣",
            title = "تفريغ وعمل Fork أو استنساخ المستودع بالكامل",
            desc = "من [مختبر البناء 🧪 -> نسخ وتفريغ المستودع] يمكنك تفريع المستودع إلى حسابك بضغطة زر واحدة أو استنساخ كافة الملفات في مستودع مستقل جديد."
        )
    }
}

@Composable
private fun ConsentGateGuideTab() {
    GuideSectionCard(
        title = "🛡️ بوابة التفويض والموافقة الصريحة (Consent Gate)",
        badge = "STRICT_SECURITY"
    ) {
        Surface(
            color = Color(0xFF10B981).copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "مبدأ الأمان المطلق: لا يتم تنفيذ أي كود أو تعديل مستودع دون موافقتك الصريحة واليدوية!",
                    color = Color(0xFF6EE7B7),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        StepGuideItem(
            stepNumber = "١",
            title = "معاينة الفارق البرمجي (Diff Preview)",
            desc = "تعرض لك شاشة الموافقة مسار الملف المستهدف، والتعديل المقترح سطراً بسطر في خط برمجي مونو سبيس عالي الوضوح."
        )

        StepGuideItem(
            stepNumber = "٢",
            title = "خيارات التحكم الكامل للمطور",
            desc = "يمكنك اختيار [موافقة وتفويض التنفيذ 🚀] لتطبيق التعديل على GitHub، أو [نسخ الحل للكليب بورد 📋]، أو [رفض الإجراء 🛑] لإلغائه فوراً."
        )

        StepGuideItem(
            stepNumber = "٣",
            title = "التوثيق الصادق والشفاف",
            desc = "يتم توثيق كل خطوة معتمدة بنجاح داخل سجل المحادثة مع رقم الـ Commit أو الـ PR المحدث."
        )
    }
}

@Composable
private fun DevToolsAndUsageGuideTab(context: Context) {
    GuideSectionCard(
        title = "⚡ أدوات ووسوم قسم التطوير والمحادثة",
        badge = "HUD_TOOLS"
    ) {
        StepGuideItem(
            stepNumber = "🚨",
            title = "وسم الطوارئ (عاجل)",
            desc = "لتحديد المشاكل الحرجة التي تحتاج تدخلاً فورياً مع إعطائها أولوية في التحليل والاستجابة."
        )

        StepGuideItem(
            stepNumber = "🛰️",
            title = "فحص التيليمتري المباشر",
            desc = "زر فحص التيليمتري يجمع حالة الذاكرة، نظام التشغيل، وإصدار التطبيق لإرفاقه بالمحادثة بضغطة زر."
        )

        StepGuideItem(
            stepNumber = "📜",
            title = "الضوابط الإسلامية والأخلاقية الصارمة",
            desc = "حظر الردود التلقائية المضللة، واستبدالها بالدعاء والشكر للمستخدم الصادق الذي يساهم في تحسين التطبيق."
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// المكونات المساعدة للبطاقات
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun GuideSectionCard(
    title: String,
    badge: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.85f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = Color(0xFF06B6D4).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.6.dp, Color(0xFF06B6D4).copy(alpha = 0.5f))
                ) {
                    Text(
                        badge,
                        color = Color(0xFF06B6D4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
            content()
        }
    }
}

@Composable
private fun StepGuideItem(
    stepNumber: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF06B6D4).copy(alpha = 0.2f))
                .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stepNumber,
                color = Color(0xFF22D3EE),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                desc,
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun GuideCodeSnippetCard(
    title: String,
    code: String
) {
    val context = LocalContext.current
    Surface(
        color = Color(0xFF070B14),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("Config Snippet", code))
                        Toast.makeText(context, "تم نسخ النموذج بنجاح ✓", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = GoldPrimary, modifier = Modifier.size(15.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                code,
                color = Color(0xFF6EE7B7),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}
