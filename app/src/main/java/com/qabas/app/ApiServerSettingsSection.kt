package com.qabas.app

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ApiServerSettingsSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isServerRunning by remember { mutableStateOf(false) }
    var serverPort by remember { mutableIntStateOf(AgentPrefs.getInt(context, "api_server_port", 8080)) }
    var apiKey by remember {
        mutableStateOf(
            AgentPrefs.getString(context, "api_server_key", "").ifBlank {
                val created = generateApiKey()
                AgentPrefs.putString(context, "api_server_key", created)
                created
            }
        )
    }
    var showInstructions by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val server = remember { LocalApiServer(context) { AgentPrefs.getString(context, "api_server_key") } }

    DisposableEffect(server) {
        onDispose { server.stop() }
    }

    AppleCard(cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "خادم API المحلي",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "للربط مع أدوات سطح المكتب والسكربتات على الشبكة المحلية",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isServerRunning) theme.success else theme.textSecondary).copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (isServerRunning) "نشط" else "متوقف",
                        color = if (isServerRunning) theme.success else theme.textSecondary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    AgentPrefs.putString(context, "api_server_key", it)
                },
                label = { Text("مفتاح API", fontFamily = CairoFont) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(apiKey))
                        Toast.makeText(context, "تم نسخ مفتاح API", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ المفتاح", tint = theme.primary, modifier = Modifier.size(18.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.primary,
                    unfocusedBorderColor = theme.separator,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = serverPort.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let { port ->
                        if (port in 1024..65535) {
                            serverPort = port
                            AgentPrefs.putInt(context, "api_server_port", port)
                        }
                    }
                },
                label = { Text("المنفذ (1024 - 65535)", fontFamily = CairoFont) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.primary,
                    unfocusedBorderColor = theme.separator,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (isServerRunning) {
                AppleSecondaryButton(
                    text = "إيقاف الخادم",
                    icon = Icons.Default.Stop,
                    tint = theme.destructive,
                    onClick = {
                        scope.launch {
                            serverError = null
                            withContext(Dispatchers.IO) { server.stop() }
                            isServerRunning = false
                        }
                    }
                )
            } else {
                ApplePrimaryButton(
                    text = "تشغيل الخادم",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        scope.launch {
                            serverError = null
                            try {
                                withContext(Dispatchers.IO) { server.start(serverPort) }
                                isServerRunning = true
                                showInstructions = true
                            } catch (e: Exception) {
                                isServerRunning = false
                                serverError = if (e is java.net.BindException) {
                                    "المنفذ $serverPort مستخدم بالفعل. أوقف الخدمة الأخرى أو اختر منفذاً مختلفاً."
                                } else {
                                    "تعذر تشغيل الخادم: " + (e.localizedMessage ?: e.javaClass.simpleName)
                                }
                            }
                        }
                    }
                )
            }

            serverError?.let { message ->
                Text(
                    text = message,
                    color = theme.destructive,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
            }

            if (isServerRunning) {
                val ipAddress = getLocalIpAddress() ?: "127.0.0.1"
                val curlCommand = """curl -X POST http://$ipAddress:$serverPort/api/generate-script \
  -H "Authorization: Bearer $apiKey" \
  -H "Content-Type: application/json" \
  -d '{"idea":"فيديو عن الصلاة","styleDescription":"سينمائي"}'"""

                Surface(
                    color = theme.secondarySurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("عنوان الخادم المحلي:", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = "http://$ipAddress:$serverPort  (health: /api/health)",
                            color = theme.success,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showInstructions = !showInstructions }) {
                                Text(if (showInstructions) "إخفاء المثال" else "عرض مثال الاستخدام", color = theme.primary, fontFamily = CairoFont)
                            }
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(curlCommand))
                                Toast.makeText(context, "تم نسخ أمر curl", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("نسخ curl", color = theme.primary, fontFamily = CairoFont)
                            }
                        }

                        if (showInstructions) {
                            Text(
                                text = curlCommand,
                                color = theme.textSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun generateApiKey(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..32).map { chars.random() }.joinToString("")
}

private fun getLocalIpAddress(): String? {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            val addresses = intf.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
    } catch (_: Exception) {
    }
    return null
}
