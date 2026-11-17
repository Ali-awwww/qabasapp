package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedItemsScreen(
    onBack: () -> Unit,
    viewModel: SavedItemsViewModel
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = QabasTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val activeFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    var showAddInspirationDialog by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<SavedItem?>(null) }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = theme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            AppleTopBar(
                title = "المحفوظات السحابية",
                subtitle = "مزامنة Supabase الآمنة",
                onBack = onBack,
                backTestTag = "saved_items_back_button",
                actions = {
                    if (uiState is SavedItemsUiState.Success) {
                        IconButton(
                            onClick = { showAddInspirationDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("add_inspiration_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "إضافة خاطرة أو إلهام",
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(theme.background)
        ) {
            when (val state = uiState) {
                is SavedItemsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = theme.primary,
                            modifier = Modifier.testTag("saved_items_loading")
                        )
                    }
                }
                is SavedItemsUiState.Unauthenticated -> {
                    UnauthenticatedAppleView(
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
                        AppleCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = theme.destructive,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = state.message,
                                    color = theme.destructive,
                                    fontFamily = NotoSansFont,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                is SavedItemsUiState.Success -> {
                    SuccessSavedItemsContent(
                        state = state,
                        activeFilter = activeFilter,
                        onFilterSelect = { viewModel.setFilter(it) },
                        onDeleteItem = { itemId ->
                            pendingDeleteItem = state.items.firstOrNull { it.id == itemId }
                        },
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

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    text = "تأكيد حذف المحفوظ",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "هل تريد حذف «${item.title}»؟ لا يمكن التراجع عن هذا الإجراء.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteItem = null
                        viewModel.deleteItem(item.id)
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_delete_saved_item")
                ) {
                    Text(
                        text = "حذف نهائي",
                        color = theme.destructive,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteItem = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "إلغاء",
                        color = theme.textSecondary,
                        fontFamily = CairoFont
                    )
                }
            }
        )
    }
}

@Composable
private fun UnauthenticatedAppleView(
    onSignIn: () -> Unit
) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(theme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "المزامنة السحابية اللحظية",
            color = theme.textPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "سجّل الدخول بحساب Google لحفظ الآيات القرآنية والخواطر الإيمانية والأدعية مع مزامنة فورية وتخزين آمن.",
            color = theme.textSecondary,
            fontFamily = NotoSansFont,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        ApplePrimaryButton(
            text = "تسجيل الدخول بحساب Google",
            icon = Icons.Default.AccountCircle,
            onClick = onSignIn,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .testTag("google_signin_button")
        )
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            UserProfileAppleCard(
                profile = state.userProfile,
                onSignOut = onSignOut
            )
        }

        item {
            SavedItemsStatsBar(
                ayatCount = state.totalAyatCount,
                inspirationsCount = state.totalInspirationsCount,
                adhkarCount = state.totalAdhkarCount
            )
        }

        item {
            FilterTabsRow(
                activeFilter = activeFilter,
                onSelect = onFilterSelect
            )
        }

        if (state.items.isEmpty()) {
            item {
                EmptyItemsAppleView()
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
                        val clip = ClipData.newPlainText(
                            "Qabas Saved Item",
                            "${item.title}\n\n${item.content}\n${item.reference}"
                        )
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ النص إلى الحافظة", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UserProfileAppleCard(
    profile: UserProfile?,
    onSignOut: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(theme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.displayName ?: "مستخدم قبس",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!profile?.email.isNullOrBlank()) {
                    Text(
                        text = profile?.email ?: "",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("signout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "تسجيل الخروج",
                    tint = theme.destructive
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
    val theme = QabasTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatPill(
            title = "آيات",
            count = ayatCount,
            color = theme.success,
            modifier = Modifier.weight(1f)
        )
        StatPill(
            title = "إلهامات",
            count = inspirationsCount,
            color = theme.primary,
            modifier = Modifier.weight(1f)
        )
        StatPill(
            title = "أذكار",
            count = adhkarCount,
            color = theme.accent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatPill(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = modifier,
        cornerRadius = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                color = color,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp,
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
    val theme = QabasTheme.current
    val filters = listOf(
        null to "الكل",
        SavedItemType.QURAN_AYAH to "آيات قرآنية",
        SavedItemType.INSPIRATION to "إلهام وخواطر",
        SavedItemType.DHIKR to "أذكار وأدعية"
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(filters) { (type, label) ->
            val selected = activeFilter == type
            val chipTint = when (type) {
                SavedItemType.QURAN_AYAH -> theme.success
                SavedItemType.INSPIRATION -> theme.primary
                SavedItemType.DHIKR -> theme.accent
                null -> theme.primary
            }
            FilterChip(
                selected = selected,
                onClick = { onSelect(type) },
                label = {
                    Text(
                        text = label,
                        fontFamily = CairoFont,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        softWrap = false,
                        maxLines = 1
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipTint,
                    selectedLabelColor = theme.onPrimary,
                    containerColor = theme.cardSurface,
                    labelColor = theme.textPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = theme.separator.copy(alpha = 0.5f),
                    selectedBorderColor = chipTint
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
    val theme = QabasTheme.current
    val accentColor = when (item.type) {
        SavedItemType.QURAN_AYAH -> theme.success
        SavedItemType.INSPIRATION -> theme.primary
        SavedItemType.DHIKR -> theme.accent
    }

    AppleCard(
        modifier = Modifier.testTag("saved_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = if (theme.isDark) 0.18f else 0.12f)
                ) {
                    Text(
                        text = item.type.labelAr,
                        color = accentColor,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = item.title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("copy_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "نسخ",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف",
                        tint = theme.destructive,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.content,
                color = theme.textPrimary,
                fontFamily = NotoSansFont,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )

            if (item.reference.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.reference,
                        color = accentColor,
                        fontFamily = CairoFont,
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
private fun EmptyItemsAppleView() {
    val theme = QabasTheme.current
    AppleCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = theme.textSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "لا توجد عناصر محفوظة حالياً",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "احفظ الآيات من المصحف الشريف أو أضف إلهامك اليومي لتتم مزامنته تلقائياً مع السحابة.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddInspirationDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, ref: String) -> Unit
) {
    val theme = QabasTheme.current
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = "إضافة خاطرة أو إلهام",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
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
                    label = { Text("العنوان", fontFamily = CairoFont) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.separator
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspiration_title_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("مضمون الخاطرة أو التأمل", fontFamily = CairoFont) },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.separator
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspiration_content_input")
                )

                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("المصدر أو الكاتب (اختياري)", fontFamily = CairoFont) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.separator
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspiration_ref_input")
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("save_inspiration_confirm_button")
            ) {
                Text(
                    text = "حفظ في السحابة",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
            }
        }
    )
}
