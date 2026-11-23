package com.frankensosege.loockingforme

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frankensosege.loockingforme.data.model.SupportedLanguage
import com.frankensosege.loockingforme.data.strings.AppStrings
import com.frankensosege.loockingforme.data.strings.StringsRepository
import com.frankensosege.loockingforme.ui.navigation.LoockingForMeNavHost
import com.frankensosege.loockingforme.ui.theme.LoockingForMeTheme
import com.frankensosege.loockingforme.utils.AdManager
import com.frankensosege.loockingforme.utils.InAppUpdateManager

/**
 * 앱 진입점 Activity
 * - Edge-to-edge 레이아웃 활성화
 * - Compose 전담 UI 설정
 * - 화면 흐름은 NavHost가 전담
 */
class MainActivity : ComponentActivity() {

    companion object {
        // 전면 광고 단위 ID (테스트/실제 분기)
        val INTERSTITIAL_AD_UNIT_ID = BuildConfig.AD_INTERSTITIAL_ID
    }

    private lateinit var updateManager: InAppUpdateManager
    private var updateReady by mutableStateOf(false)    // 다운로드 완료 → 재시작 안내 표시

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateManager = InAppUpdateManager(this) { updateReady = true }

        // AdMob 초기화 (동의 처리 포함)
        AdManager.initialize(this) {
            Log.d("MainActivity", "광고 SDK 준비 완료")
            AdManager.loadInterstitial(this, INTERSTITIAL_AD_UNIT_ID)
        }

        enableEdgeToEdge()
        setContent {
            LoockingForMeTheme {
                // InApp Update — 다운로드 완료 시 재시작 안내
                if (updateReady) {
                    UpdateReadyDialogHost(
                        onRestart = {
                            updateReady = false
                            updateManager.completeUpdate()
                        },
                        onLater = { updateReady = false },
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    LoockingForMeNavHost()
                }
            }
        }
    }

    // InApp Update
    override fun onDestroy() {
        super.onDestroy()
        updateManager.unregister()
    }

    override fun onResume() {
        super.onResume()
        updateManager.checkForUpdate()
    }
}

/**
 * 설정 화면에서 선택한 언어(SharedPreferences `language_code`)로 문자열을 읽어
 * 업데이트 안내 다이얼로그를 표시한다.
 * (Activity에는 ViewModel이 없으므로 StringsRepository를 직접 사용)
 */
@Composable
private fun UpdateReadyDialogHost(
    onRestart: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stringsFlow = remember {
        val app   = context.applicationContext as LoockingForMeApp
        val prefs = context.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
        val code  = prefs.getString("language_code", SupportedLanguage.KOREAN.code)
            ?: SupportedLanguage.KOREAN.code
        StringsRepository(app.stringsDatabase.stringsDao()).getStrings(code)
    }
    val strings by stringsFlow.collectAsStateWithLifecycle(initialValue = AppStrings.defaultStrings)

    UpdateReadyDialog(
        strings   = strings,
        onRestart = onRestart,
        onLater   = onLater,
        modifier  = modifier,
    )
}

@Composable
private fun UpdateReadyDialog(
    strings: AppStrings,
    onRestart: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier         = modifier,
        onDismissRequest = onLater,
        title            = { Text(strings.appName) },
        text             = { Text(strings.updateReadyMsg) },
        confirmButton    = { TextButton(onClick = onRestart) { Text(strings.updateRestart) } },
        dismissButton    = { TextButton(onClick = onLater) { Text(strings.updateLater) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    LoockingForMeTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // 미리보기용 — NavHost 대신 빈 Surface
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateReadyDialogPreview() {
    LoockingForMeTheme {
        UpdateReadyDialog(
            strings   = AppStrings.defaultStrings,
            onRestart = {},
            onLater   = {},
        )
    }
}