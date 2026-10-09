package com.qabas.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

/**
 * «مركز المال 💰» — دمج ثلاثة أقسام متشابهة في شاشة واحدة:
 * المبيعات والإيرادات + وكالة الأرباح والخدمات + المكافآت والأكواد.
 *
 * نفس نمط مركز الصحة: تبويبات علوية + حفظ آخر تبويب أثناء الجلسة.
 * الأقسام الثلاثة باقية كدوال داخلية — لا حذف كود، فقط غلاف موحد.
 */
enum class MoneyTab(val title: String, val icon: ImageVector) {
    REVENUE("💵 الإيرادات", Icons.Default.AttachMoney),
    AGENCY("🏢 الوكالة", Icons.Default.MonetizationOn),
    PROMO("🎁 الأكواد", Icons.Default.CardGiftcard)
}

object MoneyCenterState {
    var lastTab: Int = 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyCenterSection(
    context: Context,
    initialTab: Int = MoneyCenterState.lastTab
) {
    var selectedTab by remember { mutableStateOf(initialTab.coerceIn(0, MoneyTab.values().size - 1)) }
    val tierState = remember { TierStateManager(context) }
    val tier = tierState.currentTier
    val isPro = tier == UserTier.DEVELOPER || tier == UserTier.BYOK

    LaunchedEffect(Unit) {
        TransactionManager.init(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterHeaderCard(
            badge = if (isPro) "PRO" else "FREE",
            title = "مركز الإيرادات والنمو المالي 💰",
            subtitle = "الاشتراك الحالي: ${tier.title} • البوابة المالية المباشرة والوكالة وتتبع المبيعات",
            accent = if (isPro) Color(0xFF10B981) else GoldPrimary,
            actionLabel = "تحديث",
            onAction = {
                AppSoundManager.playSuccess()
                TransactionManager.loadFromPrefs(context)
                android.widget.Toast.makeText(context, "تم تحديث البيانات المالية والمبيعات سحابياً ✦", android.widget.Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        CenterTabRowWithIcons(
            titles = MoneyTab.values().map { it.title },
            icons = MoneyTab.values().map { it.icon },
            selected = selectedTab,
            onSelect = {
                selectedTab = it
                MoneyCenterState.lastTab = it
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (MoneyTab.values()[selectedTab]) {
                MoneyTab.REVENUE -> RevenueDashboard(context = context)
                MoneyTab.AGENCY -> AgencyMonetizationHub(context = context)
                MoneyTab.PROMO -> PromoCodesSection(context = context)
            }
        }
    }
}
