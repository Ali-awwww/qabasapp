package com.qabas.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
                // ق103: صوت النقرة يأتي من playTabRouteSound داخل عنصر
                // التبويب نفسه؛ كان playTab() هنا يعزف نغمة ثانية فوقها
                // (cyber_toggle/luxury_click/...) عند كل تبديل تبويب.
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
