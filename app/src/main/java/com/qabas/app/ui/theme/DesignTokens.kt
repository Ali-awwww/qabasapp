package com.qabas.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * قياسات نظام تصميم مساعد المسلم.
 * القيم مركزية حتى لا تنشئ كل شاشة إيقاعًا بصريًا مختلفًا.
 */
@Immutable
data class QabasSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp
)

@Immutable
data class QabasShapeTokenValues(
    val compact: RoundedCornerShape = RoundedCornerShape(8.dp),
    val control: RoundedCornerShape = RoundedCornerShape(12.dp),
    val card: RoundedCornerShape = RoundedCornerShape(16.dp),
    val prominent: RoundedCornerShape = RoundedCornerShape(20.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(999.dp)
)

@Immutable
data class QabasElevationTokenValues(
    val flat: Dp = 0.dp,
    val raised: Dp = 2.dp,
    val prominent: Dp = 6.dp
)

val QabasSpacingTokens = QabasSpacing()
val QabasShapeTokens = QabasShapeTokenValues()
val QabasElevationTokens = QabasElevationTokenValues()

/** Material 3 shapes المرتبطة بقياسات النظام الجديد. */
val QabasMaterialShapes = Shapes(
    extraSmall = QabasShapeTokens.compact,
    small = QabasShapeTokens.control,
    medium = QabasShapeTokens.card,
    large = QabasShapeTokens.prominent,
    extraLarge = RoundedCornerShape(28.dp)
)
