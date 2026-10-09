package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIdeaFormScreen(onBack: () -> Unit, onSubmitSuccess: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val goldGradient = Brush.horizontalGradient(colors = listOf(GoldSecondary, GoldPrimary))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.tr("طلب تطبيق مخصص"), fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        },
        containerColor = DeepSlate
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                Translator.tr("هل لديك فكرة تطبيق وتحلم بتحقيقها؟\nأخبرنا بالتفاصيل وسيقوم مطور قبس بتحويلها لواقع!"),
                color = Color.LightGray,
                fontFamily = CairoFont,
                fontSize = 16.sp
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(Translator.tr("اسم التطبيق المقترح"), fontFamily = CairoFont) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedLabelColor = GoldPrimary,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                label = { Text(Translator.tr("ما هو الهدف الرئيسي للتطبيق؟"), fontFamily = CairoFont) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedLabelColor = GoldPrimary,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(Translator.tr("شرح مفصل للميزات المطلوبة"), fontFamily = CairoFont) },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedLabelColor = GoldPrimary,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (title.isBlank() || goal.isBlank() || description.isBlank()) {
                        Toast.makeText(context, Translator.tr("يرجى ملء جميع الحقول"), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val request = AppRequestService.AppRequest(
                        userEmail = context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE).getString("user_email", "user@example.com") ?: "user@example.com",
                        title = title,
                        goal = goal,
                        description = description
                    )
                    AppRequestService.submitRequest(context, request)
                    Toast.makeText(context, Translator.tr("تم إرسال طلبك بنجاح!"), Toast.LENGTH_SHORT).show()
                    onSubmitSuccess()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(goldGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Translator.tr("إرسال الطلب للمطور"),
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
