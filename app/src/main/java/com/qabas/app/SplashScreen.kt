package com.qabas.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import kotlinx.coroutines.delay

private val KhadimSplashEmerald = Color(0xFF004E37)
private val KhadimSplashGold = Color(0xFFE3C878)
private val KhadimSplashCream = Color(0xFFF5EFE0)

/**
 * Brand splash for خادم المسلم / Muslim AI Assistant.
 *
 * Shows the lantern splash artwork with a gentle light reveal: the artwork
 * fades in, a soft golden glow pulses around the lantern, and the wordmark
 * fades in below. No audio, no music.
 *
 * The cold-start window background (Theme.Khadim.Splash) already shows the
 * same artwork, so this screen continues it seamlessly and then hands over
 * to the app via [onFinished]. It is an overlay on top of the normal content
 * in MainActivity, so app initialisation keeps running underneath.
 *
 * Duration is ~1.4s; when the user has reduced motion enabled the animation
 * is skipped and the splash dismisses after ~0.4s.
 */
@Composable
fun KhadimSplashScreen(
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    val artworkAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "splashArtworkAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 350),
        label = "splashTextAlpha"
    )
    val glowTransition = rememberInfiniteTransition(label = "splashGlow")
    val glowPulse by glowTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashGlowAlpha"
    )
    val glowAlpha = if (reducedMotion) 0.35f else glowPulse

    LaunchedEffect(Unit) {
        visible = true
        delay(if (reducedMotion) 400L else 1400L)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KhadimSplashEmerald),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_khadim_splash),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(artworkAlpha)
        )

        // Pulsing golden glow centred on the lantern (upper third of the artwork).
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp)
                .size(340.dp)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            KhadimSplashGold.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp)
                .alpha(textAlpha)
        ) {
            Text(
                text = "خادم المسلم",
                color = KhadimSplashGold,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "MUSLIM AI ASSISTANT",
                color = KhadimSplashCream,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
