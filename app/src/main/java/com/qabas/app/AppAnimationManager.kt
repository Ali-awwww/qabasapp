package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * أنماط أنميشن وحركات التنقل المتاحة في التطبيق (App Transition Animation Styles)
 */
enum class AppTransitionAnimation(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val badge: String,
    val isFuturistic: Boolean = false
) {
    SPATIAL_WARP(
        id = "spatial_warp",
        title = "الانتقال الكوانتي الفضائي (Spatial Warp)",
        subtitle = "انبثاق هولوغرافي وتكبير ثلاثي الأبعاد مع تلاشٍ ضوئي فضائي سلس",
        iconEmoji = "🛸",
        badge = "الافتراضي الفضائي ✦",
        isFuturistic = true
    ),
    FLUID_SLIDE(
        id = "fluid_slide",
        title = "الانزلاق الحركي السلس (Fluid Shared-Axis)",
        subtitle = "انزلاق اتجاهي متناسق مع عمق فيزيائي وتلاشٍ متناغم للمسارات",
        iconEmoji = "🌊",
        badge = "انسيابي متطور"
    ),
    CINEMATIC_FADE(
        id = "cinematic_fade",
        title = "التلاشي السينمائي الناعم (Cinematic Crossfade)",
        subtitle = "تلاشٍ ناعم مهدئ مع تنفس بصري لطيف ومريح للعينين",
        iconEmoji = "🎬",
        badge = "سينمائي هادئ"
    ),
    VERTICAL_ELEVATION(
        id = "vertical_elevation",
        title = "الارتقاء والانبثاق الرأسي (Vertical Elevation)",
        subtitle = "صعود هادئ للشاشات مع ارتقاء طبقي وبطاقات عائمة",
        iconEmoji = "🚀",
        badge = "بطاقات حديثة"
    ),
    CYBER_PULSE(
        id = "cyber_pulse",
        title = "النبض الكهرومغناطيسي السيبراني (Cyber Pulse)",
        subtitle = "نبضة سيبرانية ارتدادية مع منحنى تسارع كوانتي نيون",
        iconEmoji = "⚡",
        badge = "سيبراني تفاعلي",
        isFuturistic = true
    ),
    FAST_MINIMAL(
        id = "fast_minimal",
        title = "الانتقال الفوري فائق السرعة (Instant Minimal)",
        subtitle = "استجابة فورية فائقة الأداء مع وميض ميكرو فائق السرعة",
        iconEmoji = "⚡",
        badge = "أقصى سرعة"
    );

    companion object {
        fun fromId(id: String?): AppTransitionAnimation =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: SPATIAL_WARP
    }
}

/**
 * سرعة وتوقيت الأنميشن (Animation Speed Factor)
 */
enum class AnimationSpeedOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val durationMs: Int,
    val badge: String
) {
    FAST(
        id = "fast",
        title = "فائق السرعة (Fast)",
        subtitle = "استجابة خاطفة وسريعة جداً للمستخدمين المتقدمين",
        durationMs = 180,
        badge = "180ms"
    ),
    NORMAL(
        id = "normal",
        title = "متوازن وطبيعي (Balanced)",
        subtitle = "التوازن الأمثل بين الانسيابية والجمالية والأداء",
        durationMs = 280,
        badge = "280ms (افتراضي)"
    ),
    CINEMATIC(
        id = "cinematic",
        title = "سينمائي بطيء (Cinematic Slow)",
        subtitle = "حركات بطيئة انسيابية تبرز التفاصيل البصرية والهولوجرام",
        durationMs = 450,
        badge = "450ms"
    );

    companion object {
        fun fromId(id: String?): AnimationSpeedOption =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: NORMAL
    }
}

/**
 * مدير أنميشن وحركات التنقل المركزي في التطبيق
 */
object AppAnimationManager {
    private const val PREF_ANIM_KEY = "app_navigation_animation_style"
    private const val PREF_SPEED_KEY = "app_navigation_animation_speed"

    private val _currentAnimation = MutableStateFlow(AppTransitionAnimation.SPATIAL_WARP)
    val currentAnimation: StateFlow<AppTransitionAnimation> = _currentAnimation.asStateFlow()

    private val _currentSpeed = MutableStateFlow(AnimationSpeedOption.NORMAL)
    val currentSpeed: StateFlow<AnimationSpeedOption> = _currentSpeed.asStateFlow()

    var activeAnimation by mutableStateOf(AppTransitionAnimation.SPATIAL_WARP)
        private set

    var activeSpeed by mutableStateOf(AnimationSpeedOption.NORMAL)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val savedAnimId = prefs.getString(PREF_ANIM_KEY, AppTransitionAnimation.SPATIAL_WARP.id)
        val savedSpeedId = prefs.getString(PREF_SPEED_KEY, AnimationSpeedOption.NORMAL.id)

        val anim = AppTransitionAnimation.fromId(savedAnimId)
        val speed = AnimationSpeedOption.fromId(savedSpeedId)

        _currentAnimation.value = anim
        activeAnimation = anim

        _currentSpeed.value = speed
        activeSpeed = speed
    }

    fun setAnimation(context: Context, anim: AppTransitionAnimation) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_ANIM_KEY, anim.id).apply()
        _currentAnimation.value = anim
        activeAnimation = anim
    }

    fun setSpeed(context: Context, speed: AnimationSpeedOption) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_SPEED_KEY, speed.id).apply()
        _currentSpeed.value = speed
        activeSpeed = speed
    }

    /**
     * بناء كائن التحول الحركي (ContentTransform) بناءً على النمط والسرعة المختارة
     */
    fun createTransitionSpec(
        initialState: AppState,
        targetState: AppState,
        creationStepsOrder: List<AppState> = emptyList()
    ): ContentTransform {
        val animStyle = _currentAnimation.value
        val duration = _currentSpeed.value.durationMs

        val initialIndex = creationStepsOrder.indexOf(initialState)
        val targetIndex = creationStepsOrder.indexOf(targetState)
        val isStepForward = if (initialIndex != -1 && targetIndex != -1) targetIndex > initialIndex else true

        return when (animStyle) {
            AppTransitionAnimation.SPATIAL_WARP -> {
                val enterScale = if (isStepForward) 0.90f else 1.08f
                val exitScale = if (isStepForward) 1.08f else 0.90f
                (scaleIn(
                    initialScale = enterScale,
                    animationSpec = tween(duration, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(duration)))
                    .togetherWith(
                        scaleOut(
                            targetScale = exitScale,
                            animationSpec = tween((duration * 0.85f).toInt(), easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween((duration * 0.75f).toInt()))
                    ).apply { targetContentZIndex = 1f }
            }

            AppTransitionAnimation.FLUID_SLIDE -> {
                val factor = if (isStepForward) 4 else -4
                (slideInHorizontally(
                    initialOffsetX = { it / factor },
                    animationSpec = tween(duration, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(duration)))
                    .togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { -it / factor },
                            animationSpec = tween((duration * 0.85f).toInt(), easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween((duration * 0.75f).toInt()))
                    ).apply { targetContentZIndex = 1f }
            }

            AppTransitionAnimation.CINEMATIC_FADE -> {
                (fadeIn(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(duration, easing = FastOutSlowInEasing)))
                    .togetherWith(
                        fadeOut(animationSpec = tween((duration * 0.85f).toInt(), easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween((duration * 0.85f).toInt(), easing = FastOutSlowInEasing))
                    ).apply { targetContentZIndex = 1f }
            }

            AppTransitionAnimation.VERTICAL_ELEVATION -> {
                val factor = if (isStepForward) 5 else -5
                (slideInVertically(
                    initialOffsetY = { it / factor },
                    animationSpec = tween(duration, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(duration)))
                    .togetherWith(
                        slideOutVertically(
                            targetOffsetY = { -it / factor },
                            animationSpec = tween((duration * 0.85f).toInt(), easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween((duration * 0.75f).toInt()))
                    ).apply { targetContentZIndex = 1f }
            }

            AppTransitionAnimation.CYBER_PULSE -> {
                (scaleIn(
                    initialScale = 0.84f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(animationSpec = tween((duration * 0.7f).toInt())))
                    .togetherWith(
                        scaleOut(
                            targetScale = 0.92f,
                            animationSpec = tween((duration * 0.6f).toInt(), easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween((duration * 0.5f).toInt()))
                    ).apply { targetContentZIndex = 1f }
            }

            AppTransitionAnimation.FAST_MINIMAL -> {
                fadeIn(animationSpec = tween(90, easing = LinearEasing))
                    .togetherWith(fadeOut(animationSpec = tween(70, easing = LinearEasing)))
                    .apply { targetContentZIndex = 1f }
            }
        }
    }
}
