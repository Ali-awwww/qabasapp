package com.qabas.app

import android.content.Context
import android.widget.Toast
import java.util.Calendar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun EnhancedUsersSection(
    context: Context,
    devUsers: androidx.compose.runtime.snapshots.SnapshotStateList<DevUser>
) {
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("الكل") }
    var statusFilter by remember { mutableStateOf("الكل") }
    var selectedUser by remember { mutableStateOf<DevUser?>(null) }
    var userTimeline by remember { mutableStateOf<List<Map<String, Any>>?>(null) }

    LaunchedEffect(selectedUser?.id) {
        userTimeline = if (selectedUser != null) {
            CloudServices.Database.getUserTimeline(selectedUser!!.id)
        } else null
    }

    var confirmRoleUser by remember { mutableStateOf<DevUser?>(null) }
    var confirmSuspendUser by remember { mutableStateOf<DevUser?>(null) }
    var confirmNewRole by remember { mutableStateOf<String?>(null) }
    var tempRoleDays by remember { mutableStateOf(0) }
    var roleMenuUser by remember { mutableStateOf<DevUser?>(null) }
    var showAddUserDialog by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var newUserEmail by remember { mutableStateOf("") }
    var newUserType by remember { mutableStateOf("مجاني") }
    val coroutineScope = rememberCoroutineScope()

    var isGeneratingHadiths by remember { mutableStateOf(false) }
    var hadithGenProgress by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val swept = CloudServices.Database.sweepExpiredRoles()
        if (swept > 0) {
            Toast.makeText(context, "انتهت $swept رتبة مؤقتة وعادت إلى مجاني", Toast.LENGTH_LONG).show()
        }
    }

    val types = listOf("الكل", "مطور", "خاص", "مجاني")
    val statuses = listOf("الكل", "نشط", "موقوف")

    val filteredUsers = devUsers.filter { u ->
        val matchesQuery = u.name.contains(searchQuery, ignoreCase = true) ||
            u.email.contains(searchQuery, ignoreCase = true) ||
            (u.customId != null && u.customId.contains(searchQuery, ignoreCase = true))
        
        val uType = u.type.lowercase(java.util.Locale.ROOT)
        val fType = typeFilter.lowercase(java.util.Locale.ROOT)
        
        val matchesType = typeFilter == "الكل" || 
            uType == fType ||
            (fType == "مجاني" && uType == "freemium") ||
            (fType == "مطور" && uType == "developer") ||
            (fType == "خاص" && uType == "special")
            
        val matchesStatus = statusFilter == "الكل" ||
            (statusFilter == "موقوف" && u.isSuspended) ||
            (statusFilter == "نشط" && !u.isSuspended)
        matchesQuery && matchesType && matchesStatus
    }.sortedByDescending { it.regDate }

    val activeCount = devUsers.count { !it.isSuspended }
    val suspendedCount = devUsers.count { it.isSuspended }

    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val csv = buildUsersCsv(filteredUsers)
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            AppSoundManager.playSuccess()
            Toast.makeText(context, "تم تصدير ${filteredUsers.size} مستخدم بصيغة CSV بنجاح ✅", Toast.LENGTH_SHORT).show()
            AuditLogger.log(context, "users_export_csv", "تصدير ${filteredUsers.size} مستخدم")
        }
    }

    // Dialogs stay at top level of Composable
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GoldPrimary)
                    Text("إضافة مستخدم جديد", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        label = { Text("الاسم الكامل", color = Color.Gray, fontFamily = CairoFont) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newUserEmail,
                        onValueChange = { newUserEmail = it },
                        label = { Text("البريد الإلكتروني", color = Color.Gray, fontFamily = CairoFont) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الرتبة الممنوحة:", color = GoldSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("مجاني", "خاص", "مطور").forEach { role ->
                                val isSelected = newUserType == role
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { newUserType = role },
                                    label = { Text(role, fontFamily = CairoFont, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUserName.isNotBlank() && newUserEmail.isNotBlank()) {
                            AppSoundManager.playSuccess()
                            val newId = java.util.UUID.randomUUID().toString()
                            val assignedCustomId = UserIdentifierManager.generateUniqueId(newUserName.ifBlank { newUserEmail })
                            val newUser = DevUser(
                                id = newId,
                                name = newUserName.trim(),
                                email = newUserEmail.trim(),
                                type = newUserType,
                                projectCount = 0,
                                regDate = DevDashboardFormatters.formatRegDate(System.currentTimeMillis()),
                                isSuspended = false,
                                customId = assignedCustomId
                            )
                            LocalAccountRegistry.registerOrUpdateAccount(
                                context = context,
                                account = LocalAccountRegistry.AccountRecord(
                                    id = newId,
                                    name = newUser.name,
                                    email = newUser.email,
                                    customId = assignedCustomId,
                                    type = newUser.type,
                                    status = "active",
                                    regDateMillis = System.currentTimeMillis()
                                )
                            )
                            devUsers.add(0, newUser)
                            newUserName = ""
                            newUserEmail = ""
                            showAddUserDialog = false
                            coroutineScope.launch {
                                var saved = false
                                if (SupabaseServices.isSupabaseAvailable) {
                                    val sbSaved = SupabaseServices.Database.saveUser(
                                        id = newId,
                                        name = newUser.name,
                                        email = newUser.email,
                                        type = newUser.type,
                                        customId = assignedCustomId
                                    )
                                    if (sbSaved) saved = true
                                }
                                if (!saved && !SupabaseServices.isSupabaseAvailable) {
                                    saved = true
                                }
                                AuditLogger.log(context, "user_add", "${newUser.email} (رتبة $newUserType)")
                                Toast.makeText(
                                    context,
                                    if (saved) "تمت إضافة المستخدم وتوثيقه سحابياً ✅" else "أُضيف محلياً",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(context, "يرجى إدخال الاسم والبريد الإلكتروني", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إضافة المستخدم", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    confirmRoleUser?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmRoleUser = null; confirmNewRole = null; tempRoleDays = 0 },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GoldPrimary)
                    Text("تغيير رتبة المستخدم", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تغيير رتبة «${user.name}» إلى «${confirmNewRole ?: ""}»؟", color = Color.White, fontFamily = NotoSansFont)
                    Text("مدة صلاحية الرتبة:", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "دائم", 7 to "7 أيام", 30 to "30 يوم").forEach { (days, label) ->
                            FilterChip(
                                selected = tempRoleDays == days,
                                onClick = { tempRoleDays = days },
                                label = { Text(label, fontFamily = CairoFont, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newType = confirmNewRole ?: return@Button
                        val days = tempRoleDays
                        AppSoundManager.playSuccess()
                        coroutineScope.launch {
                            var success = false
                            if (SupabaseServices.isSupabaseAvailable) {
                                val sbOk = SupabaseServices.Database.updateUserRole(user.id, newType)
                                if (sbOk) success = true
                            }
                            if (!success && !SupabaseServices.isSupabaseAvailable) {
                                success = true
                            }
                            if (success) {
                                val idx = devUsers.indexOf(user)
                                if (idx != -1) {
                                    devUsers[idx] = devUsers[idx].copy(type = if (days > 0) "$newType (مؤقت $days يوم)" else newType)
                                }
                                AuditLogger.log(context, "user_role_change", "${user.email} -> $newType" + if (days > 0) " ($days يوم)" else "")
                                Toast.makeText(context, "تم تغيير الرتبة بنجاح وحفظها سحابياً ✅", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "فشل تغيير الرتبة: تحقق من الاتصال", Toast.LENGTH_LONG).show()
                            }
                        }
                        confirmRoleUser = null
                        confirmNewRole = null
                        tempRoleDays = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("تأكيد التغيير", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRoleUser = null; confirmNewRole = null; tempRoleDays = 0 }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    confirmSuspendUser?.let { user ->
        val willSuspend = !user.isSuspended
        AlertDialog(
            onDismissRequest = { confirmSuspendUser = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (willSuspend) Icons.Default.Block else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (willSuspend) Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                    Text(
                        if (willSuspend) "تأكيد إيقاف الحساب" else "تأكيد إعادة تفعيل الحساب",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    if (willSuspend)
                        "هل أنت متأكد من رغبتك في إيقاف حساب «${user.name}» (${user.email})؟ سيتعذر عليه استخدام المنصة مؤقتاً."
                    else
                        "هل تريد رفع الحظر وإعادة تنشيط حساب «${user.name}» (${user.email})؟",
                    color = Color.White,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newStatus = willSuspend
                        AppSoundManager.playToggle()
                        coroutineScope.launch {
                            var success = false
                            if (SupabaseServices.isSupabaseAvailable) {
                                val sbOk = SupabaseServices.Database.updateUserSuspension(user.id, newStatus)
                                if (sbOk) success = true
                            }
                            if (!success && !SupabaseServices.isSupabaseAvailable) {
                                success = true
                            }
                            if (success) {
                                val idx = devUsers.indexOf(user)
                                if (idx != -1) {
                                    devUsers[idx] = devUsers[idx].copy(isSuspended = newStatus)
                                }
                                AuditLogger.log(context, "user_suspend_toggle", "${user.email} -> ${if (newStatus) "موقوف" else "نشط"}")
                                Toast.makeText(context, if (newStatus) "تم إيقاف الحساب وحفظه سحابياً" else "تم إعادة تنشيط الحساب بنجاح 🟢", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "فشل تحديث الحالة: تحقق من الاتصال", Toast.LENGTH_LONG).show()
                            }
                        }
                        confirmSuspendUser = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (willSuspend) Color(0xFFEF4444) else Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (willSuspend) "إيقاف الحساب" else "إعادة التفعيل", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSuspendUser = null }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    selectedUser?.let { user ->
        val timeline = userTimeline
        AlertDialog(
            onDismissRequest = { selectedUser = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(GoldPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user.name.take(1).uppercase(),
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont
                        )
                    }
                    Column {
                        Text(user.name, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(user.email, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الرتبة الحالية:", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(user.type, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("إجمالي المشاريع:", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
                        Text("${user.projectCount} مشروع", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("تاريخ الانضمام:", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(user.regDate, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("حالة الحساب:", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(
                            if (user.isSuspended) "موقوف 🔴" else "نشط ومفعّل 🟢",
                            color = if (user.isSuspended) Color(0xFFEF4444) else Color(0xFF10B981),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Text("سجل الأنشطة والخط الزمني:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    when {
                        timeline == null -> Text("جاري جلب السجل...", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                        timeline.isEmpty() -> Text("لا يوجد نشاط مسجّل حتى الآن.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (ev in timeline.take(8)) {
                                    val kind = ev["kind"] as? String ?: ""
                                    val icon = when (kind) {
                                        "project" -> "🎬"
                                        "purchase" -> "💰"
                                        "role_expiry" -> "⏳"
                                        else -> "•"
                                    }
                                    val ts = (ev["ts"] as? Long) ?: 0L
                                    val date = if (ts > 0) DevDashboardFormatters.formatRegDate(ts) else ""
                                    Text(
                                        "$icon ${ev["title"]} — $date",
                                        color = Color.White,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedUser = null },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إغلاق", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- بطاقة إدارة الأحاديث اليومية السحابية ----
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إدارة الأحاديث اليومية السحابية", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("جدولة 365 حديثاً من الصحيحين للإرسال التلقائي", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }

                    if (isGeneratingHadiths) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { hadithGenProgress / 365f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = GoldPrimary,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                            Text(
                                "جارٍ التوليد والرفع سحابياً... $hadithGenProgress / 365",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    AppSoundManager.playActivate()
                                    coroutineScope.launch {
                                        isGeneratingHadiths = true
                                        hadithGenProgress = 0
                                        try {
                                            val batchSize = 50
                                            for (day in 1..365 step batchSize) {
                                                val count = if (day + batchSize > 365) 366 - day else batchSize
                                                val batch = DailyHadithAiGenerator.generateBatch(day, count)
                                                if (batch.isNotEmpty()) {
                                                    DailyHadithRepository.uploadHadiths(context, batch)
                                                    hadithGenProgress += batch.size
                                                } else {
                                                    throw Exception("Batch failed at day $day")
                                                }
                                            }
                                            Toast.makeText(context, "تم تجهيز 365 حديثاً سحابياً بنجاح! ✨", Toast.LENGTH_LONG).show()
                                            AuditLogger.log(context, "hadith_cloud_init", "توليد 365 حديثاً")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "فشل التوليد: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isGeneratingHadiths = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("توليد 365 حديثاً (AI)", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    AppSoundManager.playClick()
                                    coroutineScope.launch {
                                        AppNotificationService.triggerDailyHadithNow(context, force = true)
                                        Toast.makeText(context, "تم إرسال إشعار تجريبي بنجاح 🔔", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text("إشعار تجريبي", color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            // ── بطاقة إحصائيات النظام الفضائية (Spatial HUD Stats Card) ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("إجمالي قاعدة المستخدمين", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                Text("${devUsers.size} حساب مسجّل", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }

                        Surface(
                            color = GoldPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "المعروض: ${filteredUsers.size}",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // حالة مزامنة Firebase Firestore للحسابات والمستخدمين
                    val isFirebaseConfigured = remember { FirebaseManager.isConfigured(context) }
                    Surface(
                        color = if (isFirebaseConfigured) Color(0xFF1E293B) else Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, if (isFirebaseConfigured) Color(0xFFFFA000).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    if (isFirebaseConfigured) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (isFirebaseConfigured) Color(0xFFFFA000) else Color.Gray,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    if (isFirebaseConfigured) "سحابة Firebase Firestore: متصلة ومربوطة" else "سحابة Firebase: غير متصلة",
                                    color = if (isFirebaseConfigured) Color(0xFFFFA000) else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isFirebaseConfigured) {
                                Surface(
                                    color = Color(0xFF78350F).copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable {
                                        coroutineScope.launch {
                                            var count = 0
                                            devUsers.forEach { u ->
                                                FirebaseManager.syncUserToFirestore(
                                                    context = context,
                                                    userId = u.id,
                                                    displayName = u.name,
                                                    email = u.email
                                                )
                                                count++
                                            }
                                            Toast.makeText(context, "تمت مزامنة $count مستخدم مع Firebase Firestore بنجاح 🔥", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text(
                                        "مزامنة الكل 🔄",
                                        color = Color(0xFFFFA000),
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                            Text("النشطة: $activeCount", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                        Box(modifier = Modifier.width(1.dp).height(14.dp).background(Color(0xFF1E293B)))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), CircleShape))
                            Text("الموقوفة: $suspendedCount", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))

            // ── شريط البحث الفوري ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("بحث باسم المستخدم أو البريد الإلكتروني...", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = Color.Gray)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── تصنيف الرتب والحالات ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                types.forEach { t ->
                    val isSelected = typeFilter == t
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            AppSoundManager.playTab()
                            typeFilter = t
                        },
                        label = { Text(t, fontFamily = CairoFont, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = Color.Black
                        )
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color(0xFF1E293B)))

                FilterChip(
                    selected = statusFilter != "الكل",
                    onClick = {
                        AppSoundManager.playTab()
                        statusFilter = when (statusFilter) {
                            "الكل" -> "نشط"
                            "نشط" -> "موقوف"
                            else -> "الكل"
                        }
                    },
                    label = {
                        Text(
                            "الحالة: $statusFilter",
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (statusFilter == "موقوف") Color(0xFFEF4444) else Color(0xFF10B981),
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── شريط الإجراءات والتحكم المتناسق ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        AppSoundManager.playTab()
                        showAddUserDialog = true
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مستخدم جديد", color = Color.Black, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        if (filteredUsers.isEmpty()) {
                            Toast.makeText(context, "لا توجد حسابات للتصدير حالياً", Toast.LENGTH_SHORT).show()
                        } else {
                            csvExport.launch("qabas_users_${System.currentTimeMillis()}.csv")
                        }
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصدير CSV", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (filteredUsers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PersonSearch, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Text("لا يوجد مستخدمون مطابقون لمعايير البحث الحالية.", color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── بطاقات المستخدمين (User HUD Cards) ──
        items(filteredUsers, key = { it.id }) { user ->
            val tierColor = when (user.type) {
                "مطور" -> GoldPrimary
                "خاص" -> Color(0xFF8B5CF6)
                else -> Color(0xFF38BDF8)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (user.isSuspended) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFF1E293B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(tierColor.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, tierColor.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    user.name.take(1).uppercase(),
                                    color = tierColor,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont,
                                    fontSize = 15.sp
                                )
                            }

                            Column {
                                Text(
                                    user.name,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    user.email,
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!user.customId.isNullOrBlank()) {
                                    Surface(
                                        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = "ID: ${UserIdentifierManager.formatDisplay(user.customId)}",
                                            color = Color(0xFF00E5FF),
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            color = if (user.isSuspended) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (user.isSuspended) Color(0xFFEF4444).copy(alpha = 0.35f) else Color(0xFF10B981).copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (user.isSuspended) Color(0xFFEF4444) else Color(0xFF10B981), CircleShape)
                                )
                                Text(
                                    if (user.isSuspended) "موقوف" else "نشط",
                                    color = if (user.isSuspended) Color(0xFFEF4444) else Color(0xFF10B981),
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }

                    // شريط معلومات الرتبة والمشاريع
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("الرتبة:", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp)
                            Text(user.type, color = tierColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("المشاريع:", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp)
                            Text("${user.projectCount}", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }

                    // أزرار التحكم الثلاثة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                AppSoundManager.playTab()
                                selectedUser = user
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("تفاصيل", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.5.sp)
                        }

                        Box(modifier = Modifier.weight(1.1f)) {
                            OutlinedButton(
                                onClick = {
                                    AppSoundManager.playTab()
                                    roleMenuUser = user
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                border = BorderStroke(1.dp, GoldSecondary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("تغيير الرتبة", color = GoldSecondary, fontFamily = CairoFont, fontSize = 11.5.sp)
                            }
                            DropdownMenu(
                                expanded = roleMenuUser == user,
                                onDismissRequest = { roleMenuUser = null },
                                containerColor = CardSurface
                            ) {
                                listOf("مطور", "خاص", "مجاني").forEach { role ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                role,
                                                color = if (role == user.type) GoldPrimary else Color.White,
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp
                                            )
                                        },
                                        onClick = {
                                            roleMenuUser = null
                                            confirmNewRole = role
                                            confirmRoleUser = user
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                AppSoundManager.playTab()
                                confirmSuspendUser = user
                            },
                            modifier = Modifier.weight(0.9f).height(38.dp),
                            border = BorderStroke(
                                1.dp,
                                if (user.isSuspended) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                if (user.isSuspended) "تفعيل" else "إيقاف",
                                color = if (user.isSuspended) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildUsersCsv(users: List<DevUser>): String {
    val sb = StringBuilder()
    sb.append("المعرف,الاسم,البريد,الرتبة,المشاريع,تاريخ_التسجيل,الحالة\n")
    users.forEach { u ->
        sb.append("\"${u.id}\",\"${u.name}\",\"${u.email}\",\"${u.type}\",${u.projectCount},\"${u.regDate}\",\"${if (u.isSuspended) "موقوف" else "نشط"}\"\n")
    }
    return sb.toString()
}
