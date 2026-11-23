package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioKeysScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    /** (12.3) «خيارات متقدمة» — رابط واحد إلى ApiKeysScreen (مسابح احتياطية،
     * نسخ احتياطي JSON، إحصاءات طلبات)؛ لا شاشتا مفاتيح متوازيتان. */
    onOpenAdvancedKeys: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableIntStateOf(0) }
    var syncMessage by remember { mutableStateOf("") }
    var syncBusy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مفاتيحي") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("خيارات متقدمة للمفاتيح", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "مسابح المفاتيح الاحتياطية، النسخ الاحتياطي JSON، وإحصاءات الطلبات — " +
                                "الشاشة المفصّلة نفسها، برابط واحد من هنا لا كمدخل موازٍ.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenAdvancedKeys) {
                            Text("فتح الخيارات المتقدمة")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
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
                                message = saveAndTestKey(context, spec, key, region)
                                if (message.startsWith("تم الحفظ")) {
                                    input = ""
                                    onChanged()
                                }
                                busy = false
                            }
                        },
                        enabled = !busy
                    ) { Text("حفظ واختبار") }
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
                        onClick = {
                            scope.launch {
                                busy = true
                                KeyPoolManager.clearAll(spec.prefsKey)
                                var cloudNote = ""
                                if (spec.vaultService != null) {
                                    val deleted = runCatching {
                                        SupabaseRepository.getInstance().deleteApiKey(spec.id)
                                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                                    cloudNote = if (deleted) " وحُذف من السحابة."
                                    else " (تعذّر حذف نسخة السحابة — أعد المحاولة)."
                                } else {
                                    cloudNote = " (محلي فقط أصلاً)."
                                }
                                message = "تم الحذف$cloudNote"
                                onChanged()
                                busy = false
                            }
                        },
                        enabled = !busy && activeKey.isNotBlank()
                    ) { Text("حذف") }
                    if (busy) CircularProgressIndicator()
                }
                if (spec.keyUrl != null) {
                    TextButton(onClick = { openUrl(context, spec.keyUrl) }) {
                        Text("احصل على المفتاح")
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
/** روابط مزودي «مفاتيحي» الخمسة — تُعاد في تبويب الروابط بقسم فايب كودينج (12.8). */
val MY_KEYS_PROVIDER_LINKS: List<Pair<String, String?>>
    get() = PROVIDER_SPECS.map { it.displayName to it.keyUrl }

private suspend fun saveAndTestKey(
    context: Context,
    spec: ProviderSpec,
    key: String,
    region: String
): String {
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
                "تم الحفظ — متصل (${res.latencyMs}ms).$note"
            }
            is UniversalKeyBrain.SmartSaveResult.Failed ->
                "فشل الفحص الحي: ${res.reason} — لم يُحفظ المفتاح."
            is UniversalKeyBrain.SmartSaveResult.Unsupported ->
                "تعذّر الحفظ: ${res.reason}"
        }
    }
    val result = ApiKeyValidator.validateKey(
        context, spec.id, key
    )
    if (!result.isValid) return "فشل الفحص الحي: ${result.summary} — لم يُحفظ المفتاح."
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
    return "تم الحفظ — متصل.$note"
}
