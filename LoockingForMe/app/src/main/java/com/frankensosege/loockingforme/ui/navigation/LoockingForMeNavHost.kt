package com.frankensosege.loockingforme.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.frankensosege.loockingforme.ui.main.FindMyFaceScreen
import com.frankensosege.loockingforme.ui.result.ResultScreen
import com.frankensosege.loockingforme.ui.setting.SettingScreen

/**
 * 앱 내 화면 라우트 정의
 */
sealed class Routes(val route: String) {
    /** 설정 화면 (최초 실행 또는 ⚙️ 버튼) */
    data object Settings : Routes("settings")

    /** 메인 화면 (나를 찾아다오 메인) */
    data object FindMyFace : Routes("find_my_face")

    /** 검색 결과 화면 */
    data object Result : Routes("result/{listId}") {
        fun createRoute(listId: Long) = "result/$listId"
    }
}

/**
 * 앱 전체 내비게이션 호스트
 * setup_done SharedPreferences 값에 따라 시작 화면 결정
 */
@Composable
fun LoockingForMeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val startDestination = remember {
        resolveStartDestination(context)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.Settings.route) {
            SettingScreen(
                onSaveAndStart = {
                    navController.navigate(Routes.FindMyFace.route) {
                        popUpTo(Routes.Settings.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.FindMyFace.route) {
            FindMyFaceScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.Settings.route)
                },
                onNavigateToResult = { listId ->
                    navController.navigate(Routes.Result.createRoute(listId))
                },
            )
        }

        composable(Routes.Result.route) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: -1L
            ResultScreen(
                listId = listId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

/** SharedPreferences에서 setup_done 확인 후 시작 라우트 반환 */
private fun resolveStartDestination(context: Context): String {
    val prefs = context.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
    return if (prefs.getBoolean("setup_done", false)) {
        Routes.FindMyFace.route
    } else {
        Routes.Settings.route
    }
}
