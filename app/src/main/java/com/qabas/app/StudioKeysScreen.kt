package com.qabas.app

import android.content.Context
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * شاشة «مفاتيحي» الموحدة (BYOK) — مدخل واحد لكل مزودي الاستوديو.
 *
 * القواعد الأمنية المطبقة هنا (من الدفعة 1 وتقرير الاستوديو):
 * - المفتاح لا يُعرض أبداً إلا مقنّعاً عبر [AgentPrefs.maskSecret] (•••• + آخر 4).
 * - لا يُحفظ مفتاح إلا بعد فحص حي ناجح: عبر [UniversalKeyBrain.saveKeyIfLive]
 *   للمزودين المكتشوفين، أو عبر [ApiKeyValidator.validateKey] للبقية.
 * - التخزين محلي مشفّر (مسبح [KeyPoolManager] في EncryptedSharedPreferences)
 *   + سحابي عبر دالة `qabas-key-vault` فقط (تشفير AES-GCM من جهة الخادم).
 *
 * حدود صريحة موثقة:
 * - Leonardo ليس في قائمة الخزنة المسموحة
 *   (4 خدمات في `supabase/functions/qabas-key-vault/index.ts`) — مفتاحه
 *   محلي فقط حتى يُوسَّع Edge Function والعميل معاً.
 * - روابط «احصل على المفتاح» مذكورة فقط حيث وردت في كود التطبيق أصلاً.
 * - كل تلميحات التكلفة من بحث الويب في تقرير الاستوديو وموسومة «تقريبي».
 */
private data class ProviderSpec(
    val id: String,
    val displayName: String,
    val capability: String,
    val icon: ImageVector,
    val prefsKey: String,
    /** اسم الخدمة في الخزنة السحابية، أو null إن كانت محلية فقط. */
    val vaultService: String?,
    /** رابط لوحة المزود للحصول على مفتاح، أو null إن لم يرد موثقاً في الكود. */
    val keyUrl: String?,
    /** تلميح تكلفة — كل الأسعار هنا «تقريبي» من بحث الويب في تقرير الاستوديو. */
    val costHint: String,
    val detectedProvider: DetectedProvider?,
    /** يُدار عبر بوابة المنصة (qabas-gateway) لا بمفتاح مستخدم. */
    val gatewayManaged: Boolean = false,
    val localOnlyReason: String? = null
)

private val PROVIDER_SPECS = listOf(
    ProviderSpec(
        id = "gemini", displayName = "Gemini", capability = "نص وسكربت ووكيل",
        icon = Icons.Default.TextFields, prefsKey = "gemini_key", vaultService = "gemini_key",
        keyUrl = "https://aistudio.google.com/app/apikey",
        costHint = "مجاني بحدود الاستخدام العادل — تقريبي",
        detectedProvider = DetectedProvider.GEMINI
    ),
    ProviderSpec(
        id = "groq", displayName = "Groq", capability = "نص وسكربت ووكيل",
        icon = Icons.Default.TextFields, prefsKey = "groq_key", vaultService = "groq_key",
        keyUrl = "https://console.groq.com/keys",
        costHint = "الأسرع والأرخص للسكربت — تقريبي",
        detectedProvider = DetectedProvider.GROQ
    ),
    ProviderSpec(
        id = "openrouter", displayName = "OpenRouter", capability = "نص وسكربت ووكيل",
        icon = Icons.Default.TextFields, prefsKey = "openrouter_key", vaultService = "openrouter_key",
        keyUrl = "https://openrouter.ai/keys",
        costHint = "بوابة نماذج كثيرة، منها المجاني — تقريبي",
        detectedProvider = DetectedProvider.OPENROUTER
    ),
    ProviderSpec(
        id = "leonardo", displayName = "Leonardo", capability = "صور",
        icon = Icons.Default.Image, prefsKey = "leonardo_api_key", vaultService = null,
        keyUrl = "https://app.leonardo.ai/api-access",
        costHint = "مجاني 150 رمزاً يومياً — تقريبي",
        detectedProvider = null,
        localOnlyReason = "ليس في قائمة الخزنة المسموحة (4 خدمات) — محلي فقط حتى تُوسَّع الخزنة"
    ),
    ProviderSpec(
        id = "pixabay", displayName = "Pixabay", capability = "وسائط جاهزة",
        icon = Icons.Default.PermMedia, prefsKey = "pixabay_key", vaultService = "pixabay_key",
        keyUrl = "https://pixabay.com/api/docs/",
        costHint = "مجاني بمفتاح بسيط — تقريبي",
        detectedProvider = null
    ),
    ProviderSpec(
        id = "higgsfield", displayName = "Higgsfield", capability = "فيديو",
        icon = Icons.Default.Movie, prefsKey = "", vaultService = null,
        keyUrl = null,
        costHint = "على حساب المنصة وبحصص محدودة — بلا مفتاح شخصي",
        detectedProvider = null,
        gatewayManaged = true
    )
)

private data class FreeAlternative(
    val displayName: String,
    val capability: String,
    val note: String
)

private val FREE_ALTERNATIVES = listOf(
    FreeAlternative("Pollinations / FLUX", "صور", "توليد صور مجاني بلا مفتاح — يعمل الآن"),
    FreeAlternative("Edge TTS", "صوت", "تعليق صوتي عصبي مجاني بلا مفتاح — يعمل الآن"),
    FreeAlternative("Android TTS", "صوت", "محرك النظام المدمج — يعمل الآن بلا إنترنت")
)

/** تبويبات القسم الموحد «مفاتيحي ونماذجي» (المرحلة 5): المفاتيح والنماذج
 * والمتقدم (مسابح احتياطية، لصق ذكي، نسخ احتياطي، سجل طلبات — نُقلت من
 * ApiKeysScreen). */
private enum class KeysModelsTab(val title: String) {
    KEYS("المفاتيح"),
    MODELS("النماذج"),
    ADVANCED("متقدم")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioKeysScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableIntStateOf(0) }
    var syncMessage by remember { mutableStateOf("") }
    var syncBusy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مفاتيحي ونماذجي") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            var selectedTab by remember { mutableStateOf(KeysModelsTab.KEYS) }
            AppleSegmentedControl(
                items = KeysModelsTab.entries.map { it to it.title },
                selectedItem = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when (selectedTab) {
                KeysModelsTab.KEYS -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    item {
                        Text(
                            "مفتاح واحد لكل مزود — يُفحص حياً قبل الحفظ، ويُعرض مقنّعاً دائماً. " +
                                "بدون أي مفتاح يعمل الاستوديو بالبدائل المجانية أدناه.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    item {
                        SyncCard(
                            busy = syncBusy,
                            message = syncMessage,
                            onUpload = {
                                syncBusy = true
                                KeySyncService.pushToCloud(context) { ok, msg ->
                                    syncBusy = false
                                    syncMessage = msg
                                    if (ok) refreshTick++
                                }
                            },
                            onDownload = {
                                syncBusy = true
                                KeySyncService.pullFromCloud(context) { ok, msg ->
                                    syncBusy = false
                                    syncMessage = msg
                                    if (ok) refreshTick++
                                }
                            }
                        )
                    }

                    val grouped = PROVIDER_SPECS.groupBy { it.capability }
                    grouped.forEach { (capability, specs) ->
                        item {
                            Text(capability, style = MaterialTheme.typography.titleMedium)
                        }
                        items(specs, key = { it.id }) { spec ->
                            ProviderKeyCard(
                                spec = spec,
                                refreshTick = refreshTick,
                                onChanged = { refreshTick++ }
                            )
                        }
                    }

                    item {
                        Text("بدائل مجانية بلا مفتاح", style = MaterialTheme.typography.titleMedium)
                    }
                    items(FREE_ALTERNATIVES) { alt ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(alt.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(alt.capability, style = MaterialTheme.typography.bodySmall)
                                Text(alt.note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
                KeysModelsTab.MODELS -> ModelsTabContent(
                    refreshTick = refreshTick,
                    onChanged = { refreshTick++ }
                )
                KeysModelsTab.ADVANCED -> AdvancedKeysTab(
                    refreshTick = refreshTick,
                    onChanged = { refreshTick++ }
                )
            }
        }
    }
}

@Composable
private fun SyncCard(
    busy: Boolean,
    message: String,
    onUpload: () -> Unit,
    onDownload: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("مزامنة سحابية مشفّرة", style = MaterialTheme.typography.titleSmall)
            Text(
                "الرفع والتنزيل عبر دالة qabas-key-vault فقط (AES-GCM من جهة الخادم). " +
                    "المفاتيح المحلية فقط (Leonardo) لا تُزامَن.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUpload, enabled = !busy) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Text("رفع إلى السحابة")
                }
                OutlinedButton(onClick = onDownload, enabled = !busy) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Text("تنزيل من السحابة")
                }
                if (busy) CircularProgressIndicator()
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ProviderKeyCard(
    spec: ProviderSpec,
    refreshTick: Int,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    // (المرحلة 5) تأكيد قبل حذف مفتاح متصل.
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // (المرحلة 7) خطوات الحصول على المفتاح — من دليل المنصات (كانت في تبويب
    // الروابط المحذوف): للموفّرين الذين لهم دليل خطوات فقط.
    var showKeySteps by remember { mutableStateOf(false) }
    val keyGuideSteps: List<String> = remember(spec.detectedProvider) {
        spec.detectedProvider?.let { provider ->
            CodingPlatformDirectory.platforms.firstOrNull { it.provider == provider }?.steps
        }.orEmpty()
    }

    val activeKey = remember(refreshTick) {
        if (spec.prefsKey.isBlank()) "" else KeyPoolManager.getActiveKey(spec.prefsKey)
    }
    var gatewayConfigured by remember { mutableStateOf<Boolean?>(null) }
    if (spec.gatewayManaged) {
        LaunchedEffect(refreshTick) {
            gatewayConfigured = runCatching {
                HiggsfieldVideoService.isConfigured(context)
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
        }
    }
    val connected = if (spec.gatewayManaged) gatewayConfigured == true else activeKey.isNotBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(spec.icon, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (connected) "متصل" else "غير مضبوط",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (spec.gatewayManaged) {
                Text(
                    "يُدار عبر بوابة المنصة (qabas-gateway) — لا حاجة لمفتاح شخصي. " +
                        "التوليد به على حساب المنصة وبحصص محدودة.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                val (ok, detail) = HiggsfieldVideoService.testConnection(context)
                                message = detail
                                busy = false
                            }
                        },
                        enabled = !busy
                    ) { Text("اختبر") }
                    if (busy) CircularProgressIndicator()
                }
            } else {
                if (activeKey.isNotBlank()) {
                    Text(
                        "المفتاح الحالي: ${AgentPrefs.maskSecret(activeKey)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("الصق المفتاح الجديد") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val key = KeyPoolManager.sanitizeApiKey(input)
                            if (key.isBlank()) {
                                message = "الصق مفتاحاً أولاً."
                                return@Button
                            }
                            scope.launch {
                                busy = true
                                // ق54: النجاح علم لا نص — كان المنادي يشمّ
                                // بادئة «تم الحفظ» من الرسالة فينكسر مسح
                                // الحقل والتحديث مع أي إعادة صياغة للنص.
                                val (saved, msg) = saveAndTestKey(context, spec, key)
                                message = msg
                                if (saved) {
                                    input = ""
                                    onChanged()
                                }
                                busy = false
                            }
                        },
                        enabled = !busy
                    ) { Text(if (activeKey.isNotBlank()) "تحديث" else "حفظ واختبار") }
                    OutlinedButton(
                        onClick = {
                            val key = KeyPoolManager.sanitizeApiKey(input.ifBlank { activeKey })
                            if (key.isBlank()) {
                                message = "لا يوجد مفتاح لاختباره."
                                return@OutlinedButton
                            }
                            scope.launch {
                                busy = true
                                val result = ApiKeyValidator.validateKey(
                                    context, spec.id, key
                                )
                                message = result.summary
                                busy = false
                            }
                        },
                        enabled = !busy
                    ) { Text("اختبر") }
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !busy && activeKey.isNotBlank()
                    ) { Text("حذف") }
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("حذف مفتاح ${spec.displayName}؟") },
                            text = {
                                Text(
                                    "سيُحذف المفتاح ${AgentPrefs.maskSecret(activeKey)} نهائياً من الجهاز" +
                                        " ومن الخزنة السحابية إن وُجدت نسخة. لا يمكن التراجع."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirm = false
                                        scope.launch {
                                            busy = true
                                            KeyPoolManager.clearAll(spec.prefsKey)
                                            var cloudNote = ""
                                            if (spec.vaultService != null) {
                                                val deleted = runCatching {
                                                    SupabaseRepository.getInstance().deleteApiKey(spec.id)
                                                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                                                cloudNote = if (deleted) " وحُذف من السحابة."
                                                else " (تعذّر حذف نسخة السحابة — ستعود عند التنزيل من السحابة؛ أعد المحاولة)."
                                            } else {
                                                cloudNote = " (محلي فقط أصلاً)."
                                            }
                                            // (المرحلة 5) بعد الحذف لا يبقى الوضع على مفتاح
                                            // محذوف: يُحسب المحرك النشط الجديد فوراً ويُبلَّغ به.
                                            val (fallbackProvider, _, _) =
                                                AgentConnectivityEngine.resolveKeyWithSource(context)
                                            val fallbackNote = if (fallbackProvider == DetectedProvider.NONE) {
                                                " لا يوجد مفتاح صالح — سيعمل التطبيق بالبدائل المجانية."
                                            } else {
                                                " المحرك النشط الآن: ${fallbackProvider.displayName}."
                                            }
                                            message = "تم حذف مفتاح ${spec.displayName}.$cloudNote$fallbackNote"
                                            onChanged()
                                            busy = false
                                        }
                                    }
                                ) { Text("حذف نهائي", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") }
                            }
                        )
                    }
                    if (busy) CircularProgressIndicator()
                }
                if (spec.keyUrl != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { openUrl(context, spec.keyUrl) }) {
                            Text("احصل على المفتاح")
                        }
                        TextButton(onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("رابط الحصول على المفتاح", spec.keyUrl)
                            )
                            Toast.makeText(context, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("نسخ الرابط")
                        }
                    }
                }
                // (المرحلة 7) خطوات الحصول على المفتاح — نُقلت من تبويب الروابط
                // المحذوف؛ مطوية افتراضياً حتى لا تُطيل البطاقة.
                if (keyGuideSteps.isNotEmpty()) {
                    TextButton(onClick = { showKeySteps = !showKeySteps }) {
                        Text(if (showKeySteps) "إخفاء خطوات الحصول على المفتاح" else "خطوات الحصول على المفتاح")
                    }
                    if (showKeySteps) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            keyGuideSteps.forEachIndexed { idx, stepText ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(14.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${idx + 1}",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stepText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            spec.localOnlyReason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text("التكلفة: ${spec.costHint}", style = MaterialTheme.typography.bodySmall)
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/**
 * حفظ بعد فحص حي ناجح فقط. للمزودين المكتشوفين عبر [UniversalKeyBrain.saveKeyIfLive]،
 * وللبقية عبر [ApiKeyValidator.validateKey] ثم حفظ محلي مشفّر + خزنة سحابية إن سُمح.
 * لا يُطبع المفتاح أبداً في أي رسالة أو سجل.
 */
private suspend fun saveAndTestKey(
    context: Context,
    spec: ProviderSpec,
    key: String
): Pair<Boolean, String> {
    val detected = spec.detectedProvider
    if (detected != null) {
        return when (val res = UniversalKeyBrain.saveKeyIfLive(context, key, detected)) {
            is UniversalKeyBrain.SmartSaveResult.Saved -> {
                var note = ""
                if (spec.vaultService != null) {
                    val pushed = runCatching {
                        SupabaseRepository.getInstance().registerApiKey(spec.id, key)
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                    note = if (pushed) " وزُامن إلى الخزنة السحابية."
                    else " (تعذّرت المزامنة السحابية — محفوظ محلياً فقط)."
                } else {
                    note = " (محلي فقط — ليس في قائمة الخزنة المسموحة)."
                }
                true to "تم الحفظ — متصل (${res.latencyMs}ms).$note"
            }
            is UniversalKeyBrain.SmartSaveResult.Failed ->
                false to "فشل الفحص الحي: ${res.reason} — لم يُحفظ المفتاح."
            is UniversalKeyBrain.SmartSaveResult.Unsupported ->
                false to "تعذّر الحفظ: ${res.reason}"
        }
    }
    val result = ApiKeyValidator.validateKey(
        context, spec.id, key
    )
    if (!result.isValid) return false to "فشل الفحص الحي: ${result.summary} — لم يُحفظ المفتاح."
    KeyPoolManager.setSingleKey(spec.prefsKey, key)
    var note = ""
    if (spec.vaultService != null) {
        val pushed = runCatching {
            SupabaseRepository.getInstance().registerApiKey(spec.id, key)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
        note = if (pushed) " وزُامن إلى الخزنة السحابية."
        else " (تعذّرت المزامنة السحابية — محفوظ محلياً فقط)."
    } else {
        note = " (محلي فقط — ليس في قائمة الخزنة المسموحة)."
    }
    return true to "تم الحفظ — متصل.$note"
}

/**
 * تبويب «النماذج» في القسم الموحد (المرحلة 5): نماذج سحابية مشتقة من المفاتيح
 * + النماذج المحلية على الجهاز — يُعاد استخدام [OnDeviceModelsContent] كما هو،
 * ففحص قدرة الجهاز حياً وبوابة التحميل والاستيراد والحذف كلها فيه.
 */
private enum class ModelsSubTab(val title: String) {
    CLOUD("سحابية"),
    LOCAL("محلية")
}

@Composable
private fun ModelsTabContent(
    refreshTick: Int,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableStateOf(ModelsSubTab.CLOUD) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppleSegmentedControl(
            items = ModelsSubTab.entries.map { it to it.title },
            selectedItem = subTab,
            onSelect = { subTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when (subTab) {
            ModelsSubTab.CLOUD -> CloudModelsSection(
                refreshTick = refreshTick,
                onChanged = onChanged,
                modifier = Modifier.weight(1f)
            )
            ModelsSubTab.LOCAL -> OnDeviceModelsContent(
                context = context,
                showCloseButton = false,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

/**
 * النماذج السحابية — مشتقة من المفاتيح المخزنة نفسها (مخزن واحد:
 * [KeyPoolManager] في `qabas_agent_encrypted`)، وقوائم النماذج من
 * [DetectedProvider.models] لا من قائمة جديدة. التفعيل يكتب
 * [AgentPrefs.putModel] — المصدر الوحيد الذي تقرأ منه المحادثة نموذجها.
 */
@Composable
private fun CloudModelsSection(
    refreshTick: Int,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeModel = remember(refreshTick) { AgentPrefs.model(context) }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "نماذج سحابية من مفاتيحك: فعّل نموذجاً ليصبح النشط في المحادثة. " +
                    "المزود بلا مفتاح تظهر نماذجه معطلة مع السبب.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (activeModel != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("النموذج النشط الآن", style = MaterialTheme.typography.titleSmall)
                        Text(
                            readableModelName(activeModel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        PROVIDER_SPECS.filter { it.detectedProvider != null }.forEach { spec ->
            val provider = spec.detectedProvider!!
            item {
                Text(spec.displayName, style = MaterialTheme.typography.titleMedium)
            }
            items(provider.models, key = { it }) { modelId ->
                val hasKey = remember(refreshTick) {
                    spec.prefsKey.isNotBlank() &&
                        KeyPoolManager.getActiveKey(spec.prefsKey).isNotBlank()
                }
                val isActive = activeModel == modelId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(readableModelName(modelId), style = MaterialTheme.typography.titleSmall)
                                if (isActive) {
                                    Text(
                                        "✓ نشط",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (!hasKey) {
                                    Text(
                                        "يحتاج مفتاح ${spec.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (!isActive) {
                                Button(
                                    onClick = {
                                        AgentPrefs.putModel(context, modelId)
                                        onChanged()
                                    },
                                    enabled = hasKey
                                ) { Text("تفعيل") }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * أقسام «متقدم» — نُقلت من ApiKeysScreen.kt (المرحلة 5، تبويب «متقدم»):
 * مسابح المفاتيح الاحتياطية، سجل الطلبات، نسبة الجاهزية، النسخ الاحتياطي
 * JSON، المزامنة السحابية، وأدوات اللصق الذكي/الاستيراد. تُحذف النسخ
 * الأصلية مع الملف نفسه؛ الأقسام الخاصة المكررة مؤقتاً بين الملفين
 * مسموحة (private على مستوى الملف — كما في openUrl المكررة أصلاً).
 */
private val KEY_PATTERNS: Map<String, Regex> = mapOf(
    "gemini" to Regex("""AIzaSy[A-Za-z0-9_\-]{33}"""),
    "groq" to Regex("""gsk_[A-Za-z0-9_\-]{10,}"""),
    "openrouter" to Regex("""sk-or-v1-[A-Za-z0-9_\-]{10,}"""),
    "pixabay" to Regex("""\d{7,10}-[a-f0-9]{16,32}"""),
)

fun detectKeysForAutoFill(text: String): Map<String, String> {
    val found = linkedMapOf<String, String>()
    KEY_PATTERNS.forEach { (service, regex) ->
        val match = regex.find(text)?.value?.trim()
        if (!match.isNullOrBlank()) found[service] = match
    }
    return found
}

private val ORDERED_SINGLE_KEY_PATTERNS: List<Pair<String, Regex>> = listOf(
    "gemini" to Regex("""AIzaSy[A-Za-z0-9_\-]{33}"""),
    "groq" to Regex("""gsk_[A-Za-z0-9_\-]{10,}"""),
    "openrouter" to Regex("""sk-or-v1-[A-Za-z0-9_\-]{10,}"""),
    "pixabay" to Regex("""\d{7,10}-[a-f0-9]{16,32}""")
)

fun normalizePastedKey(raw: String): String {
    var s = KeyPoolManager.sanitizeApiKey(raw)
        .replace(Regex("\\s+"), "")
    s = s.removeSurrounding("\"").removeSurrounding("'")
    if (s.startsWith("Bearer ", ignoreCase = true)) s = s.substring(7)
    return KeyPoolManager.sanitizeApiKey(s).removeSurrounding("\"").removeSurrounding("'")
}

fun detectSingleKeyService(input: String): String? {
    val key = normalizePastedKey(input)
    if (key.length < 8) return null
    return ORDERED_SINGLE_KEY_PATTERNS.firstOrNull { (_, rx) -> rx.matches(key) }?.first
}

object ApiKeysBackupManager {
    fun generateExportJson(
        prefs: SharedPreferences,
        currentKeys: Map<String, String>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "Qabas")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        val keysObj = JSONObject()
        val allKeysToExport = listOf(
            "gemini_key",
            "groq_key",
            "openrouter_key",
            "pixabay_key",
            "leonardo_api_key",
        )

        for (k in allKeysToExport) {
            val v = currentKeys[k] ?: prefs.getString(k, "") ?: ""
            if (v.isNotBlank()) {
                keysObj.put(k, v.trim())
            }
        }

        root.put("keys", keysObj)
        return root.toString(4)
    }

    /**
     * (r17) تحليل فقط — بلا كتابة: الاستيراد السابق كان يحفظ كل قيمة
     * في الملف مباشرة دون فحص حي ودون قائمة سماح. الاستدعاء الآن يمرر
     * الخريطة إلى importKeysWithLiveCheck فتُفحص كل قيمة حياً قبل حفظها.
     */
    fun parseImport(
        rawContent: String
    ): Map<String, String> {
        val importedMap = mutableMapOf<String, String>()

        try {
            val trimmed = rawContent.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val targetObj = if (json.has("keys")) json.optJSONObject("keys") ?: json else json

                val keyMappings = mapOf(
                    "gemini" to "gemini_key",
                    "gemini_key" to "gemini_key",
                    "gemini_api_key" to "gemini_key",
                    "groq" to "groq_key",
                    "groq_key" to "groq_key",
                    "groq_api_key" to "groq_key",
                    "pixabay" to "pixabay_key",
                    "pixabay_key" to "pixabay_key",
                    "openrouter" to "openrouter_key",
                    "openrouter_key" to "openrouter_key",
                    "leonardo" to "leonardo_api_key",
                    "leonardo_api_key" to "leonardo_api_key",
                )

                val keysIterator = targetObj.keys()
                while (keysIterator.hasNext()) {
                    val rawKey = keysIterator.next()
                    val normalizedKey = rawKey.lowercase().trim()
                    val prefKey = keyMappings[normalizedKey] ?: if (keyMappings.values.contains(normalizedKey)) normalizedKey else null

                    val value = targetObj.optString(rawKey, "").trim()
                    if (prefKey != null && value.isNotBlank()) {
                        importedMap[prefKey] = value
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (importedMap.isEmpty()) {
            val lines = rawContent.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
                val cleanLine = trimmed.replace(Regex("""^(val|var|const|let|String|final|static|"|')\s*"""), "")
                val parts = cleanLine.split("=", ":", limit = 2)
                if (parts.size == 2) {
                    val keyName = parts[0].trim().lowercase().removeSurrounding("\"", "'")
                    val valStr = parts[1].trim().removeSurrounding("\"", "'").removeSuffix(";").removeSuffix(",")
                    if (valStr.isNotBlank()) {
                        when {
                            keyName.contains("openrouter") || valStr.startsWith("sk-or-") -> importedMap["openrouter_key"] = valStr
                            keyName.contains("gemini") -> importedMap["gemini_key"] = valStr
                            keyName.contains("groq") -> importedMap["groq_key"] = valStr
                            keyName.contains("pixabay") -> importedMap["pixabay_key"] = valStr
                            keyName.contains("leonardo") -> importedMap["leonardo_api_key"] = valStr
                        }
                    }
                }
            }
        }

        return importedMap
    }
}

/** مسابح المفاتيح الاحتياطية — نُقل من ApiKeysScreen (المرحلة 5) مع تكييف
 * التوقيع على ProviderSpec (prefsKey/serviceId) بدل ServiceUiConfig؛ السلوك نفسه. */
@Composable
private fun KeyPoolSection(
    prefsKey: String,
    serviceId: String,
    onPoolActiveChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var newKeyInput by remember { mutableStateOf("") }
    var testingIdx by remember { mutableStateOf<Int?>(null) }
    var poolResults by remember { mutableStateOf<Map<Int, KeyValidationResult>>(emptyMap()) }
    var ranking by remember { mutableStateOf(false) }
    var rankSummary by remember { mutableStateOf<String?>(null) }

    val pool = remember(refreshTick) { KeyPoolManager.getKeys(prefsKey) }
    val activeIdx = remember(refreshTick) { KeyPoolManager.getActiveIndex(prefsKey) }

    fun syncParent() {
        onPoolActiveChanged(KeyPoolManager.getActiveKey(prefsKey))
        refreshTick++
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = theme.secondarySurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = if (pool.size > 1) theme.success else theme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "المفاتيح الاحتياطية (${pool.size})",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pool.size > 1) {
                        Text(
                            text = "تبديل تلقائي مفعّل",
                            color = theme.success,
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "طي" else "توسيع",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = { newKeyInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("الصق مفتاحاً احتياطياً...", color = theme.textSecondary, fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(10.dp)
                )
                Button(
                    onClick = {
                        val clean = normalizePastedKey(newKeyInput)
                        if (clean.isBlank()) return@Button
                        val detected = detectSingleKeyService(clean)
                        if (detected != null && detected != serviceId) {
                            val other = PROVIDER_SPECS.find { it.id == detected }?.displayName ?: detected
                            Toast.makeText(context, "هذا المفتاح يتبع $other — أضفه في بطاقته", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (KeyPoolManager.addKey(prefsKey, clean)) {
                            newKeyInput = ""
                            syncParent()
                            Toast.makeText(context, "أُضيف المفتاح الاحتياطي", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "المفتاح موجود مسبقاً أو غير صالح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة", fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (pool.size > 1) {
                AppleSecondaryButton(
                    text = if (ranking) "يفحص السرعة..." else "ترتيب حسب الأسرع المتصل",
                    icon = Icons.Default.Bolt,
                    tint = theme.success,
                    enabled = !ranking && testingIdx == null,
                    onClick = {
                        ranking = true
                        rankSummary = null
                        scope.launch {
                            try {
                                val provider = AgentConnectivityEngine.prefsKeyToProvider(prefsKey)
                                if (provider == DetectedProvider.NONE) {
                                    rankSummary = "الترتيب التلقائي يدعم Gemini/Groq/OpenRouter فقط"
                                } else {
                                    val (ok, n, best) = AgentConnectivityEngine.rankPoolBySpeed(prefsKey, provider)
                                    rankSummary = if (ok) "تم ترتيب $n مفاتيح — الأسرع المتصل أولاً (${best}ms)"
                                    else "لا يوجد مفتاح متصل — تحقق من المفاتيح والشبكة"
                                    syncParent()
                                }
                            } finally {
                                ranking = false
                            }
                        }
                    }
                )
                if (rankSummary != null) {
                    Text(rankSummary!!, color = theme.success, fontFamily = NotoSansFont, fontSize = 11.sp)
                }
            }

            if (pool.isEmpty()) {
                Text(
                    text = "لا توجد مفاتيح احتياطية بعد — أضف بدائل هنا للتجاوز التلقائي عند امتلاء الحصة.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            } else {
                pool.forEachIndexed { idx, key ->
                    val isActive = idx == activeIdx
                    val testing = testingIdx == idx
                    val res = poolResults[idx]
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isActive) theme.success.copy(alpha = 0.08f) else theme.secondarySurface,
                        border = if (isActive) BorderStroke(1.dp, theme.success.copy(alpha = 0.45f)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isActive) theme.success else theme.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = AgentPrefs.maskSecret(key),
                                    color = theme.textPrimary,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!isActive) {
                                    TextButton(
                                        onClick = {
                                            if (KeyPoolManager.setActiveIndex(prefsKey, idx)) {
                                                syncParent()
                                                Toast.makeText(context, "تم تفعيل المفتاح البديل", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text("تفعيل", fontSize = 11.sp, color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        "نشط",
                                        fontSize = 11.sp,
                                        color = theme.success,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        testingIdx = idx
                                        scope.launch {
                                            try {
                                                val r = ApiKeyValidator.validateKey(context, serviceId, key, null)
                                                poolResults = poolResults + (idx to r)
                                            } finally {
                                                testingIdx = null
                                            }
                                        }
                                    },
                                    enabled = testingIdx == null,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    if (testing) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = theme.primary, strokeWidth = 1.5.dp)
                                    } else {
                                        Icon(Icons.Default.FlashOn, contentDescription = "فحص", tint = theme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (KeyPoolManager.removeKeyAt(prefsKey, idx)) {
                                            poolResults = poolResults - idx
                                            syncParent()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = theme.destructive, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (res != null) {
                                Text(
                                    text = res.summary,
                                    color = if (res.isValid) theme.success else theme.destructive,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp,
                                    maxLines = 1
                                )
                            }
                            val lat = remember(refreshTick, key) { KeyLatencyTracker.get(prefsKey, key) }
                            if (lat != null) {
                                Text(
                                    text = if (lat.connected) "${lat.latencyMs}ms • متصل" else "آخر فحص: غير متصل",
                                    color = if (lat.connected) theme.primary else theme.warning,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** لا يكشف المفتاح أبداً: •••• + آخر 4 أحرف فقط (عبر AgentPrefs.maskSecret). */

@Composable
private fun LiveTrafficLogsView(
    recentLogs: List<ApiUsageTracker.RequestLogItem>,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit
) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "سجل الطلبات الحية والتشخيص",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "عرض مباشر لاستجابات الخوادم وأسباب النجاح والفشل",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp
                )
            }
            Row {
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = theme.primary)
                }
                if (recentLogs.isNotEmpty()) {
                    IconButton(onClick = onClearLogs, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "مسح السجل", tint = theme.destructive)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (recentLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, tint = theme.textSecondary.copy(alpha = 0.45f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا توجد طلبات مسجلة بعد",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اضغط على «فحص شامل لجميع المفاتيح الآن» أو افحص أي مفتاح لمشاهدة سجل الطلبات هنا.",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recentLogs, key = { it.id }) { logItem ->
                    val isSuccess = logItem.isSuccess
                    val statusColor = when {
                        isSuccess -> theme.success
                        logItem.statusCode == 429 -> theme.warning
                        logItem.statusCode in 400..499 -> theme.destructive
                        logItem.statusCode in 500..599 -> Color(0xFFAF52DE)
                        else -> theme.destructive
                    }

                    val timeStr = remember(logItem.timestamp) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(logItem.timestamp))
                    }

                    AppleCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = logItem.apiName,
                                            color = statusColor,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = timeStr, color = theme.textSecondary, fontFamily = RobotoMonoFont, fontSize = 11.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = "${logItem.statusCode ?: "Error"} ${logItem.statusText}",
                                            color = statusColor,
                                            fontFamily = RobotoMonoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${logItem.latencyMs} ms",
                                        color = theme.primary,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (isSuccess) "سبب النجاح:" else "سبب الفشل:",
                                        color = statusColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = logItem.reason,
                                        color = theme.textPrimary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            if (!logItem.diagnostic.isNullOrBlank()) {
                                Surface(
                                    color = theme.secondarySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = theme.warning, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = logItem.diagnostic,
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
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

@Composable
private fun ReadinessScoreCard(readinessReport: ReadinessReport) {
    val theme = QabasTheme.current
    val scoreColor = when {
        readinessReport.percentage >= 80 -> theme.success
        readinessReport.percentage >= 40 -> theme.primary
        else -> theme.warning
    }
    AppleCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("نسبة جاهزية الخدمات الحقيقية", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(readinessReport.statusTitle, color = theme.textPrimary, fontSize = 16.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = scoreColor.copy(alpha = 0.14f),
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, scoreColor)
                ) {
                    Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        Text("${readinessReport.percentage}%", color = scoreColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { readinessReport.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = scoreColor,
                trackColor = theme.secondarySurface
            )

            Text(readinessReport.statusDescription, color = theme.textSecondary, fontSize = 12.sp, fontFamily = NotoSansFont, lineHeight = 17.sp)

            HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.45f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                readinessReport.services.forEach { srv ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (srv.isConfigured) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (srv.isConfigured) theme.success else theme.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${srv.name} (+${srv.weight}%)",
                                color = if (srv.isConfigured) theme.textPrimary else theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp,
                                fontWeight = if (srv.isConfigured) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        Text(
                            text = if (srv.isConfigured) "مفعّل" else "غير معد",
                            color = if (srv.isConfigured) theme.success else theme.warning,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonBackupCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSmartPaste: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("النسخ الاحتياطي السريع (JSON)", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("تصدير واستيراد المفاتيح لنقلها بين أجهزتك.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                    // (r17) ملف التصدير يحمل المفاتيح بنص صريح غير مشفر —
                    // كان الزر يصدّره دون أي تنبيه.
                    Text(
                        "تحذير: ملف التصدير يحتوي مفاتيحك بنص صريح غير مشفر — احفظه في مكان آمن واحذفه بعد النقل ولا تشاركه مع أحد.",
                        color = theme.warning,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApplePrimaryButton(
                    text = "تصدير JSON",
                    icon = Icons.Default.FileDownload,
                    modifier = Modifier.weight(1f),
                    onClick = onExport
                )
                AppleSecondaryButton(
                    text = "استيراد JSON",
                    icon = Icons.Default.FileUpload,
                    modifier = Modifier.weight(1f),
                    onClick = onImport
                )
            }

            AppleSecondaryButton(
                text = "اللصق الذكي والفرز التلقائي",
                icon = Icons.Default.AutoFixHigh,
                onClick = onSmartPaste
            )
        }
    }
}

@Composable
private fun SupabaseSyncCard(
    context: Context,
    onSyncedFromCloud: () -> Unit
) {
    val theme = QabasTheme.current
    var syncState by remember { mutableIntStateOf(0) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncSuccess by remember { mutableStateOf(true) }

    AppleCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("المزامنة السحابية (Supabase)", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("مزامنة المفاتيح المشفرة عبر حسابك.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
                }
            }

            if (syncMessage != null) {
                Surface(
                    color = (if (syncSuccess) theme.success else theme.destructive).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = syncMessage.orEmpty(),
                        color = if (syncSuccess) theme.success else theme.destructive,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (!SupabaseConfig.isConfigured) {
                AppleKeyRequiredBanner(
                    serviceName = "سحابة Supabase",
                    reason = "أضف SUPABASE_URL و SUPABASE_ANON_KEY في إعدادات البناء لتفعيل المزامنة السحابية."
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AppleSecondaryButton(
                        text = if (syncState == 1) "يرفع..." else "رفع",
                        icon = Icons.Default.CloudUpload,
                        enabled = syncState == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            syncState = 1; syncMessage = null
                            KeySyncService.pushToCloud(context) { ok, msg ->
                                syncSuccess = ok; syncMessage = msg; syncState = 0
                            }
                        }
                    )
                    AppleSecondaryButton(
                        text = if (syncState == 2) "يسحب..." else "سحب",
                        icon = Icons.Default.CloudDownload,
                        enabled = syncState == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            syncState = 2; syncMessage = null
                            KeySyncService.pullFromCloud(context) { ok, msg ->
                                syncSuccess = ok; syncMessage = msg; syncState = 0
                                if (ok) onSyncedFromCloud()
                            }
                        }
                    )
                    ApplePrimaryButton(
                        text = "مزامنة",
                        icon = Icons.Default.SyncAlt,
                        enabled = syncState == 0,
                        isLoading = syncState == 3,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            syncState = 3; syncMessage = null
                            KeySyncService.syncBidirectional(context) { ok, msg ->
                                syncSuccess = ok; syncMessage = msg; syncState = 0
                                if (ok) onSyncedFromCloud()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * بطاقة «اللصق الذكي» — نُقلت من تبويب مفاتيح ApiKeysScreen (المرحلة 5):
 * لصق مفتاح واحد فيتعرف نوعه تلقائياً ويُفحص حياً قبل الحفظ، مع مفتاح
 * التقاط الحافظة الاختياري (متوقف افتراضياً، يعبّئ فقط ولا يحفظ شيئاً).
 */
@Composable
private fun SmartPasteCard(
    smartInput: String,
    onSmartInputChange: (String) -> Unit,
    smartDetectedName: String?,
    smartTesting: Boolean,
    smartLatencyMs: Long?,
    validationResult: KeyValidationResult?,
    autoCaptureEnabled: Boolean,
    onAutoCaptureChange: (Boolean) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onFillAndTest: () -> Unit
) {
    val theme = QabasTheme.current
    var smartShowKey by remember { mutableStateOf(false) }
    AppleCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "اللصق الذكي — التعرف التلقائي على المفاتيح",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "التقاط مفاتيح الحافظة تلقائياً",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = "عند التفعيل: أي مفتاح تنسخه يُعبَّأ في الحقل أدناه فقط — لا يُحفظ إلا بعد فحصه حياً بزر «تعبئة وفحص».",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = autoCaptureEnabled,
                    onCheckedChange = onAutoCaptureChange
                )
            }

            OutlinedTextField(
                value = smartInput,
                onValueChange = onSmartInputChange,
                label = { Text("الصق المفتاح هنا (AIzaSy, gsk_, sk-or- ...)", fontFamily = NotoSansFont, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (smartShowKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = onPasteFromClipboard) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "لصق", tint = theme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { smartShowKey = !smartShowKey }) {
                            Icon(
                                if (smartShowKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "إظهار/إخفاء",
                                tint = theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (smartInput.isNotBlank()) {
                            IconButton(onClick = { onSmartInputChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            if (smartDetectedName != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.success.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "تم التعرف: $smartDetectedName",
                            color = theme.success,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "جاهز للحفظ والفحص المباشر",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp
                        )
                    }
                }
            } else if (normalizePastedKey(smartInput).length >= 8) {
                Text(
                    text = "صيغة غير معروفة بعد — تأكد من لصق المفتاح كاملاً",
                    color = theme.warning,
                    fontFamily = CairoFont,
                    fontSize = 12.sp
                )
            }

            ApplePrimaryButton(
                text = if (smartTesting) "يفحص الاتصال..." else "تعبئة وفحص تلقائياً",
                icon = Icons.Default.Speed,
                enabled = smartDetectedName != null,
                isLoading = smartTesting,
                onClick = onFillAndTest
            )

            if (validationResult != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (validationResult.isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (validationResult.isValid) theme.success else theme.destructive,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = validationResult.summary + (smartLatencyMs?.let { " • سرعة الاتصال: ${it}ms" } ?: ""),
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private enum class AdvancedSubTab(val title: String) {
    TOOLS("الأدوات والنسخ"),
    LOGS("سجل الطلبات")
}

/**
 * تبويب «متقدم» في القسم الموحد (المرحلة 5): كل ما كان في شاشة ApiKeysScreen
 * المنفصلة — نسبة الجاهزية، المسابح الاحتياطية، اللصق الذكي، النسخ
 * الاحتياطي JSON، المزامنة السحابية، وسجل الطلبات — بلا شاشة مفاتيح ثانية.
 * الحالة هنا محلية للتبويب؛ التغييرات تُبلَّغ للأعلى عبر onChanged فتُحدَّث
 * بطاقات المفاتيح والنماذج من المخزن الواحد نفسه.
 */
@Composable
private fun AdvancedKeysTab(
    refreshTick: Int,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()
    var subTab by remember { mutableStateOf(AdvancedSubTab.TOOLS) }
    var usageStats by remember { mutableStateOf<Map<String, ApiUsageTracker.ApiStat>>(emptyMap()) }
    var recentLogs by remember { mutableStateOf<List<ApiUsageTracker.RequestLogItem>>(emptyList()) }
    var validationResults by remember { mutableStateOf<Map<String, KeyValidationResult>>(emptyMap()) }

    val keysState = remember(refreshTick) {
        PROVIDER_SPECS.filter { it.prefsKey.isNotBlank() }
            .associate { it.prefsKey to KeyPoolManager.getActiveKey(it.prefsKey) }
    }
    val readinessReport = remember(keysState) {
        OperationalReadinessManager.calculateReadiness(context, keysState)
    }

    var smartInput by remember { mutableStateOf("") }
    var smartTesting by remember { mutableStateOf(false) }
    var smartLatencyMs by remember { mutableStateOf<Long?>(null) }
    val smartDetectedId = remember(smartInput) { detectSingleKeyService(smartInput) }
    val smartDetectedName = remember(smartDetectedId) {
        PROVIDER_SPECS.find { it.id == smartDetectedId }?.displayName
    }
    var autoCaptureMessage by remember { mutableStateOf<String?>(null) }
    var showSmartPasteDialog by remember { mutableStateOf(false) }
    var smartPasteText by remember { mutableStateOf("") }
    var lastGlobalClipboard by remember { mutableStateOf("") }
    var autoCaptureEnabled by remember { mutableStateOf(AgentPrefs.getBoolean(context, "auto_capture_clipboard_keys", false)) }

    val prefs = remember { AgentPrefs.instance(context) }
    val androidClipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    fun refreshTelemetry() {
        scope.launch {
            usageStats = ApiUsageTracker.snapshot(context)
            recentLogs = ApiUsageTracker.getRecentLogs(context)
        }
    }

    LaunchedEffect(Unit) { refreshTelemetry() }

    /**
     * (r17) الالتقاط التلقائي من الحافظة اختياري (متوقف افتراضياً)
     * ولا يحفظ شيئاً أبداً: يعبّئ المفتاح الملتقط في حقل «اللصق الذكي»
     * فقط، ويحفظه المستخدم بنفسه بزر «تعبئة وفحص» بعد فحصه حياً.
     */
    fun autoDetectAndApply(text: String) {
        if (text.isBlank() || text == lastGlobalClipboard) return
        lastGlobalClipboard = text
        val detected = detectKeysForAutoFill(text)
        if (detected.isEmpty()) return
        val (srv, key) = detected.entries.first()
        smartInput = key.trim()
        val extra = if (detected.size > 1) " (+${detected.size - 1} آخر)" else ""
        autoCaptureMessage = "التُقط مفتاح $srv من الحافظة$extra — اضغط «تعبئة وفحص» لحفظه بعد فحصه حياً"
    }

    DisposableEffect(autoCaptureEnabled) {
        if (!autoCaptureEnabled) return@DisposableEffect onDispose {}
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val text = androidClipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            autoDetectAndApply(text)
        }
        androidClipboard?.addPrimaryClipChangedListener(listener)
        onDispose { androidClipboard?.removePrimaryClipChangedListener(listener) }
    }

    LaunchedEffect(autoCaptureMessage) {
        if (autoCaptureMessage != null) {
            delay(5000)
            autoCaptureMessage = null
        }
    }

    /**
     * (r17) استيراد بعد فحص حي: كل قيمة تُفحص عند مزودها أولاً،
     * ويُحفظ الصالح فقط. أي اسم خارج المزودات الخمسة يُتجاهل.
     */
    fun importKeysWithLiveCheck(parsed: Map<String, String>) {
        val targets = parsed.entries.mapNotNull { (prefKey, value) ->
            val spec = PROVIDER_SPECS.find { it.prefsKey == prefKey } ?: return@mapNotNull null
            val clean = value.trim()
            if (clean.isBlank()) null else Pair(spec, clean)
        }
        if (targets.isEmpty()) {
            Toast.makeText(context, "لم يتم العثور على مفاتيح صالحة داخل الملف", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            var saved = 0
            var skipped = 0
            coroutineScope {
                targets.map { (spec, key) ->
                    async(Dispatchers.IO) {
                        val res = ApiKeyValidator.validateKey(context, spec.id, key, null)
                        withContext(Dispatchers.Main) {
                            validationResults = validationResults + (spec.id to res)
                            if (res.isValid) {
                                KeyPoolManager.setSingleKey(spec.prefsKey, key)
                                saved++
                            } else skipped++
                        }
                    }
                }.awaitAll()
            }
            refreshTelemetry()
            onChanged()
            Toast.makeText(
                context,
                "تم استيراد $saved مفتاحاً بعد فحصها حياً" +
                    if (skipped > 0) "؛ وتُخطّي $skipped مفتاحاً غير صالح" else "",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun runSmartFillAndTest() {
        val clean = normalizePastedKey(smartInput)
        val svcId = detectSingleKeyService(clean)
        if (svcId == null) {
            Toast.makeText(context, "تعذر التعرف على نوع المفتاح — تأكد من نسخه كاملاً", Toast.LENGTH_LONG).show()
            return
        }
        val spec = PROVIDER_SPECS.find { it.id == svcId } ?: return
        scope.launch {
            smartTesting = true
            smartLatencyMs = null
            try {
                val t0 = System.nanoTime()
                val res = ApiKeyValidator.validateKey(context, spec.id, clean, null)
                val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
                smartLatencyMs = ms
                validationResults = validationResults + (spec.id to res)
                refreshTelemetry()
                if (res.isValid) {
                    // (r17) الحفظ بعد نجاح الفحص فقط — السابق كان يحفظ
                    // أولاً ويُبقي المفتاح مخزناً حتى عند فشل الفحص.
                    KeyPoolManager.setSingleKey(spec.prefsKey, clean)
                    onChanged()
                }
                Toast.makeText(
                    context,
                    if (res.isValid) "${spec.displayName}: يعمل (${ms}ms)" else "${spec.displayName}: ${res.summary}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                Toast.makeText(context, "خطأ أثناء الفحص: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                smartTesting = false
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = ApiKeysBackupManager.generateExportJson(prefs, keysState)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, "تم تصدير ملف المفاتيح بنجاح", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "فشل التصدير: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val map = ApiKeysBackupManager.parseImport(text)
                if (map.isNotEmpty()) {
                    importKeysWithLiveCheck(map)
                } else {
                    Toast.makeText(context, "لم يتم العثور على مفاتيح صالحة داخل الملف", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ في قراءة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleSegmentedControl(
            items = AdvancedSubTab.entries.map { it to it.title },
            selectedItem = subTab,
            onSelect = { subTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when (subTab) {
            AdvancedSubTab.TOOLS -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (autoCaptureMessage != null) {
                    item {
                        Surface(
                            color = theme.success.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = theme.success, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = autoCaptureMessage.orEmpty(),
                                    color = theme.success,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { autoCaptureMessage = null }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "إغلاق", tint = theme.success, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    SpatialServiceDiagnosticsHub(onNavigateToKeys = null)
                }
                item {
                    ReadinessScoreCard(readinessReport)
                }
                item {
                    Text("المفاتيح الاحتياطية", style = MaterialTheme.typography.titleMedium)
                }
                PROVIDER_SPECS.filter { it.prefsKey.isNotBlank() }.forEach { spec ->
                    item(key = "pool_${spec.id}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                            KeyPoolSection(
                                prefsKey = spec.prefsKey,
                                serviceId = spec.id,
                                onPoolActiveChanged = { onChanged() }
                            )
                        }
                    }
                }
                item {
                    SmartPasteCard(
                        smartInput = smartInput,
                        onSmartInputChange = { smartInput = it },
                        smartDetectedName = smartDetectedName,
                        smartTesting = smartTesting,
                        smartLatencyMs = smartLatencyMs,
                        validationResult = smartDetectedId?.let { validationResults[it] },
                        autoCaptureEnabled = autoCaptureEnabled,
                        onAutoCaptureChange = {
                            autoCaptureEnabled = it
                            AgentPrefs.putBoolean(context, "auto_capture_clipboard_keys", it)
                        },
                        onPasteFromClipboard = {
                            val clip = androidClipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            if (clip.isNotBlank()) smartInput = clip
                        },
                        onFillAndTest = { runSmartFillAndTest() }
                    )
                }
                item {
                    JsonBackupCard(
                        onExport = { exportJsonLauncher.launch("qabas_api_keys.json") },
                        onImport = { importJsonLauncher.launch("*/*") },
                        onSmartPaste = { showSmartPasteDialog = true }
                    )
                }
                item {
                    SupabaseSyncCard(
                        context = context,
                        onSyncedFromCloud = { onChanged() }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
            AdvancedSubTab.LOGS -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LiveTrafficLogsView(
                    recentLogs = recentLogs,
                    onRefresh = { refreshTelemetry() },
                    onClearLogs = {
                        scope.launch {
                            ApiUsageTracker.clearLogs(context)
                            usageStats = ApiUsageTracker.snapshot(context)
                            recentLogs = ApiUsageTracker.getRecentLogs(context)
                            Toast.makeText(context, "تم مسح سجل الطلبات", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    if (showSmartPasteDialog) {
        AlertDialog(
            onDismissRequest = { showSmartPasteDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text("اللصق الذكي لمفاتيح API", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "الصق محتوى ملف .env أو كود JSON أو نصاً يحتوي على مفاتيح متعددة، وسيقوم التطبيق بفرزها وفحصها حياً وتخزين الصالح منها فقط:",
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        color = theme.textSecondary,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = smartPasteText,
                        onValueChange = { smartPasteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = {
                            Text(
                                "GEMINI_API_KEY=AIzaSy...\nGROQ_API_KEY=gsk_...",
                                fontSize = 12.sp,
                                color = theme.textSecondary.copy(alpha = 0.6f)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val map = ApiKeysBackupManager.parseImport(smartPasteText)
                        if (map.isNotEmpty()) {
                            importKeysWithLiveCheck(map)
                            showSmartPasteDialog = false
                            smartPasteText = ""
                        } else {
                            Toast.makeText(context, "لم يتم العثور على صيغ مفاتيح صالحة في النص المدخل", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("استخراج وتوزيع", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartPasteDialog = false }) {
                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }
}
