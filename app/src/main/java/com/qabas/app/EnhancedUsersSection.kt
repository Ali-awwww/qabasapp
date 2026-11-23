package com.qabas.app

import android.content.Context
import android.widget.Toast
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
    val theme = QabasTheme.current
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
    var roleMenuUser by remember { mutableStateOf<DevUser?>(null) }
    var updatingUserId by remember { mutableStateOf<String?>(null) }
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


    confirmRoleUser?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmRoleUser = null; confirmNewRole = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PresetPrimary)
                    Text("تغيير رتبة المستخدم", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تغيير رتبة «${user.name}» إلى «${confirmNewRole ?: ""}»؟", color = theme.textPrimary, fontFamily = NotoSansFont)
                    Text("سيتم حفظ الرتبة بشكل دائم عبر صلاحية الإدارة السحابية.", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newType = confirmNewRole ?: return@Button
                        AppSoundManager.playSuccess()
                        updatingUserId = user.id
                        coroutineScope.launch {
                            try {
                                var success = false
                                if (SupabaseServices.isSupabaseAvailable) {
                                    val sbOk = SupabaseServices.Database.updateUserRole(user.id, newType)
                                    if (sbOk) success = true
                                }
                                if (success) {
                                    val idx = devUsers.indexOf(user)
                                    if (idx != -1) {
                                        devUsers[idx] = devUsers[idx].copy(type = newType)
                                    }
                                    if (user.email.equals(SupabaseServices.Auth.getCurrentUserEmail(), ignoreCase = true)) {
                                        AdminGuard.isDashboardAccessAllowed(context)
                                    }
                                    AuditLogger.log(context, "user_role_change", "${user.email} -> $newType")
                                    Toast.makeText(context, "تم ترقية الحساب وحفظ الرتبة ($newType) في السحابة بنجاح 🚀", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "فشل تغيير الرتبة: تحقق من الاتصال بالسحابة", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                updatingUserId = null
                            }
                        }
                        confirmRoleUser = null
                        confirmNewRole = null
                    },
                    enabled = updatingUserId == null,
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (updatingUserId == user.id) "جارٍ الحفظ…" else "تأكيد التغيير", color = theme.background, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRoleUser = null; confirmNewRole = null }) {
                    Text("إلغاء", color = theme.textTertiary, fontFamily = CairoFont)
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
                        tint = if (willSuspend) theme.destructive else theme.success
                    )
                    Text(
                        if (willSuspend) "تأكيد إيقاف الحساب" else "تأكيد إعادة تفعيل الحساب",
                        color = theme.textPrimary,
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
                    color = theme.textPrimary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newStatus = willSuspend
                        AppSoundManager.playToggle()
                        updatingUserId = user.id
                        coroutineScope.launch {
                            try {
                                var success = false
                                if (SupabaseServices.isSupabaseAvailable) {
                                    val sbOk = SupabaseServices.Database.updateUserSuspension(user.id, newStatus)
                                    if (sbOk) success = true
                                }
                                if (success) {
                                    val idx = devUsers.indexOf(user)
                                    if (idx != -1) {
                                        devUsers[idx] = devUsers[idx].copy(isSuspended = newStatus)
                                    }
                                    AuditLogger.log(context, "user_suspend_toggle", "${user.email} -> ${if (newStatus) "موقوف" else "نشط"}")
                                    Toast.makeText(context, if (newStatus) "تم إيقاف الحساب وحفظه سحابياً" else "تم إعادة تنشيط الحساب وحفظه سحابياً 🟢", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "فشل تحديث الحالة: تحقق من الاتصال", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                updatingUserId = null
                            }
                        }
                        confirmSuspendUser = null
                    },
                    enabled = updatingUserId == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (willSuspend) theme.destructive else theme.success
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (updatingUserId == user.id) "جارٍ الحفظ…" else if (willSuspend) "إيقاف الحساب" else "إعادة التفعيل", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSuspendUser = null }) {
                    Text("إلغاء", color = theme.textTertiary, fontFamily = CairoFont)
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
                            .background(PresetPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user.name.take(1).uppercase(),
                            color = PresetPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont
                        )
                    }
                    Column {
                        Text(user.name, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        MaskedEmailText(email = user.email, reason = "عرض مستخدم في لوحة المطور", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = theme.secondarySurface)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الرتبة الحالية:", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(user.type, color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("إجمالي المشاريع:", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 12.sp)
                        Text("${user.projectCount} مشروع", color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("تاريخ الانضمام:", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(user.regDate, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("حالة الحساب:", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(
                            if (user.isSuspended) "موقوف 🔴" else "نشط ومفعّل 🟢",
                            color = if (user.isSuspended) theme.destructive else theme.success,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    HorizontalDivider(color = theme.secondarySurface)
                    Text("سجل الأنشطة والخط الزمني:", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    when {
                        timeline == null -> Text("جاري جلب السجل...", color = theme.textTertiary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        timeline.isEmpty() -> Text("لا يوجد نشاط مسجّل حتى الآن.", color = theme.textTertiary, fontFamily = NotoSansFont, fontSize = 12.sp)
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
                                        color = theme.textPrimary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إغلاق", color = theme.background, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
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
                colors = CardDefaults.cardColors(containerColor = theme.cardSurface.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = PresetPrimary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.3f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إدارة الأحاديث اليومية السحابية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("جدولة 365 حديثاً من الصحيحين للإرسال التلقائي", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }

                    if (isGeneratingHadiths) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { hadithGenProgress / 365f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = PresetPrimary,
                                trackColor = theme.textPrimary.copy(alpha = 0.1f)
                            )
                            Text(
                                "جارٍ التوليد والرفع سحابياً... $hadithGenProgress / 365",
                                color = PresetPrimary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
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
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                            Toast.makeText(context, "فشل التوليد: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isGeneratingHadiths = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.background, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("توليد 365 حديثاً (AI)", color = theme.background, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.4f)),
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
                                    .background(PresetPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("إجمالي قاعدة المستخدمين", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                Text("${devUsers.size} حساب مسجّل", color = theme.textPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }

                        Surface(
                            color = PresetPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "المعروض: ${filteredUsers.size}",
                                color = PresetPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // حالة إدارة ملفات المستخدمين عبر Supabase
                    val isSupabaseConfigured = SupabaseServices.isSupabaseAvailable
                    Surface(
                        color = if (isSupabaseConfigured) theme.secondarySurface else theme.cardSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, if (isSupabaseConfigured) theme.success.copy(alpha = 0.5f) else theme.textTertiary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    if (isSupabaseConfigured) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (isSupabaseConfigured) theme.success else theme.textTertiary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    if (isSupabaseConfigured) "سحابة Supabase: متصلة ومربوطة" else "سحابة Supabase: غير متصلة",
                                    color = if (isSupabaseConfigured) theme.success else theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isSupabaseConfigured) {
                                Surface(
                                    color = theme.warning.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable {
                                        Toast.makeText(
                                            context,
                                            "ملفات المستخدمين تُدار في Supabase ولا تحتاج إلى مزامنة Firestore منفصلة",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Text(
                                        "الملفات على Supabase",
                                        color = theme.success,
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = theme.secondarySurface)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(theme.success, CircleShape))
                            Text("النشطة: $activeCount", color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                        Box(modifier = Modifier.width(1.dp).height(14.dp).background(theme.secondarySurface))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(theme.destructive, CircleShape))
                            Text("الموقوفة: $suspendedCount", color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
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
                placeholder = { Text("بحث باسم المستخدم أو البريد الإلكتروني...", color = theme.textTertiary, fontFamily = NotoSansFont, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PresetPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = theme.textTertiary)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PresetPrimary,
                    unfocusedBorderColor = theme.secondarySurface,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary
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
                            selectedContainerColor = PresetPrimary,
                            selectedLabelColor = theme.background
                        )
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(28.dp).background(theme.secondarySurface))

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
                        selectedContainerColor = if (statusFilter == "موقوف") theme.destructive else theme.success,
                        selectedLabelColor = theme.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── شريط الإجراءات والتحكم المتناسق ──
            Button(
                onClick = {
                    if (filteredUsers.isEmpty()) {
                        Toast.makeText(context, "لا توجد حسابات للتصدير حالياً", Toast.LENGTH_SHORT).show()
                    } else {
                        csvExport.launch("qabas_users_${System.currentTimeMillis()}.csv")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = theme.background, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تصدير سجل المستخدمين (CSV)", color = theme.background, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (filteredUsers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, theme.secondarySurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PersonSearch, contentDescription = null, tint = theme.textTertiary, modifier = Modifier.size(36.dp))
                        Text("لا يوجد مستخدمون مطابقون لمعايير البحث الحالية.", color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── بطاقات المستخدمين (User HUD Cards) ──
        items(filteredUsers, key = { it.id }) { user ->
            val tierColor = when (user.type) {
                "مطور" -> PresetPrimary
                "خاص" -> theme.accent
                else -> theme.primary
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (user.isSuspended) theme.destructive.copy(alpha = 0.5f) else theme.secondarySurface
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
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                MaskedEmailText(
                                    email = user.email,
                                    reason = "عرض مستخدم في لوحة المطور",
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp
                                )
                                if (!user.customId.isNullOrBlank()) {
                                    Surface(
                                        color = theme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.3f)),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = "ID: ${UserIdentifierManager.formatDisplay(user.customId)}",
                                            color = theme.primary,
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
                            color = if (user.isSuspended) theme.destructive.copy(alpha = 0.15f) else theme.success.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (user.isSuspended) theme.destructive.copy(alpha = 0.35f) else theme.success.copy(alpha = 0.35f)
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
                                        .background(if (user.isSuspended) theme.destructive else theme.success, CircleShape)
                                )
                                Text(
                                    if (user.isSuspended) "موقوف" else "نشط",
                                    color = if (user.isSuspended) theme.destructive else theme.success,
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
                            .background(theme.cardSurface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("الرتبة:", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 11.sp)
                            Text(user.type, color = tierColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("المشاريع:", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 11.sp)
                            Text("${user.projectCount}", color = theme.textPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
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
                            border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("تفاصيل", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.5.sp)
                        }

                        Box(modifier = Modifier.weight(1.1f)) {
                            OutlinedButton(
                                onClick = {
                                    AppSoundManager.playTab()
                                    roleMenuUser = user
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                border = BorderStroke(1.dp, PresetAccent.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("تغيير الرتبة", color = PresetAccent, fontFamily = CairoFont, fontSize = 11.5.sp)
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
                                                color = if (role == user.type) PresetPrimary else theme.textPrimary,
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
                                if (user.isSuspended) theme.success.copy(alpha = 0.5f) else theme.destructive.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                if (user.isSuspended) "تفعيل" else "إيقاف",
                                color = if (user.isSuspended) theme.success else theme.destructive,
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
        sb.append("\"${u.id}\",\"${u.name}\",\"${maskEmail(u.email)}\",\"${u.type}\",${u.projectCount},\"${u.regDate}\",\"${if (u.isSuspended) "موقوف" else "نشط"}\"\n")
    }
    return sb.toString()
}
