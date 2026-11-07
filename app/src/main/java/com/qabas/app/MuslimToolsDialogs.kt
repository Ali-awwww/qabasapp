package com.qabas.app

import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GoldPrimary.copy(alpha = 0.15f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
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
                Text(Translator.tr("إغلاق"), color = GoldPrimary, fontFamily = CairoFont)
            }
        },
        containerColor = Color(0xFF0F172A).copy(alpha = 0.95f),
        shape = CutCornerShape(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun ZakatCalculatorDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
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
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    carat.toString(),
                                    color = GoldPrimary,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color(0xFF1E293B))) {
                                listOf(24, 22, 21, 18).forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.toString(), color = Color.White) },
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
                        color = Color(0xFFFF8A80),
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
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = CutCornerShape(10.dp)
                ) {
                    Text(Translator.tr("احسب الزكاة الآن"), color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
            result?.let { res ->
                item {
                    Surface(
                        color = if (res.isNisabReached) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (res.isNisabReached) Color(0xFF10B981) else Color(0xFFEF4444))
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (res.isNisabReached) Translator.tr("بلغ مجموع المدخلات نصاب الذهب (85 غ) ✅") else Translator.tr("لم يبلغ مجموع المدخلات نصاب الذهب (85 غ) ⏳"),
                                color = if (res.isNisabReached) Color(0xFF34D399) else Color(0xFFF87171),
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(Translator.tr("إجمالي الأصول المدخلة (بالعملة نفسها):"), color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                            Text(String.format(Locale.US, "%.2f", res.totalWealth), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(Translator.tr("نصاب الذهب المحسوب (85 غ):"), color = qabasTextSecondary(), fontSize = 11.sp, fontFamily = CairoFont)
                            Text(String.format(Locale.US, "%.2f", res.nisabValueGold), color = qabasTextSecondary(), fontSize = 13.sp, fontFamily = CairoFont)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(Translator.tr("قيمة الزكاة التقديرية (2.5%):"), color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                            Text(
                                String.format(Locale.US, "%.2f", res.zakatAmount),
                                color = GoldPrimary,
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
                Text(Translator.tr("أدخل إجمالي عدد أيام القضاء الواجبة عليك:"), color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                OutlinedTextField(
                    value = inputDays,
                    onValueChange = { inputDays = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
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
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
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
                        color = GoldPrimary,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeWidth = 12.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${((progress * 100).toInt())}%", color = GoldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(Translator.tr("منجز"), color = qabasTextSecondary(), fontSize = 10.sp, fontFamily = CairoFont)
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FastingStatBox(Translator.tr("الإجمالي"), total.toString(), GoldPrimary)
                    FastingStatBox(Translator.tr("المتبقي"), (total - completed).toString(), Color(0xFFEF4444))
                }

                Button(
                    onClick = {
                        if (engine.incrementCompletedFastingDays()) {
                            completed = engine.getCompletedFastingDays()
                            AppSoundManager.playSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
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
fun QuranKhatmaDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
    val context = LocalContext.current
    val existingPlan = remember(engine) { engine.getSavedKhatmaPlan() }
    var selectedDays by remember { mutableIntStateOf(existingPlan?.days ?: 30) }
    var savedDays by remember { mutableIntStateOf(existingPlan?.days ?: 0) }
    val plan = remember(selectedDays) { engine.generateKhatmaPlan(selectedDays) }

    MuslimToolsGlassDialog(
        title = Translator.tr("مخطط الختمة الذكي"),
        icon = Icons.Default.AutoStories,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(Translator.tr("اختر مدة الختمة (بالأيام):"), color = Color.White, fontSize = 13.sp, fontFamily = CairoFont)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 30, 60).forEach { days ->
                    Surface(
                        onClick = { selectedDays = days; AppSoundManager.playClick() },
                        modifier = Modifier.weight(1f),
                        color = if (selectedDays == days) GoldPrimary.copy(alpha = 0.2f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selectedDays == days) GoldPrimary else Color.Transparent)
                    ) {
                        Text(
                            "$days ${Translator.tr("يوم")}",
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center,
                            color = if (selectedDays == days) GoldPrimary else Color.White,
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                shape = CutCornerShape(12.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Translator.tr("وردك اليومي المقترح:"), color = qabasTextSecondary(), fontSize = 12.sp, fontFamily = CairoFont)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(plan.portionsPerDay, color = GoldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${Translator.tr("بمعدل")} ${String.format("%.1f", plan.pagesPerDay)} ${Translator.tr("صفحة يومياً")}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = CairoFont
                    )
                }
            }

            if (savedDays > 0) {
                Text(
                    Translator.tr(
                        if (savedDays == selectedDays) "هذه خطتك المحفوظة على هذا الجهاز" else "تغيّرت الخطة؛ اعتمدها لحفظ التعديل"
                    ),
                    color = if (savedDays == selectedDays) Color(0xFF86EFAC) else qabasTextSecondary(),
                    fontSize = 11.sp,
                    fontFamily = CairoFont
                )
            }

            Button(
                onClick = {
                    if (engine.saveKhatmaPlan(selectedDays)) {
                        savedDays = selectedDays
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
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Text(
                    Translator.tr(if (savedDays == selectedDays) "تحديث خطة الختمة 📖" else "اعتماد خطة الختمة 📖"),
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HajjUmrahDialog(onDismiss: () -> Unit) {
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
                color = GoldPrimary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${Translator.tr("خطوة")} ${currentStep + 1} ${Translator.tr("من")} ${steps.size}",
                        color = GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        steps[currentStep],
                        color = Color.White,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
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
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
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
                        Brush.radialGradient(listOf(GoldPrimary.copy(alpha = 0.1f), Color.Transparent)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NightsStay, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(hijri, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
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
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun FastingStatBox(label: String, value: String, color: Color) {
    Surface(
        color = Color(0xFF1E293B),
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
    val types = listOf("سبحان الله", "الحمد لله", "الله أكبر", "أستغفر الله", "لا إله إلا الله")
    var selectedType by remember { mutableStateOf(types[0]) }
    var count by remember { mutableIntStateOf(engine.getTasbeehCount(selectedType)) }

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
                        color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.15f))
                    ) {
                        Text(
                            type,
                            color = if (isSelected) DeepSlate else Color.White,
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
                    .background(Brush.radialGradient(listOf(GoldPrimary.copy(alpha = 0.15f), Color.Transparent)))
                    .border(2.dp, GoldPrimary.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        engine.incrementTasbeeh(selectedType)
                        count = engine.getTasbeehCount(selectedType)
                        AppSoundManager.playClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(count.toString(), color = GoldPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text(selectedType, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontFamily = CairoFont)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        engine.resetTasbeeh(selectedType)
                        count = 0
                        AppSoundManager.playClick()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(Translator.tr("تصفير"), color = Color(0xFFEF4444), fontFamily = CairoFont)
                }
            }
        }
    }
}

@Composable
fun HabitsTrackerDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
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
                    color = if (isDone) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFF1E293B),
                    border = BorderStroke(1.dp, if (isDone) Color(0xFF10B981) else Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(habit.name, color = if (isDone) Color(0xFF34D399) else Color.White, fontFamily = CairoFont)
                        Icon(
                            if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isDone) Color(0xFF10B981) else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorshipAnalyticsDialog(onDismiss: () -> Unit, engine: MuslimToolsEngine) {
    val stats = remember { engine.getWorshipStats() }

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
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(Translator.tr("تقدم الصيام"), color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { stats.fastingProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Color(0xFF10B981),
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(Translator.tr("تقدم الختمة"), color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { stats.khatmaProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = GoldPrimary,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCard(label: String, value: String, icon: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = qabasTextSecondary(), fontSize = 10.sp, fontFamily = CairoFont)
        }
    }
}
