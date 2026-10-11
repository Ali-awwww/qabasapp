package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedItemsScreen(
    onBack: () -> Unit,
    viewModel: SavedItemsViewModel
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val activeFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    var showAddInspirationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color(0xFF090D16),
        topBar = {
            SavedItemsTopBar(
                onBack = onBack,
                onAddInspiration = { showAddInspirationDialog = true },
                isAuthenticated = uiState !is SavedItemsUiState.Unauthenticated
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF090D16),
                            Color(0xFF0D1527),
                            Color(0xFF090D16)
                        )
                    )
                )
        ) {
            when (val state = uiState) {
                is SavedItemsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF10B981),
                            modifier = Modifier.testTag("saved_items_loading")
                        )
                    }
                }
                is SavedItemsUiState.Unauthenticated -> {
                    UnauthenticatedHudView(
                        onSignIn = { viewModel.signInWithGoogle(context) }
                    )
                }
                is SavedItemsUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = Color(0xFFEF4444),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is SavedItemsUiState.Success -> {
                    SuccessSavedItemsContent(
                        state = state,
                        activeFilter = activeFilter,
                        onFilterSelect = { viewModel.setFilter(it) },
                        onDeleteItem = { viewModel.deleteItem(it) },
                        onSignOut = { viewModel.signOut() },
                        context = context
                    )
                }
            }
        }
    }

    if (showAddInspirationDialog) {
        AddInspirationDialog(
            onDismiss = { showAddInspirationDialog = false },
            onConfirm = { title, content, ref ->
                viewModel.saveInspiration(title, content, ref)
                showAddInspirationDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedItemsTopBar(
    onBack: () -> Unit,
    onAddInspiration: () -> Unit,
    isAuthenticated: Boolean
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF090D16).copy(alpha = 0.95f)
        ),
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("saved_items_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "الرجوع",
                    tint = Color.White
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "المحفوظات السحابية",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Cybernetic HUD status chip
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "مزامنة Firestore",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        },
        actions = {
            if (isAuthenticated) {
                IconButton(
                    onClick = onAddInspiration,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_inspiration_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة خاطرة أو إلهام",
                        tint = Color(0xFF10B981)
                    )
                }
            }
        }
    )
}

@Composable
private fun UnauthenticatedHudView(
    onSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Spatial HUD Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981).copy(alpha = 0.1f))
                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "المزامنة السحابية اللحظية",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "سجل الدخول بحساب Google لحفظ الآيات القرآنية، الخواطر الإيمانية والأدعية مع مزامنة لحظية فورية وتخزين يعمل بدون إنترنت.",
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Google Sign-In Button with Cybernetic Glassmorphic Styling
        Button(
            onClick = onSignIn,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF10B981)
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("google_signin_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "تسجيل الدخول بحساب Google",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SuccessSavedItemsContent(
    state: SavedItemsUiState.Success,
    activeFilter: SavedItemType?,
    onFilterSelect: (SavedItemType?) -> Unit,
    onDeleteItem: (String) -> Unit,
    onSignOut: () -> Unit,
    context: Context
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("saved_items_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile HUD Header
        item {
            UserProfileHudCard(
                profile = state.userProfile,
                onSignOut = onSignOut
            )
        }

        // Stats Summary Telemetry Bar
        item {
            SavedItemsStatsBar(
                ayatCount = state.totalAyatCount,
                inspirationsCount = state.totalInspirationsCount,
                adhkarCount = state.totalAdhkarCount
            )
        }

        // Filter Pills
        item {
            FilterTabsRow(
                activeFilter = activeFilter,
                onSelect = onFilterSelect
            )
        }

        // Empty state or Items List
        if (state.items.isEmpty()) {
            item {
                EmptyItemsHudView(activeFilter = activeFilter)
            }
        } else {
            items(
                items = state.items,
                key = { it.id }
            ) { item ->
                SavedItemCard(
                    item = item,
                    onDelete = { onDeleteItem(item.id) },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Qabas Saved Item", "${item.title}\n\n${item.content}\n${item.reference}")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ النص إلى الحافظة ✦", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun UserProfileHudCard(
    profile: UserProfile?,
    onSignOut: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D33)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFF10B981), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = profile?.displayName ?: "مستخدم قبس",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile?.email ?: "",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("signout_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "تسجيل الخروج",
                    tint = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun SavedItemsStatsBar(
    ayatCount: Int,
    inspirationsCount: Int,
    adhkarCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatPill(
            title = "آيات",
            count = ayatCount,
            color = Color(0xFF10B981),
            modifier = Modifier.weight(1f)
        )
        StatPill(
            title = "إلهامات",
            count = inspirationsCount,
            color = Color(0xFF06B6D4),
            modifier = Modifier.weight(1f)
        )
        StatPill(
            title = "أذكار",
            count = adhkarCount,
            color = Color(0xFFA855F7),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatPill(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1
            )
            Text(
                text = title,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FilterTabsRow(
    activeFilter: SavedItemType?,
    onSelect: (SavedItemType?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = activeFilter == null,
                onClick = { onSelect(null) },
                label = { Text("الكل", softWrap = false, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF10B981),
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF131D33),
                    labelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = activeFilter == SavedItemType.QURAN_AYAH,
                onClick = { onSelect(SavedItemType.QURAN_AYAH) },
                label = { Text("آيات قرآنية", softWrap = false, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF10B981),
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF131D33),
                    labelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = activeFilter == SavedItemType.INSPIRATION,
                onClick = { onSelect(SavedItemType.INSPIRATION) },
                label = { Text("إلهام وخواطر", softWrap = false, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF06B6D4),
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF131D33),
                    labelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = activeFilter == SavedItemType.DHIKR,
                onClick = { onSelect(SavedItemType.DHIKR) },
                label = { Text("أذكار وأدعية", softWrap = false, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFA855F7),
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF131D33),
                    labelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun SavedItemCard(
    item: SavedItem,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val accentColor = when (item.type) {
        SavedItemType.QURAN_AYAH -> Color(0xFF10B981)
        SavedItemType.INSPIRATION -> Color(0xFF06B6D4)
        SavedItemType.DHIKR -> Color(0xFFA855F7)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11192C)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_item_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row: Type Badge + Title + Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge with Anti-Vertical-Squishing rules applied
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.type.labelAr,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("copy_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "نسخ",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content Body
            Text(
                text = item.content,
                color = Color(0xFFE2E8F0),
                fontSize = 15.sp,
                lineHeight = 24.sp
            )

            if (item.reference.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.reference,
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyItemsHudView(activeFilter: SavedItemType?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BookmarkBorder,
            contentDescription = null,
            tint = Color(0xFF475569),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "لا توجد عناصر محفوظة حالياً",
            color = Color(0xFF94A3B8),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "احفظ الآيات من المصحف الشريف أو أضف إلهامك اليومي لتتم مزامنته تلقائياً مع السحابة",
            color = Color(0xFF64748B),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun AddInspirationDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, ref: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131D33),
        title = {
            Text(
                text = "إضافة خاطرة أو إلهام",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("inspiration_title_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("مضمون الخاطرة أو التأمل") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("inspiration_content_input")
                )

                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("المصدر أو الكاتب (اختياري)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("inspiration_ref_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onConfirm(title, content, reference)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.testTag("save_inspiration_confirm_button")
            ) {
                Text("حفظ في السحابة", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color(0xFF94A3B8))
            }
        }
    )
}
