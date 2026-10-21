package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.TestTags
import androidx.compose.ui.platform.testTag

@Composable
fun QabasApp() {
    val context = LocalContext.current
    
    // Initialize Navigation Animation, Style and Sound managers
    LaunchedEffect(Unit) {
        AppAnimationManager.init(context)
        HomeScreenStyleManager.init(context)
        AppSoundManager.init(context)
    }

    DisposableEffect(Unit) {
        val server = LocalApiServer(context)
        server.start(8080)
        onDispose {
            server.stop()
            AudioPlayerManager.stopAudio()
        }
    }

    val viewModel: ProjectViewModel = viewModel(factory = ProjectViewModelFactory(context))
    val state by viewModel.state.collectAsState()

    // Observe active animation style & speed for real-time reactivity
    val activeAnimation by AppAnimationManager.currentAnimation.collectAsState()
    val activeSpeed by AppAnimationManager.currentSpeed.collectAsState()

    val bottomNav = @Composable {
        QabasBottomNavigation(
            modifier = Modifier.testTag("bottom_navigation"),
            currentRoute = state.appState,
            onNavigate = { newState ->
                if (newState != state.appState) {
                    AppSoundManager.playTab()
                }
                viewModel.updateState { copy(appState = newState) }
            }
        )
    }

    // Trigger transition sound effect when switching between main states
    var previousAppState by remember { mutableStateOf<AppState?>(null) }
    LaunchedEffect(state.appState) {
        if (previousAppState != null && previousAppState != state.appState) {
            AppSoundManager.playAnimationSound()
        }
        previousAppState = state.appState
    }

    // وجهة التنقّل المطلوبة من الإشعارات (navigate_to) — تُستهلك مرة واحدة.
    // تُحتفظ حتى يجهز التطبيق (بعد التهيئة/الدخول) ثم تُطبّق، حتى مع الإقلاع البارد.
    val authFunnelStates = remember {
        setOf(
            AppState.DATA_LOADING, AppState.ONBOARDING, AppState.LOGIN,
            AppState.REGISTER, AppState.FORGOT_PASSWORD
        )
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(
            AppNavigator.notificationDestination,
            viewModel.state
        ) { destination, currentState -> destination to currentState.appState }
            .collect { (destination, currentAppState) ->
                if (destination == null) return@collect
                if (currentAppState in authFunnelStates) return@collect // تُحفظ حتى الجاهزية
                viewModel.updateState { copy(appState = destination) }
                AppNavigator.consumeNotificationDestination()
            }
    }

    // Observe global navigator events (e.g. from Spatial HUD Lens Camera)
    LaunchedEffect(Unit) {
        AppNavigator.targetAppState.collect { newTarget ->
            if (newTarget != state.appState) {
                AppSoundManager.playTab()
                viewModel.updateState { copy(appState = newTarget) }
            }
        }
    }

    LaunchedEffect(Unit) {
        AppNavigator.pendingInputText.collect { text ->
            viewModel.updateState { copy(inputText = text, appState = AppState.INPUT) }
        }
    }

    val creationStepsOrder = remember {
        listOf(
            AppState.HOME,
            AppState.INPUT,
            AppState.UNDERSTANDING,
            AppState.PROCESSING,
            AppState.REVIEW,
            AppState.PAYMENT,
            AppState.ADVANCED_EDIT,
            AppState.SAVE_SHARE
        )
    }

    AnimatedContent(
        targetState = state.appState,
        transitionSpec = {
            AppAnimationManager.createTransitionSpec(
                initialState = initialState,
                targetState = targetState,
                creationStepsOrder = creationStepsOrder
            )
        },
        label = "QabasRootScreenTransition_${activeAnimation.id}_${activeSpeed.id}"
    ) { targetAppState ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSlate)
        ) {
            AppNavigation(
                state = state.copy(appState = targetAppState),
                viewModel = viewModel,
                context = context,
                bottomNav = bottomNav
            )
        }
    }
}

@Composable
fun WelcomeRulesDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DeepSlate)
                .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.verticalGradient(colors = listOf(GoldPrimary.copy(alpha = 0.15f), Color.Transparent)))
            )

            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(CardSurface)
                        .border(1.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(32.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = Translator.tr("ضوابط استوديو قبس"),
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = Translator.tr("مرحباً بك في استوديو قبس. نلتزم معاً بصناعة محتوى راقٍ ونقي، وفق الضوابط التالية:"),
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = NotoSansFont,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                val rules = listOf(
                    Translator.tr("لا تُستخدم الموسيقى، نعتمد على المؤثرات الصوتية المباحة فقط."),
                    Translator.tr("يمنع تماماً أي محتوى خادش للحياء أو ألفاظ بذيئة."),
                    Translator.tr("يمنع الإساءة للإسلام أو لأي من الرموز الدينية."),
                    Translator.tr("الصور المولدة تُطمس وجوهها للحفاظ على الضوابط، بينما الحقيقية تبقى كما هي.")
                )

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    rules.forEachIndexed { index, rule ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(rule, color = Color.White.copy(alpha = 0.8f), fontFamily = NotoSansFont, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(GoldSecondary, GoldPrimary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Translator.tr("أتعهد بالالتزام"),
                            color = DeepSlate,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
