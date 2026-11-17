package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EnterpriseSponsorshipTier(
    val title: String,
    val capacity: String,
    val monthlyCostUsd: Int,
    val impactDescription: String,
    val badgeTitle: String
)

@Composable
fun EnterprisePortalScreen(
    onBack: () -> Unit,
    onNavigate: (AppState) -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = remember { context.getSharedPreferences("qabas_enterprise_prefs", Context.MODE_PRIVATE) }

    var activeTab by remember { mutableIntStateOf(0) } // 0: White-Label, 1: Sponsorships, 2: Quotes, 3: Retainers

    // 1. White Label Custom App Builder State
    var orgName by remember { mutableStateOf("") }
    var orgEmail by remember { mutableStateOf("") }
    var customAppName by remember { mutableStateOf("") }
    var isPrivateServerRequired by remember { mutableStateOf(false) }
    var customRequirements by remember { mutableStateOf("") }

    // 2. Sponsorship State
    val sponsorshipTiers = listOf(
        EnterpriseSponsorshipTier("رعاية المبادرة الفضية 🥈", "تغطية 50 صانع محتوى", 300, "إنتاج ~7,500 فيديو وبطاقة حديث شهرياً", "برعاية فضية 🌟"),
        EnterpriseSponsorshipTier("رعاية المؤسسة الذهبية 🏆", "تغطية 200 صانع محتوى", 1000, "إنتاج ~30,000 فيديو وبطاقة حديث شهرياً", "برعاية ذهبية 👑"),
        EnterpriseSponsorshipTier("رعاية المأثرة الماسية 💎", "تغطية 1,000 صانع محتوى", 4500, "إنتاج ~150,000 فيديو وبطاقة حديث شهرياً", "برعاية ماسية 💎")
    )
    var selectedSponsorshipTier by remember { mutableStateOf(sponsorshipTiers[1]) }

    // 3. Invoice / Quote State
    var invoiceClientName by remember { mutableStateOf("") }
    var invoiceServiceType by remember { mutableStateOf("تطبيق مخصص White-Label + سيرفر خاص") }
    var invoiceAmountUsd by remember { mutableStateOf("1500") }
    var generatedQuoteText by remember { mutableStateOf("") }

    // 4. Saved Retainers (Real persisted records)
    fun loadSavedRetainers(): List<ClientRetainer> {
        val jsonStr = prefs.getString("saved_retainers_json", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ClientRetainer>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ClientRetainer(
                        clientName = obj.optString("clientName", ""),
                        serviceType = obj.optString("serviceType", ""),
                        monthlyAmount = obj.optDouble("monthlyAmount", 0.0),
                        paymentStatus = obj.optString("paymentStatus", "معلق 🟡"),
                        deliveryProgress = obj.optDouble("deliveryProgress", 0.0).toFloat()
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveRetainers(retainers: List<ClientRetainer>) {
        val array = JSONArray()
        retainers.forEach { r ->
            val obj = JSONObject().apply {
                put("clientName", r.clientName)
                put("serviceType", r.serviceType)
                put("monthlyAmount", r.monthlyAmount)
                put("paymentStatus", r.paymentStatus)
                put("deliveryProgress", r.deliveryProgress.toDouble())
            }
            array.put(obj)
        }
        prefs.edit().putString("saved_retainers_json", array.toString()).apply()
    }

    var activeRetainers by remember { mutableStateOf(loadSavedRetainers()) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = "بوابة المؤسسات B2B",
                subtitle = "حلول الجمعيات الخيرية والرعايات المؤسسية",
                onBack = onBack
            )
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Banner
            AppleCard(
                cornerRadius = 18.dp,
                containerColor = theme.cardSurface
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "لوحة حلول الجمعيات والدعم المؤسسي 🤝",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "تمكين المؤسسات والجمعيات من بناء تطبيقات دعوية مخصصة باسمهم أو رعاية صناع المحتوى الدعوي.",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tab Selector
                    AppleSegmentedControl(
                        items = listOf(
                            0 to "تطبيق مخصص",
                            1 to "رعاية الدعاة",
                            2 to "عرض سعر",
                            3 to "السجل"
                        ),
                        selectedItem = activeTab,
                        onSelect = { activeTab = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // TAB 0: White-Label Custom App Request Builder
            if (activeTab == 0) {
                AppleCard(
                    cornerRadius = 16.dp,
                    containerColor = theme.cardSurface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📲 طلب بناء تطبيق مخصص باسم المؤسسة (White-Label):",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Text(
                            text = "بناء نسخة أندرويد مستقلة بالكامل تحمل اسم وشعار مؤسستكم مع ربط محتواكم الخاص ومكتبتكم الصوتية.",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = orgName,
                            onValueChange = { orgName = it },
                            label = { Text("اسم الجمعية / المؤسسة", fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = customAppName,
                            onValueChange = { customAppName = it },
                            label = { Text("الاسم المقترح للتطبيق (مثلاً: تطبيق هداية)", fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = orgEmail,
                            onValueChange = { orgEmail = it },
                            label = { Text("البريد الإلكتروني الرسمي للتواصل", fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = customRequirements,
                            onValueChange = { customRequirements = it },
                            label = { Text("متطلبات إضافية (أقسام مخصصة، مكتبة تلاوات، إلخ)", fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isPrivateServerRequired,
                                onCheckedChange = { isPrivateServerRequired = it }
                            )
                            Text(
                                text = "ربط سيرفر ذكاء اصطناعي وخادم خاص بالجمعية 🔒",
                                color = theme.textPrimary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                        }

                        ApplePrimaryButton(
                            text = "تقديم طلب التشييد الهندسي للمؤسسة 🚀",
                            icon = Icons.Default.Send,
                            onClick = {
                                if (orgName.isBlank() || orgEmail.isBlank()) {
                                    Toast.makeText(context, "يرجى تعبئة اسم المؤسسة والبريد الإلكتروني", Toast.LENGTH_SHORT).show()
                                } else {
                                    val newRetainer = ClientRetainer(
                                        clientName = orgName,
                                        serviceType = "تطبيق مخصص: " + customAppName.ifBlank { "White-Label" },
                                        monthlyAmount = if (isPrivateServerRequired) 2500.0 else 1500.0,
                                        paymentStatus = "قيد المراجعة ⏳",
                                        deliveryProgress = 0.15f
                                    )
                                    val updated = listOf(newRetainer) + activeRetainers
                                    activeRetainers = updated
                                    saveRetainers(updated)

                                    val summary = """
                                        طلب تطبيق مخصص White-Label:
                                        المؤسسة: $orgName
                                        التطبيق المقترح: $customAppName
                                        البريد: $orgEmail
                                        سيرفر خاص: ${if (isPrivateServerRequired) "نعم" else "لا"}
                                        المتطلبات: $customRequirements
                                    """.trimIndent()

                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("B2B Request", summary))

                                    // Open Email chooser if supported
                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:support@qabas.app")
                                        putExtra(Intent.EXTRA_SUBJECT, "طلب تشييد تطبيق مخصص - $orgName")
                                        putExtra(Intent.EXTRA_TEXT, summary)
                                    }
                                    runCatching { context.startActivity(emailIntent) }

                                    Toast.makeText(context, "تم حفظ الطلب ونقله إلى السجل بنجاح! 🚀", Toast.LENGTH_LONG).show()
                                    orgName = ""
                                    orgEmail = ""
                                    customAppName = ""
                                    customRequirements = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // TAB 1: Sponsorship Tiers
            if (activeTab == 1) {
                AppleCard(
                    cornerRadius = 16.dp,
                    containerColor = theme.cardSurface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🎁 باقات رعاية الحسابات والمبادرات الدعوية:",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Text(
                            text = "بإمكان الجمعيات كفالة تكاليف خوادم الذكاء الاصطناعي لفتح الحدود المجانية أمام مئات الدعاة مع إبراز شعار الجمعية كـ (راعي رسمي).",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        sponsorshipTiers.forEach { tier ->
                            val isSelected = selectedSponsorshipTier == tier
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSponsorshipTier = tier },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.secondarySurface,
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 0.5.dp,
                                    if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tier.title,
                                            color = if (isSelected) theme.primary else theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "$${tier.monthlyCostUsd} / شهرياً",
                                            color = theme.primary,
                                            fontFamily = NotoSansFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tier.capacity,
                                        color = theme.textPrimary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = tier.impactDescription,
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }

                        ApplePrimaryButton(
                            text = "نسخ عقد الرعاية الرسمي للمؤسسة 📋",
                            icon = Icons.Default.ContentCopy,
                            onClick = {
                                val quote = "عرض رعاية مؤسسية رسمية - ${selectedSponsorshipTier.title}\nالتكلفة: $${selectedSponsorshipTier.monthlyCostUsd}/شهرياً\nالأثر: ${selectedSponsorshipTier.impactDescription}\nالشارة: ${selectedSponsorshipTier.badgeTitle}\nمنصة قبس للدعوة الرقمية QABAS B2B"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Sponsorship Quote", quote)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "تم نسخ تفاصيل باقة الرعاية إلى الحافظة! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // TAB 2: Official B2B Quote & Invoice Generator
            if (activeTab == 2) {
                AppleCard(
                    cornerRadius = 16.dp,
                    containerColor = theme.cardSurface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🧾 مولد عروض الأسعار والفواتير الرسمية للمؤسسات:",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        OutlinedTextField(
                            value = invoiceClientName,
                            onValueChange = { invoiceClientName = it },
                            label = { Text("اسم الجهة / الجمعية المستفيدة", fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = invoiceServiceType,
                            onValueChange = { invoiceServiceType = it },
                            label = { Text("نوع الخدمة / البند", fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = invoiceAmountUsd,
                            onValueChange = { invoiceAmountUsd = it },
                            label = { Text("المبلغ الإجمالي (USD)", fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        ApplePrimaryButton(
                            text = "توليد الفاتورة الرسمية الموثقة 🧾✨",
                            icon = Icons.Default.ReceiptLong,
                            onClick = {
                                if (invoiceClientName.isBlank()) {
                                    Toast.makeText(context, "أدخل اسم الجهة أولاً", Toast.LENGTH_SHORT).show()
                                } else {
                                    val refNo = "QBS-INV-${(10000..99999).random()}"
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    generatedQuoteText = """
                                        ========================================
                                        📜 عرض سعر رسمي - منصة قبس B2B ENTERPRISE
                                        رقم المرجع: $refNo
                                        التاريخ: $dateStr
                                        ========================================
                                        المُستفيد: $invoiceClientName
                                        بند الخدمة: $invoiceServiceType
                                        المبلغ الإجمالي: $${invoiceAmountUsd} دولار أمريكي
                                        
                                        الميزات المضمنة:
                                        • تطبيق أندرويد مخصص White-Label
                                        • استوديو كامل لصناعة المحتوى الإسلامي
                                        • دعم علمي وتقني متواصل
                                        ========================================
                                        معتمد رسمياً من فريق هندسة QABAS STUDIO
                                    """.trimIndent()

                                    // Also save to retainers history
                                    val newRetainer = ClientRetainer(
                                        clientName = invoiceClientName,
                                        serviceType = invoiceServiceType,
                                        monthlyAmount = invoiceAmountUsd.toDoubleOrNull() ?: 1500.0,
                                        paymentStatus = "عرض سعر صادر 📜",
                                        deliveryProgress = 0.05f
                                    )
                                    val updated = listOf(newRetainer) + activeRetainers
                                    activeRetainers = updated
                                    saveRetainers(updated)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (generatedQuoteText.isNotBlank()) {
                            Surface(
                                color = theme.secondarySurface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = generatedQuoteText,
                                        color = theme.textPrimary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.5.sp,
                                        lineHeight = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AppleSecondaryButton(
                                            text = "نسخ الفاتورة 📋",
                                            icon = Icons.Default.ContentCopy,
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                clipboard?.setPrimaryClip(ClipData.newPlainText("Invoice", generatedQuoteText))
                                                Toast.makeText(context, "تم نسخ الفاتورة الرسمية! 📋", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 3: Retainers & Active Sponsorships
            if (activeTab == 3) {
                AppleCard(
                    cornerRadius = 16.dp,
                    containerColor = theme.cardSurface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📂 سجل الطلبات والمؤسسات:",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        if (activeRetainers.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = theme.textSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(42.dp)
                                )
                                Text(
                                    text = "لا توجد طلبات أو عقود رعاية مسجلة حالياً.",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "يمكنك تقديم طلب تطبيق مخصص أو توليد عرض سعر رسمي من التبويبات أعلاه وسيتم حفظه هنا تلقائياً.",
                                    color = theme.textSecondary.copy(alpha = 0.8f),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            activeRetainers.forEach { client ->
                                Surface(
                                    color = theme.secondarySurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = client.clientName,
                                                color = theme.textPrimary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )
                                            Text(
                                                text = client.paymentStatus,
                                                color = theme.primary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "نوع الخدمة: ${client.serviceType}",
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { client.deliveryProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = theme.primary,
                                            trackColor = theme.separator.copy(alpha = 0.3f)
                                        )
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
