package com.qabas.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ApiServerSettingsSection() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()
    
    var isServerRunning by remember { mutableStateOf(false) }
    var serverPort by remember { mutableIntStateOf(prefs.getInt("api_server_port", 8080)) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_server_key", generateApiKey()) ?: generateApiKey()) }
    var showInstructions by remember { mutableStateOf(false) }
    
    val server = remember { LocalApiServer(context, apiKey) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🔌 خادم API المحلي",
                color = GoldPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "للربط مع أدوات سطح المكتب والبرمجة",
                color = TextSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { 
                    apiKey = it
                    prefs.edit().putString("api_server_key", it).apply()
                },
                label = { Text("مفتاح API") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = serverPort.toString(),
                onValueChange = { 
                    it.toIntOrNull()?.let { port ->
                        if (port in 1024..65535) {
                            serverPort = port
                            prefs.edit().putInt("api_server_port", port).apply()
                        }
                    }
                },
                label = { Text("المنفذ (1024-65535)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        if (isServerRunning) {
                            server.stop()
                            isServerRunning = false
                        } else {
                            server.start(serverPort)
                            isServerRunning = true
                            showInstructions = true
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServerRunning) 
                        androidx.compose.ui.graphics.Color.Red else GoldPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isServerRunning) "⏹ إيقاف الخادم" else "▶ تشغيل الخادم",
                    color = if (isServerRunning) androidx.compose.ui.graphics.Color.White else DeepSlate,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (isServerRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                val ipAddress = getLocalIpAddress() ?: "localhost"
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "الخادم يعمل على:",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "http://$ipAddress:$serverPort",
                            color = androidx.compose.ui.graphics.Color.Green,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { showInstructions = !showInstructions }
                        ) {
                            Text(
                                if (showInstructions) "إخفاء الأمثلة" else "عرض الأمثلة",
                                color = GoldPrimary
                            )
                        }
                        
                        if (showInstructions) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                """أمثلة الاستخدام:
                                
curl -X POST http://$ipAddress:$serverPort/api/generate-script \
  -H "Authorization: Bearer $apiKey" \
  -H "Content-Type: application/json" \
  -d '{"idea":"فيديو عن الصلاة","styleDescription":"سينمائي"}'""",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 16.sp
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
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
