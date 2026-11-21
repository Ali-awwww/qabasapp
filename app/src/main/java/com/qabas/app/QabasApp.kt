package com.qabas.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qabas.app.ui.theme.*

@Composable
fun QabasApp() {
    val context = LocalContext.current
    val currentUser by SupabaseServices.Auth.currentUser.collectAsState()

    LaunchedEffect(Unit) {
        AppAnimationManager.init(context)
        HomeScreenStyleManager.init(context)
        AppSoundManager.init(context)
    }

    LaunchedEffect(currentUser?.id) {
        if (currentUser?.id != null) {
            PushTokenManager.syncPending(context)
            DevChatRealtimeManager.start(
                context = context,
                userId = currentUser!!.id,
                userEmail = currentUser!!.email.orEmpty()
            )
        } else {
            DevChatRealtimeManager.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AudioPlayerManager.stopAudio()
        }
    }

    val viewModel: ProjectViewModel = viewModel(factory = ProjectViewModelFactory(context))
    val state by viewModel.state.collectAsState()

    val activeAnimation by AppAnimationManager.currentAnimation.collectAsState()
    val activeSpeed by AppAnimationManager.currentSpeed.collectAsState()
    val reducedMotion by UserPreferencesManager.reducedMotion.collectAsState()

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

    var previousAppState by remember { mutableStateOf<AppState?>(null) }
    LaunchedEffect(state.appState) {
        if (previousAppState != null && previousAppState != state.appState) {
            AppSoundManager.playAnimationSound()
        }
        previousAppState = state.appState
    }

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
                if (currentAppState in authFunnelStates && destination != AppState.FORGOT_PASSWORD) {
                    return@collect
                }
                viewModel.updateState { copy(appState = destination) }
                AppNavigator.consumeNotificationDestination()
            }
    }

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

    LaunchedEffect(Unit) {
        AppNavigator.pendingChatInfo.collect { (_, _) ->
            viewModel.updateState { copy(appState = AppState.AI_STUDIO_AGENT) }
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

    val theme = QabasTheme.current
    AnimatedContent(
        targetState = state.appState,
        transitionSpec = {
            AppAnimationManager.createTransitionSpec(
                initialState = initialState,
                targetState = targetState,
                creationStepsOrder = creationStepsOrder,
                reducedMotion = reducedMotion
            )
        },
        label = "QabasRootScreenTransition_${activeAnimation.id}_${activeSpeed.id}"
    ) { targetAppState ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
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
    val theme = QabasTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = theme.cardSurface,
            shadowElevation = if (theme.isDark) 4.dp else 12.dp,
            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = Translator.tr("ضوابط استوديو مساعد المسلم"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = Translator.tr("مرحباً بك في استوديو مساعد المسلم. نلتزم معاً بصناعة محتوى راقٍ ونقي، وفق الضوابط التالية:"),
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                val rules = listOf(
                    Translator.tr("لا تُستخدم الموسيقى، نعتمد على المؤثرات الصوتية المباحة فقط."),
                    Translator.tr("يمنع تماماً أي محتوى خادش للحياء أو ألفاظ بذيئة."),
                    Translator.tr("يمنع الإساءة للإسلام أو لأي من الرموز الدينية."),
                    Translator.tr("الصور المولدة تُطمس وجوهها للحفاظ على الضوابط، بينما الحقيقية تبقى كما هي.")
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rules.forEachIndexed { index, rule ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = theme.primary,
                                    fontFamily = NotoSansFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = rule,
                                color = theme.textPrimary,
                                fontFamily = NotoSansFont,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = Translator.tr("أتعهد بالالتزام"),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
