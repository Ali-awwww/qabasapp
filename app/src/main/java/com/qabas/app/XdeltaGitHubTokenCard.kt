package com.qabas.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import kotlinx.coroutines.launch

@Composable
fun XdeltaGitHubTokenCard() {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var loadingStatus by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadingStatus = true
        configured = runCatching { SupabaseServices.Database.getGitHubTokenConfigured() }.getOrNull()
        loadingStatus = false
    }

    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF0D1527).copy(alpha = 0.92f),
        borderColor = Color(0xFF10B981).copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF34D399))
                Text(
                    text = "خزنة GitHub السحابية",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Text(
                text = "أدخل توكن GitHub هنا. يُرسل إلى الخادم مباشرة ويُحفظ في app_secrets؛ لا يُخزّن على الجهاز ولا يُعاد عرضه بعد الحفظ.",
                color = Color(0xFFCBD5E1),
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; message = null },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                singleLine = true,
                label = { Text("GitHub Personal Access Token") },
                placeholder = { Text("ghp_… أو github_pat_…") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedLabelColor = Color(0xFF00E5FF),
                    unfocusedLabelColor = Color(0xFF94A3B8),
                    cursorColor = Color(0xFF00E5FF)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (loadingStatus) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp, color = Color(0xFF00E5FF))
                    } else if (configured == true) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399))
                    }
                    Text(
                        text = when {
                            loadingStatus -> "جارٍ فحص حالة الخزنة…"
                            configured == true -> "يوجد توكن نشط في الخادم"
                            configured == false -> "لم يُضبط توكن بعد"
                            else -> "تعذر قراءة حالة الخزنة"
                        },
                        color = if (configured == true) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }
                Button(
                    enabled = token.isNotBlank() && !saving && !loadingStatus,
                    onClick = {
                        val submitted = token
                        saving = true
                        message = null
                        scope.launch {
                            runCatching { SupabaseServices.Database.saveGitHubToken(submitted) }
                                .onSuccess { saved ->
                                    configured = saved
                                    if (saved) {
                                        token = ""
                                        message = "تم التحقق من التوكن وحفظه في الخادم."
                                    } else {
                                        message = "لم يؤكد الخادم حفظ التوكن."
                                    }
                                }
                                .onFailure {
                                    message = "تعذر الحفظ. تحقق من صلاحيات التوكن واتصال الخادم."
                                }
                            saving = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (saving) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                    Text("حفظ في السحابة", modifier = Modifier.padding(start = 6.dp), fontFamily = CairoFont)
                }
            }
            message?.let {
                Text(it, color = if (configured == true) Color(0xFF34D399) else Color(0xFFFBBF24), fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            Text(
                text = "الصلاحيات المطلوبة على المستودع: Actions (Read and write)، وContents (Read). لا تُرسل التوكن عبر الدردشة أو البلاغات.",
                color = Color(0xFF94A3B8),
                fontFamily = NotoSansFont,
                fontSize = 11.sp
            )
        }
    }
}
