package com.qabas.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasteProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tastes by remember { mutableStateOf<List<TasteEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showRequestDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val db = AppDatabase.getDatabase(context)
            tastes = db.tasteDao().getAllTastes()
        } catch (e: Exception) {
            // Error handling
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("هوية الذكاء الاصطناعي", color = GoldPrimary, fontFamily = CairoFont) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardSurface)
            )
        },
        containerColor = DeepSlate,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showRequestDialog = true },
                containerColor = GoldPrimary,
                contentColor = DeepSlate,
                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                text = { Text("طلب أسلوب جديد", fontFamily = TajawalFont, fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldPrimary)
            }
        } else if (tastes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "لم يتم تكوين هوية الذكاء الاصطناعي بعد.",
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontSize = 18.sp
                    )
                    Text(
                        "مع كل مشروع تقوم بإنشائه، سيتعلم التطبيق أسلوبك وذوقك الإخراجي.",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "الذوق الإخراجي المتطور",
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        "يتعلم المخرج الآلي من مشاريعك السابقة واختياراتك لتقديم اقتراحات تناسب ذوقك وتطورك الإخراجي.",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                }

                val grouped = tastes.groupBy { it.category }
                grouped.forEach { (category, list) ->
                    item {
                        val translatedCategory = when(category) {
                            "STYLE" -> "الأسلوب البصري"
                            "TONE" -> "النبرة"
                            "PACING" -> "الإيقاع"
                            "TOPIC" -> "الموضوعات المفضلة"
                            "AUDIO" -> "الخلفيات الصوتية"
                            else -> category
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(translatedCategory, color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                val maxWeight = list.maxOfOrNull { it.weight }?.toFloat() ?: 1f
                                
                                list.sortedByDescending { it.weight }.take(5).forEach { taste ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(taste.preferredValue, color = TextPrimary, fontFamily = NotoSansFont, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        
                                        // Progress bar relative to max weight
                                        val progress = (taste.weight.toFloat() / maxWeight).coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.weight(1f).height(8.dp),
                                            color = GoldSecondary,
                                            trackColor = DeepSlate
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${taste.weight}", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showRequestDialog) {
        var styleName by remember { mutableStateOf("") }
        var styleDescription by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            containerColor = CardSurface,
            title = {
                Text("اقترح أسلوباً ليتم تغذيته", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "أخبرنا عن أسلوب الإخراج أو مرجع فيديو (مثلاً: أسلوب فيديوهات فلان، أو هادئ ومركز) وسنقوم بهضمه للعقل الاصطناعي.",
                        color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = styleName,
                        onValueChange = { styleName = it },
                        label = { Text("اسم الأسلوب أو المرجع", fontFamily = NotoSansFont) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = styleDescription,
                        onValueChange = { styleDescription = it },
                        label = { Text("تفاصيل الميزات البصرية والحركية المطلوبة", fontFamily = NotoSansFont) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (styleName.isNotBlank()) {
                            isSubmitting = true
                            scope.launch {
                                val userEmail = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE).getString("user_email", "user@example.com") ?: "user@example.com"
                                val req = AppRequestService.AppRequest(
                                    userEmail = userEmail,
                                    title = "طلب أسلوب: $styleName",
                                    description = styleDescription,
                                    goal = "STYLE_REQUEST"
                                )
                                AppRequestService.submitRequest(context, req)
                                isSubmitting = false
                                showRequestDialog = false
                                android.widget.Toast.makeText(context, "تم إرسال طلب الأسلوب بنجاح! سيقوم فريق التطوير بتغذية العقل به.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(20.dp))
                    } else {
                        Text("إرسال الطلب", color = DeepSlate, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRequestDialog = false }) {
                    Text("إلغاء", color = TextSecondary, fontFamily = TajawalFont)
                }
            }
        )
    }

}

