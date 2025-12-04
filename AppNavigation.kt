package com.example.quizgpt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch

object Routes {
    const val AUTH_SCREEN = "auth"
    const val MAIN_SCREEN = "main"
    const val REGISTER_SCREEN = "register"
    const val FORGOT_PASSWORD_SCREEN = "forgot_password"
    const val RESEND_VERIFICATION_SCREEN = "resend_verification"
    const val CREATE_SET_SCREEN = "create_set/{isAiGraded}"
    const val ADD_CARDS_SCREEN = "add_cards/{setId}/{setTitle}"
    const val SET_DETAILS_SCREEN = "set_details/{setId}"
    const val STUDY_SCREEN = "study/{setId}/{setTitle}?starredOnly={starredOnly}"
    const val WRITE_MODE_SCREEN = "write/{setId}/{setTitle}?starredOnly={starredOnly}"
    const val TEST_CONFIG_SCREEN = "test_config/{setId}/{setTitle}?starredOnly={starredOnly}"
    const val TUTOR_SCREEN = "tutor/{setId}/{setTitle}?starredOnly={starredOnly}"
    const val TEST_SESSION_SCREEN = "test_session/{setId}"
    const val IMPORT_SCREEN = "import"
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    val authViewModel: AuthViewModel = viewModel()
    val quizSetViewModel: QuizSetViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val currentUser by authViewModel.currentUser.collectAsState()
    val authUiState by authViewModel.uiState.collectAsState()

    val startDestination = if (currentUser != null && currentUser!!.isEmailVerified) Routes.MAIN_SCREEN else Routes.AUTH_SCREEN

    NavHost(navController = navController, startDestination = startDestination) {

        // ... (Auth Routes) ...
        composable(Routes.AUTH_SCREEN) {
            AuthScreen(
                uiState = authUiState, isLoading = authUiState.isLoading,
                onSignIn = { e, p -> authViewModel.signIn(e, p) },
                onRegister = { navController.navigate(Routes.REGISTER_SCREEN) },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD_SCREEN) },
                onResendVerification = { navController.navigate(Routes.RESEND_VERIFICATION_SCREEN) }
            )
        }
        composable(Routes.REGISTER_SCREEN) {
            RegisterScreen(uiState = authUiState, onRegisterClicked = { e, p, f, l -> authViewModel.createUserAndSaveDetails(e, p, f, l) }, onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.FORGOT_PASSWORD_SCREEN) {
            ForgotPasswordScreen(uiState = authUiState, isLoading = authUiState.isLoading, onSendResetEmail = { authViewModel.sendPasswordReset(it) }, onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.RESEND_VERIFICATION_SCREEN) {
            ResendVerificationScreen(uiState = authUiState, isLoading = authUiState.isLoading, onResendEmail = { e, p -> authViewModel.resendVerificationEmail(e, p) }, onNavigateBack = { navController.popBackStack() })
        }

        // ... (Main App Routes) ...
        composable(Routes.MAIN_SCREEN) {
            // FIX 1: Collect sets to get the total count for the Profile Screen
            val allSets by quizSetViewModel.sets.collectAsState(initial = emptyList())

            MainContainerScreen(
                // FIX 2: Pass the required parameters
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange,
                totalSetsCount = allSets.size,

                onSignOut = { authViewModel.signOut() },
                onNavigateToCreateSet = { navController.navigate("create_set/$it") },
                onStudySet = { id, _, _ -> navController.navigate("set_details/$id") },
                onManageCards = { id, t -> navController.navigate("add_cards/$id/${java.net.URLEncoder.encode(t, "UTF-8")}") },
                onWriteSet = { id, t -> navController.navigate("write/$id/${java.net.URLEncoder.encode(t, "UTF-8")}?starredOnly=false") },
                onImportSet = { navController.navigate(Routes.IMPORT_SCREEN) },
                quizSetViewModel = quizSetViewModel
            )
        }

        // ... (Rest of your routes remain unchanged) ...
        composable(Routes.IMPORT_SCREEN) {
            ImportSetScreen(
                onNavigateBack = { navController.popBackStack() },
                onImportSuccess = { navController.popBackStack() },
                quizSetViewModel = quizSetViewModel
            )
        }

        composable(Routes.CREATE_SET_SCREEN, arguments = listOf(navArgument("isAiGraded") { type = NavType.BoolType })) {
            val isAi = it.arguments?.getBoolean("isAiGraded") ?: false
            CreateSetScreen(isAiGraded = isAi, onNavigateBack = { navController.popBackStack() }) { t, d -> scope.launch { quizSetViewModel.createSet(t, d, isAi) }; navController.popBackStack() }
        }
        composable(Routes.ADD_CARDS_SCREEN, arguments = listOf(navArgument("setId") { type = NavType.StringType }, navArgument("setTitle") { type = NavType.StringType })) {
            val t = it.arguments?.getString("setTitle")?.let { s -> java.net.URLDecoder.decode(s, "UTF-8") } ?: ""
            AddCardsScreen(setTitle = t, setId = it.arguments?.getString("setId") ?: "", onNavigateBack = { quizSetViewModel.clearCardListener(); navController.popBackStack() }, quizSetViewModel = quizSetViewModel)
        }

        composable(
            route = Routes.SET_DETAILS_SCREEN,
            arguments = listOf(navArgument("setId") { type = NavType.StringType })
        ) { backStackEntry ->
            val setId = backStackEntry.arguments?.getString("setId") ?: ""
            val set = quizSetViewModel.getSetById(setId)
            val safeTitle = if (set != null) java.net.URLEncoder.encode(set.title, "UTF-8") else "Set"

            SetDetailsScreen(
                setId = setId,
                onNavigateBack = { navController.popBackStack() },
                onStudy = { starred -> navController.navigate("study/$setId/$safeTitle?starredOnly=$starred") },
                onWrite = { starred -> navController.navigate("write/$setId/$safeTitle?starredOnly=$starred") },
                onManageCards = { navController.navigate("add_cards/$setId/$safeTitle") },
                onTest = { starred -> navController.navigate("test_config/$setId/$safeTitle?starredOnly=$starred") },
                onTutor = { starred -> navController.navigate("tutor/$setId/$safeTitle?starredOnly=$starred") },
                quizSetViewModel = quizSetViewModel
            )
        }

        composable(
            route = Routes.STUDY_SCREEN,
            arguments = listOf(navArgument("setId") { type = NavType.StringType }, navArgument("setTitle") { type = NavType.StringType }, navArgument("starredOnly") { type = NavType.BoolType; defaultValue = false })
        ) {
            val t = it.arguments?.getString("setTitle")?.let { s -> java.net.URLDecoder.decode(s, "UTF-8") } ?: ""
            val starred = it.arguments?.getBoolean("starredOnly") ?: false
            StudyScreen(setId = it.arguments?.getString("setId") ?: "", setTitle = t, starredOnly = starred, onNavigateBack = { quizSetViewModel.clearCardListener(); navController.popBackStack() }, quizSetViewModel = quizSetViewModel)
        }

        composable(
            route = Routes.WRITE_MODE_SCREEN,
            arguments = listOf(navArgument("setId") { type = NavType.StringType }, navArgument("setTitle") { type = NavType.StringType }, navArgument("starredOnly") { type = NavType.BoolType; defaultValue = false })
        ) {
            val t = it.arguments?.getString("setTitle")?.let { s -> java.net.URLDecoder.decode(s, "UTF-8") } ?: ""
            val starred = it.arguments?.getBoolean("starredOnly") ?: false
            WriteModeScreen(setId = it.arguments?.getString("setId") ?: "", setTitle = t, starredOnly = starred, onNavigateBack = { quizSetViewModel.clearCardListener(); navController.popBackStack() }, quizSetViewModel = quizSetViewModel)
        }

        composable(
            route = Routes.TEST_CONFIG_SCREEN,
            arguments = listOf(navArgument("setId") { type = NavType.StringType }, navArgument("setTitle") { type = NavType.StringType }, navArgument("starredOnly") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val t = backStackEntry.arguments?.getString("setTitle")?.let { s -> java.net.URLDecoder.decode(s, "UTF-8") } ?: ""
            val starred = backStackEntry.arguments?.getBoolean("starredOnly") ?: false
            val id = backStackEntry.arguments?.getString("setId") ?: ""
            TestConfigScreen(
                setId = id, setTitle = t, starredOnly = starred,
                onNavigateBack = { navController.popBackStack() },
                onStartTest = {
                    navController.navigate("test_session/$id")
                },
                quizSetViewModel = quizSetViewModel
            )
        }

        composable(
            route = Routes.TUTOR_SCREEN,
            arguments = listOf(navArgument("setId") { type = NavType.StringType }, navArgument("setTitle") { type = NavType.StringType }, navArgument("starredOnly") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val t = backStackEntry.arguments?.getString("setTitle")?.let { s -> java.net.URLDecoder.decode(s, "UTF-8") } ?: ""
            val starred = backStackEntry.arguments?.getBoolean("starredOnly") ?: false
            val id = backStackEntry.arguments?.getString("setId") ?: ""
            TutorScreen(
                setId = id, setTitle = t, starredOnly = starred,
                onNavigateBack = { navController.popBackStack() },
                quizSetViewModel = quizSetViewModel
            )
        }

        composable(
            route = Routes.TEST_SESSION_SCREEN,
            arguments = listOf(navArgument("setId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("setId") ?: ""
            TestSessionScreen(
                setId = id,
                onNavigateBack = { navController.popBackStack(Routes.SET_DETAILS_SCREEN, false) },
                quizSetViewModel = quizSetViewModel
            )
        }
    }

    LaunchedEffect(currentUser, navController) {
        val currentRoute = navController.currentDestination?.route
        val authScreens = listOf(Routes.AUTH_SCREEN, Routes.REGISTER_SCREEN, Routes.FORGOT_PASSWORD_SCREEN, Routes.RESEND_VERIFICATION_SCREEN)
        if (currentUser != null) {
            if (currentUser!!.isEmailVerified) {
                if (currentRoute != Routes.MAIN_SCREEN && currentRoute in authScreens) {
                    navController.navigate(Routes.MAIN_SCREEN) { popUpTo(Routes.AUTH_SCREEN) { inclusive = true } }
                }
            } else {
                if (currentRoute == Routes.MAIN_SCREEN) navController.navigate(Routes.AUTH_SCREEN) { popUpTo(Routes.MAIN_SCREEN) { inclusive = true } }
            }
        } else {
            if (currentRoute != null && currentRoute !in authScreens) navController.navigate(Routes.AUTH_SCREEN) { popUpTo(Routes.MAIN_SCREEN) { inclusive = true } }
        }
    }
}