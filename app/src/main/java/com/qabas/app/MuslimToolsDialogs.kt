package com.qabas.app

import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MuslimToolsGlassDialog(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = QabasTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = 0.12f))
                        .border(1.dp, theme.primary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                content = content
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Translator.tr("إغلاق"), color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(20.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp).border(1.dp, theme.cardBorder, RoundedCornerShape(20.dp))
    )
}

@Composable
fun ZakatCalculatorDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
    val theme = QabasTheme.current
    var cash by remember { mutableStateOf("") }
    var gold by remember { mutableStateOf("") }
    var silver by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf("") }
    var goldPricePerGram24k by remember { mutableStateOf("") }
    var silverPricePerGram by remember { mutableStateOf("") }
    var carat by remember { mutableIntStateOf(24) }

    var result by remember { mutableStateOf<MuslimToolsEngine.ZakatResult?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun invalidateResult() {
        result = null
        validationError = null
    }

    MuslimToolsGlassDialog(
        title = Translator.tr("حاسبة الزكاة الذكية"),
        icon = Icons.Default.Payments,
        onDismiss = onDismiss
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    Translator.tr("حاسبة تقديرية تعتمد نصاب الذهب (85 غراماً عيار 24) ومعدل 2.5% مع افتراض اكتمال الحول. لا تخصم الديون ولا تغطي جميع تفاصيل أحكام الزكاة؛ راجع جهة شرعية موثوقة. أدخل جميع المبالغ والأسعار بالعملة نفسها ولا يتم تحويل العملات."),
                    color = qabasTextSecondary(),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    fontFamily = CairoFont
                )
            }
            item {
                ZakatInputField(Translator.tr("السيولة النقدية (بالعملة نفسها)"), cash, { cash = it; invalidateResult() }, "💵")
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ZakatInputField(Translator.tr("وزن الذهب (غرام)"), gold, { gold = it; invalidateResult() }, "🟡")
                    }
                    Column(modifier = Modifier.width(80.dp)) {
                        Text(Translator.tr("العيار"), color = qabasTextSecondary(), fontSize = 10.sp, fontFamily = CairoFont)
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                onClick = { expanded = true },
                                color = theme.secondarySurface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    carat.toString(),
                                    color = PresetPrimary,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(theme.secondarySurface)) {
                                listOf(24, 22, 21, 18).forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.toString(), color = theme.textPrimary) },
                                        onClick = { carat = c; expanded = false; invalidateResult() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                ZakatInputField(Translator.tr("وزن الفضة (غرام)"), silver, { silver = it; invalidateResult() }, "⚪")
            }
            item {
                ZakatInputField(Translator.tr("عروض التجارة والأسهم (بالعملة نفسها)"), trade, { trade = it; invalidateResult() }, "📈")
            }
            item {
                ZakatInputField(
                    Translator.tr("سعر غرام الذهب عيار 24 الحالي (بالعملة نفسها)"),
                    goldPricePerGram24k,
                    { goldPricePerGram24k = it; invalidateResult() },
                    "💰"
                )
            }
            item {
                ZakatInputField(
                    Translator.tr("سعر غرام الفضة الحالي (بالعملة نفسها؛ مطلوب عند إدخال وزن الفضة)"),
                    silverPricePerGram,
                    { silverPricePerGram = it; invalidateResult() },
                    "⚪"
                )
            }
            validationError?.let { message ->
                item {
                    Text(
                        Translator.tr(message),
                        color = theme.destructive,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val parsedCash = parseZakatAmount(cash, allowBlank = true)
                        val parsedGold = parseZakatAmount(gold, allowBlank = true)
                        val parsedSilver = parseZakatAmount(silver, allowBlank = true)
                        val parsedTrade = parseZakatAmount(trade, allowBlank = true)
                        val parsedGoldPrice = parseZakatAmount(goldPricePerGram24k, allowBlank = false)
                        val parsedSilverPrice = parseZakatAmount(
                            silverPricePerGram,
                            allowBlank = parsedSilver == 0.0
                        )

                        when {
                            parsedCash == null || parsedGold == null || parsedSilver == null || parsedTrade == null -> {
                                result = null
                                validationError = "تحقق من إدخال أرقام صحيحة غير سالبة للأصول والأوزان."
                            }
                            parsedGoldPrice == null || parsedGoldPrice <= 0.0 -> {
                                result = null
                                validationError = "أدخل سعر غرام الذهب عيار 24 موجباً من السوق الحالي."
                            }
                            parsedSilverPrice == null || (parsedSilver > 0.0 && parsedSilverPrice <= 0.0) -> {
                                result = null
                                validationError = "تحقق من سعر الفضة؛ يجب أن يكون موجباً عند إدخال وزن فضة."
                            }
                            else -> {
                                try {
                                    result = engine.calculateZakat(
                                        cash = parsedCash,
                                        goldGrams = parsedGold,
                                        goldCarat = carat,
                                        silverGrams = parsedSilver,
                                        tradeGoods = parsedTrade,
                                        goldPricePerGram24k = parsedGoldPrice,
                                        silverPricePerGram = parsedSilverPrice
                                    )
                                    validationError = null
                                    AppSoundManager.playSuccess()
                                } catch (_: IllegalArgumentException) {
                                    result = null
                                    validationError = "تعذر الحساب؛ تحقق من القيم ومن أن مجموعها ضمن النطاق العددي."
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(Translator.tr("احسب الزكاة الآن"), color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
            result?.let { res ->
                item {
                    Surface(
                        color = if (res.isNisabReached) theme.success.copy(alpha = 0.15f) else theme.destructive.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (res.isNisabReached) theme.success else theme.destructive)
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (res.isNisabReached) Translator.tr("بلغ مجموع المدخلات نصاب الذهب (85 غ) ✅") else Translator.tr("لم يبلغ مجموع المدخلات نصاب الذهب (85 غ) ⏳"),
                                color = if (res.isNisabReached) theme.success else theme.destructive,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(Translator.tr("إجمالي الأصول المدخلة (بالعملة نفسها):"), color = theme.textPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                            Text(String.format(Locale.US, "%.2f", res.totalWealth), color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(Translator.tr("نصاب الذهب المحسوب (85 غ):"), color = qabasTextSecondary(), fontSize = 11.sp, fontFamily = CairoFont)
                            Text(String.format(Locale.US, "%.2f", res.nisabValueGold), color = qabasTextSecondary(), fontSize = 13.sp, fontFamily = CairoFont)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(Translator.tr("قيمة الزكاة التقديرية (2.5%):"), color = theme.textPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                            Text(
                                String.format(Locale.US, "%.2f", res.zakatAmount),
                                color = PresetPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = CairoFont
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FastingTrackerDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
    val theme = QabasTheme.current
    var total by remember { mutableIntStateOf(engine.getMissedFastingDays()) }
    var completed by remember { mutableIntStateOf(engine.getCompletedFastingDays()) }
    var showEdit by remember { mutableStateOf(total == 0) }
    var inputDays by remember { mutableStateOf(total.toString()) }

    MuslimToolsGlassDialog(
        title = Translator.tr("متابع قضاء رمضان"),
        icon = Icons.Default.NightsStay,
        onDismiss = onDismiss
    ) {
        if (showEdit) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Translator.tr("أدخل إجمالي عدد أيام القضاء الواجبة عليك:"), color = theme.textPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                OutlinedTextField(
                    value = inputDays,
                    onValueChange = { inputDays = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textPrimary, unfocusedTextColor = theme.textPrimary)
                )
                Button(
                    onClick = {
                        val d = (inputDays.toIntOrNull() ?: 0).coerceAtLeast(0)
                        engine.setMissedFastingDays(d)
                        total = engine.getMissedFastingDays()
                        completed = engine.getCompletedFastingDays()
                        inputDays = total.toString()
                        showEdit = false
                        AppSoundManager.playActivate()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary)
                ) {
                    Text(Translator.tr("حفظ"), color = DeepSlate, fontFamily = CairoFont)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                    val progress = if (total > 0) completed.toFloat() / total else 0f
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = PresetPrimary,
                        trackColor = theme.textPrimary.copy(alpha = 0.1f),
                        strokeWidth = 12.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${((progress * 100).toInt())}%", color = PresetPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(Translator.tr("منجز"), color = qabasTextSecondary(), fontSize = 10.sp, fontFamily = CairoFont)
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FastingStatBox(Translator.tr("الإجمالي"), total.toString(), PresetPrimary)
                    FastingStatBox(Translator.tr("المتبقي"), (total - completed).toString(), theme.destructive)
                }

                Button(
                    onClick = {
                        if (engine.incrementCompletedFastingDays()) {
                            completed = engine.getCompletedFastingDays()
                            AppSoundManager.playSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.success),
                    shape = RoundedCornerShape(12.dp),
                    enabled = completed < total
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Translator.tr("تم صيام يوم واحد ✅"), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { showEdit = true }) {
                    Text(Translator.tr("تعديل الإجمالي"), color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun QuranKhatmaDialog(
    onDismiss: () -> Unit,
    onOpenMushaf: () -> Unit
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    // الخطة الواحدة الموحّدة: مخطط المصحف نفسه — لا مخزن ثانٍ.
    // (كان هنا مخطط موازٍ في MuslimToolsEngine لا يقرأ تقدّمه أحد،
    // وإحصائيات العبادة تعرض 0% إلى الأبد.)
    var savedPlan by remember { mutableStateOf(UserPreferencesManager.getMushafKhatmahPlan(context)) }
    var progress by remember { mutableStateOf(UserPreferencesManager.getKhatmahProgress(context)) }
    var selectedDays by remember { mutableIntStateOf(savedPlan?.targetDays ?: 30) }
    val lastRead = remember { UserPreferencesManager.getLastReadQuran(context) }
    val todayStart = remember { startOfDayMillis(System.currentTimeMillis()) }
    // معاينة خطة جديدة قبل اعتمادها: من موضع القراءة الفعلية،
    // والمتبقي والورد اليومي منها لا من إجمالي المصحف.
    val prospectivePlan = remember(selectedDays, lastRead) {
        UserPreferencesManager.MushafKhatmahPlan(
            startSurah = lastRead?.surah ?: 1,
            startAyah = lastRead?.ayah ?: 1,
            startPage = lastRead?.page,
            startMillis = todayStart,
            targetDays = selectedDays.coerceAtLeast(1),
            createdAtMillis = System.currentTimeMillis()
        )
    }
    val prospectiveProgress = remember(prospectivePlan) {
        UserPreferencesManager.computeKhatmahProgress(
            plan = prospectivePlan,
            currentSurah = prospectivePlan.startSurah,
            currentAyah = prospectivePlan.startAyah,
            currentPage = prospectivePlan.startPage,
            usePages = prospectivePlan.startPage != null,
            nowMillis = System.currentTimeMillis()
        )
    }

    MuslimToolsGlassDialog(
        title = Translator.tr("مخطط الختمة الذكي"),
        icon = Icons.Default.AutoStories,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(Translator.tr("اختر مدة الختمة (بالأيام):"), color = theme.textPrimary, fontSize = 13.sp, fontFamily = CairoFont)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 30, 60).forEach { days ->
                    Surface(
                        onClick = { selectedDays = days; AppSoundManager.playClick() },
                        modifier = Modifier.weight(1f),
                        color = if (selectedDays == days) PresetPrimary.copy(alpha = 0.2f) else theme.secondarySurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selectedDays == days) PresetPrimary else Color.Transparent)
                    ) {
                        Text(
                            "$days ${Translator.tr("يوم")}",
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center,
                            color = if (selectedDays == days) PresetPrimary else theme.textPrimary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Surface(
                color = theme.secondarySurface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (progress != null) {
                        // خطة نشطة: تقدّم حقيقي من القراءة الفعلية، وورد اليوم
                        // من المتبقي والأيام المتبقية — لا قسمة ثابتة.
                        Text(Translator.tr("تقدّم خطتك:"), color = qabasTextSecondary(), fontSize = 12.sp, fontFamily = CairoFont)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${(progress!!.fraction * 100).toInt()}%",
                            color = PresetPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress!!.fraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = PresetPrimary,
                            trackColor = theme.textPrimary.copy(alpha = 0.1f),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Translator.tr("قرأت ") +
                                "${progress!!.readUnits} ${progress!!.unitWord}" +
                                Translator.tr(" من أصل ") +
                                "${progress!!.totalUnits} ${progress!!.unitWord}" +
                                Translator.tr(" — المتبقي ") +
                                "${progress!!.leftUnits} ${progress!!.unitWord}" +
                                Translator.tr(" في ") +
                                "${progress!!.daysLeft}" +
                                Translator.tr(" يوم."),
                            color = theme.textPrimary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontFamily = CairoFont,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            Translator.tr("ورد اليوم المقترح: ") +
                                "${progress!!.suggestedDailyUnits} ${progress!!.unitWord}" +
                                Translator.tr(" يومياً"),
                            color = theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont
                        )
                        if (savedPlan != null && savedPlan!!.targetDays != selectedDays) {
                            // معاينة صادقة للمدة الجديدة قبل اعتمادها.
                            val newRemainingDays =
                                (selectedDays - progress!!.daysElapsed + 1).coerceAtLeast(1)
                            val newDaily =
                                (progress!!.leftUnits + newRemainingDays - 1) / newRemainingDays
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                Translator.tr("بالمدة الجديدة (") +
                                    "$selectedDays" +
                                    Translator.tr(" يوماً): نحو ") +
                                    "$newDaily ${progress!!.unitWord}" +
                                    Translator.tr(" يومياً"),
                                color = theme.textPrimary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontFamily = CairoFont
                            )
                        }
                        if (progress!!.overdue) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                Translator.tr("انتهت المدة وبقي ") +
                                    "${progress!!.leftUnits} ${progress!!.unitWord}" +
                                    Translator.tr(" — حدّث المدة ليعيد الحساب."),
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontFamily = CairoFont,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(Translator.tr("وردك اليومي المقترح:"), color = qabasTextSecondary(), fontSize = 12.sp, fontFamily = CairoFont)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${prospectiveProgress.suggestedDailyUnits} ${prospectiveProgress.unitWord}",
                            color = PresetPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            Translator.tr("المتبقي من المصحف: ") +
                                "${prospectiveProgress.totalUnits} ${prospectiveProgress.unitWord}" +
                                Translator.tr(" — من ") +
                                if (lastRead != null) {
                                    Translator.tr("سورة ") +
                                        QuranDataProvider.surahNameOf(lastRead.surah) +
                                        Translator.tr(" آية ") +
                                        "${lastRead.ayah}"
                                } else {
                                    Translator.tr("أول المصحف")
                                },
                            color = theme.textPrimary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontFamily = CairoFont,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (savedPlan != null) {
                Text(
                    Translator.tr(
                        if (savedPlan!!.targetDays == selectedDays) "هذه خطتك النشطة — تقدّمها من قراءتك الفعلية في المصحف"
                        else "تغيّرت المدة؛ اعتمدها لتحديث هدف الخطة (تبقى بدايتها كما هي)"
                    ),
                    color = if (savedPlan!!.targetDays == selectedDays) theme.success else qabasTextSecondary(),
                    fontSize = 11.sp,
                    fontFamily = CairoFont
                )
            }

            Button(
                onClick = {
                    // تحديث الهدف فقط عند وجود خطة — البداية وتاريخها يبقيان؛
                    // وإلا إنشاء خطة جديدة من موضع القراءة الفعلية.
                    val plan = savedPlan?.copy(targetDays = selectedDays) ?: prospectivePlan
                    UserPreferencesManager.saveMushafKhatmahPlan(context, plan)
                    val verified = UserPreferencesManager.getMushafKhatmahPlan(context)
                    if (verified != null && verified.targetDays == selectedDays) {
                        savedPlan = verified
                        progress = UserPreferencesManager.getKhatmahProgress(context)
                        AppSoundManager.playSuccess()
                        android.widget.Toast.makeText(
                            context,
                            Translator.tr("تم حفظ خطة الختمة على هذا الجهاز"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            Translator.tr("تعذر حفظ خطة الختمة؛ حاول مرة أخرى"),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary)
            ) {
                Text(
                    Translator.tr(if (savedPlan == null) "اعتماد خطة الختمة 📖" else "تحديث خطة الختمة 📖"),
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onClick = onOpenMushaf,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    Translator.tr("فتح مخطط الختمة في المصحف"),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** بداية اليوم (00:00) بتوقيت الجهاز. */
private fun startOfDayMillis(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

@Composable
fun HajjUmrahDialog(onDismiss: () -> Unit) {
    val theme = QabasTheme.current
    val steps = listOf(
        "الإحرام: الاغتسال ولبس ملابس الإحرام والنية.",
        "الطواف: الطواف حول الكعبة سبعة أشواط.",
        "السعي: السعي بين الصفا والمروة سبعة أشواط.",
        "التحلل: الحلق أو التقصير للمعتمر."
    )
    var currentStep by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ar")
        }
        onDispose { tts?.stop(); tts?.shutdown() }
    }

    MuslimToolsGlassDialog(
        title = Translator.tr("دليل الحج والعمرة"),
        icon = Icons.Default.DirectionsWalk,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / steps.size },
                modifier = Modifier.fillMaxWidth(),
                color = PresetPrimary,
                trackColor = theme.textPrimary.copy(alpha = 0.1f)
            )
            
            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${Translator.tr("خطوة")} ${currentStep + 1} ${Translator.tr("من")} ${steps.size}",
                        color = PresetPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        steps[currentStep],
                        color = theme.textPrimary,
                        fontSize = 15.sp,
                        fontFamily = CairoFont,
                        lineHeight = 22.sp
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { tts?.speak(steps[currentStep], TextToSpeech.QUEUE_FLUSH, null, null) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.secondarySurface)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Translator.tr("استماع"), fontFamily = CairoFont)
                }
                
                Button(
                    onClick = {
                        if (currentStep < steps.size - 1) {
                            currentStep++
                            AppSoundManager.playClick()
                        } else {
                            onDismiss()
                            AppSoundManager.playSuccess()
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary)
                ) {
                    Text(
                        if (currentStep < steps.size - 1) Translator.tr("التالي") else Translator.tr("تم بحمد الله"),
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HijriCalendarDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
    val theme = QabasTheme.current
    val hijri = remember { engine.getHijriDate() }
    
    MuslimToolsGlassDialog(
        title = Translator.tr("التقويم الهجري"),
        icon = Icons.Default.Event,
        onDismiss = onDismiss
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        Brush.radialGradient(listOf(PresetPrimary.copy(alpha = 0.1f), Color.Transparent)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NightsStay, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(hijri, color = theme.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                    Text(Translator.tr("تاريخ اليوم"), color = qabasTextSecondary(), fontSize = 12.sp, fontFamily = CairoFont)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                Translator.tr("التقويم الهجري يعتمد على دورة القمر، وهو التقويم الرسمي للمناسبات الإسلامية."),
                color = qabasTextSecondary(),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                fontFamily = CairoFont
            )
        }
    }
}

private fun parseZakatAmount(raw: String, allowBlank: Boolean): Double? {
    if (raw.isBlank()) return if (allowBlank) 0.0 else null

    val normalizedDigits = buildString(raw.length) {
        raw.forEach { char ->
            val digit = when {
                char in '٠'..'٩' -> char.code - '٠'.code
                char in '۰'..'۹' -> char.code - '۰'.code
                else -> null
            }
            if (digit == null) append(char) else append(('0'.code + digit).toChar())
        }
    }
    val normalized = normalizedDigits
        .trim()
        .replace('٫', '.')
        .replace("٬", "")
        .replace(" ", "")
    val parsed = normalized.toDoubleOrNull() ?: return null
    return parsed.takeIf { it.isFinite() && it >= 0.0 }
}

@Composable
private fun ZakatInputField(label: String, value: String, onValueChange: (String) -> Unit, icon: String) {
    val theme = QabasTheme.current
    Column {
        Text(label, color = qabasTextSecondary(), fontSize = 11.sp, fontFamily = CairoFont)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Text(icon, modifier = Modifier.padding(start = 8.dp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PresetPrimary,
                unfocusedBorderColor = theme.cardBorder,
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun FastingStatBox(label: String, value: String, color: Color) {
    val theme = QabasTheme.current
    Surface(
        color = theme.secondarySurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.width(100.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = qabasTextSecondary(), fontSize = 10.sp, fontFamily = CairoFont)
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
fun SmartTasbeehDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
    val theme = QabasTheme.current
    val types = listOf("سبحان الله", "الحمد لله", "الله أكبر", "أستغفر الله", "لا إله إلا الله")
    // ق144: النوع المختار يبقى عبر التدوير — كان remember عادياً فيعيد
    // النوع الأول فيدخل التدوير نقرات لاحقة في ذكر آخر بصمت.
    var selectedType by rememberSaveable { mutableStateOf(types[0]) }
    var count by remember { mutableIntStateOf(engine.getTasbeehCount(selectedType)) }
    var showResetConfirm by remember { mutableStateOf(false) }
    // ق144: العدّاد تراكمي مدى الحياة بلا هدف — جولة 33 (معيار المشكاة
    // نفسه) عرض مشتق بلا تغيير لسلوك العدّ: تقدّم الجولة الحالية +
    // عدد الجولات المكتملة، ورسالة إتمام عند مضاعف 33.
    val tasbeehTarget = 33
    val roundsDone = count / tasbeehTarget
    val inRound = when {
        count <= 0 -> 0
        count % tasbeehTarget == 0 -> tasbeehTarget
        else -> count % tasbeehTarget
    }

    // ق144: التصفير يمسح عدّاداً تراكمياً بلا رجعة — كان بنقرة واحدة
    // بلا تأكيد؛ الآن تأكيد قبل المسح (سابقة ق128 وق140).
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(Translator.tr("تصفير العدّاد؟"), color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "سيُصفَّر عدّاد «$selectedType» التراكمي ($count) نهائياً، ولا يمكن التراجع.",
                    color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    engine.resetTasbeeh(selectedType)
                    count = 0
                    showResetConfirm = false
                    AppSoundManager.playClick()
                }) {
                    Text(Translator.tr("تصفير"), color = theme.destructive, fontFamily = CairoFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(Translator.tr("إلغاء"), color = theme.primary, fontFamily = CairoFont)
                }
            },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    MuslimToolsGlassDialog(
        title = Translator.tr("المسبحة الذكية"),
        icon = Icons.Default.Fingerprint,
        onDismiss = onDismiss
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Type Selector
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(types) { type ->
                    val isSelected = selectedType == type
                    Surface(
                        onClick = {
                            selectedType = type
                            count = engine.getTasbeehCount(type)
                            AppSoundManager.playClick()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PresetPrimary else theme.textPrimary.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, if (isSelected) PresetPrimary else theme.textPrimary.copy(alpha = 0.15f))
                    ) {
                        Text(
                            type,
                            color = if (isSelected) DeepSlate else theme.textPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Tasbeeh Counter
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(PresetPrimary.copy(alpha = 0.15f), Color.Transparent)))
                    .border(2.dp, PresetPrimary.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        engine.incrementTasbeeh(selectedType)
                        count = engine.getTasbeehCount(selectedType)
                        AppSoundManager.playClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(count.toString(), color = PresetPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text(selectedType, color = theme.textPrimary.copy(alpha = 0.7f), fontSize = 14.sp, fontFamily = CairoFont)
                }
            }

            // تقدّم الجولة الحالية من 33 + الجولات المكتملة (عرض مشتق من
            // العدّاد التراكمي — لا يغيّر العدّ ولا إجمالي التسبيح).
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (count > 0 && count % tasbeehTarget == 0)
                        "✓ أتممت جولة 33 من $selectedType"
                    else
                        "الجولة الحالية: $inRound / $tasbeehTarget",
                    color = if (count > 0 && count % tasbeehTarget == 0) theme.success else theme.textPrimary.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = CairoFont
                )
                LinearProgressIndicator(
                    progress = { inRound / tasbeehTarget.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = PresetPrimary,
                    trackColor = theme.textPrimary.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                if (roundsDone > 0) {
                    Text(
                        "جولات مكتملة: $roundsDone",
                        color = qabasTextSecondary(),
                        fontSize = 11.sp,
                        fontFamily = CairoFont
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        showResetConfirm = true
                        AppSoundManager.playClick()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.destructive.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(Translator.tr("تصفير"), color = theme.destructive, fontFamily = CairoFont)
                }
            }
        }
    }
}

@Composable
fun HabitsTrackerDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
    val theme = QabasTheme.current
    var habits by remember { mutableStateOf(engine.getHabits()) }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    MuslimToolsGlassDialog(
        title = Translator.tr("متابع العادات"),
        icon = Icons.Default.DoneAll,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Translator.tr("عاداتك اليومية لزيادة الحسنات:"), color = qabasTextSecondary(), fontSize = 12.sp, fontFamily = CairoFont)
            
            habits.forEach { habit ->
                val isDone = habit.lastDoneDate == today
                Surface(
                    onClick = {
                        engine.toggleHabit(habit.id)
                        habits = engine.getHabits()
                        AppSoundManager.playToggle()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDone) theme.success.copy(alpha = 0.1f) else theme.secondarySurface,
                    border = BorderStroke(1.dp, if (isDone) theme.success else theme.textPrimary.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(habit.name, color = if (isDone) theme.success else theme.textPrimary, fontFamily = CairoFont)
                        Icon(
                            if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isDone) theme.success else theme.textPrimary.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorshipAnalyticsDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val stats = remember { engine.getWorshipStats() }
    val hasKhatmaPlan = remember { UserPreferencesManager.getMushafKhatmahPlan(context) != null }

    MuslimToolsGlassDialog(
        title = Translator.tr("إحصائيات العبادة"),
        icon = Icons.Default.Analytics,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsCard(Translator.tr("إجمالي التسبيح"), stats.totalTasbeeh.toString(), "📿", Modifier.weight(1f))
                AnalyticsCard(Translator.tr("رصيد الحسنات"), stats.totalHasanat.toString(), "💎", Modifier.weight(1f))
            }

            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(Translator.tr("تقدم الصيام"), color = theme.textPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { stats.fastingProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = theme.success,
                        trackColor = theme.textPrimary.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        Translator.tr("تقدم الختمة") + " • ${(stats.khatmaProgress * 100).toInt()}%",
                        color = theme.textPrimary, fontSize = 12.sp, fontFamily = CairoFont
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { stats.khatmaProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = PresetPrimary,
                        trackColor = theme.textPrimary.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    if (!hasKhatmaPlan) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Translator.tr("لا توجد خطة ختمة بعد — أنشئها من «مخطط الختمة» في المصحف أو من أدوات الحسنات."),
                            color = qabasTextSecondary(), fontSize = 11.sp, fontFamily = CairoFont
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCard(label: String, value: String, icon: String, modifier: Modifier = Modifier) {
    val theme = QabasTheme.current
    Surface(
        modifier = modifier,
        color = theme.secondarySurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = PresetPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = qabasTextSecondary(), fontSize = 10.sp, fontFamily = CairoFont)
        }
    }
}
