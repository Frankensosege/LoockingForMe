package com.frankensosege.loockingforme.ui.setting

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frankensosege.loockingforme.LoockingForMeApp
import com.frankensosege.loockingforme.data.model.SupportedLanguage
import com.frankensosege.loockingforme.data.strings.AppStrings
import com.frankensosege.loockingforme.data.strings.StringsRepository
import com.frankensosege.loockingforme.ui.theme.LoockingForMeTheme
import com.frankensosege.loockingforme.BuildConfig
import com.frankensosege.loockingforme.utils.AdBanner

/** SettingViewModel 기본 생성 — 테스트에서 vm 파라미터로 교체 가능 */
@Composable
private fun settingViewModel(): SettingViewModel {
    val context = LocalContext.current
    val app = context.applicationContext as LoockingForMeApp
    return viewModel(
        factory = SettingViewModel.Factory(
            stringsRepository = StringsRepository(app.stringsDatabase.stringsDao()),
            appContext        = context.applicationContext,
        ),
    )
}

@SuppressLint("ComposeViewModelInjection")
@Composable
fun SettingScreen(
    onSaveAndStart: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingViewModel = settingViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    SettingScreenContent(
        modifier            = modifier,
        uiState             = uiState,
        onLanguageChange    = vm::changeLanguage,
        onSourceDirSelected = { uri ->
            uri?.let { vm.onSourceDirSelected(it) }
        },
        onScanDirSelected = { uri ->
            uri?.let { vm.onScanDirSelected(it) }
        },
        onSimilarityChanged = vm::onSimilarityInputChanged,
        onSaveAndStart = {
            vm.saveAndMarkSetupDone()
            onSaveAndStart()
        },
    )
}

@Composable
private fun SettingScreenContent(
    uiState: SettingUiState,
    onLanguageChange: (SupportedLanguage) -> Unit,
    onSourceDirSelected: (android.net.Uri?) -> Unit,
    onScanDirSelected: (android.net.Uri?) -> Unit,
    onSimilarityChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSaveAndStart: () -> Unit
) {
    val strings = uiState.strings

    // SAF 디렉토리 선택 런처
    val sourceDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> onSourceDirSelected(uri) }

    val scanDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> onScanDirSelected(uri) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = strings.settingTitle,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        // 배너 광고 — 화면 하단 고정 (Scaffold가 본문 하단 여백을 자동 확보)
        bottomBar = {
            AdBanner(
                adUnitId = BuildConfig.AD_BANNER_SETTING_ID,
                modifier = Modifier.navigationBarsPadding(),   // 제스처/내비게이션 바와 겹침 방지
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 언어 선택 섹션 ────────────────────────────────────────────────
            SettingSection(title = strings.settingLanguage) {
                LanguageSelector(
                    selectedLanguage = uiState.selectedLanguage,
                    strings          = strings,
                    onLanguageChange = onLanguageChange,
                )
            }

            HorizontalDivider()

            // ── 소스 사진 폴더 섹션 ───────────────────────────────────────────
            SettingSection(title = strings.settingSourceDir) {
                FolderPickerRow(
                    label       = uiState.sourceDirDisplay.ifEmpty { strings.settingSourceDirHint },
                    buttonLabel = strings.settingSelectFolder,
                    onPickClick = { sourceDirLauncher.launch(uiState.sourceDirUri) },
                )
            }

            HorizontalDivider()

            // ── 탐색 대상 폴더 섹션 ───────────────────────────────────────────
            SettingSection(title = strings.settingScanDir) {
                FolderPickerRow(
                    label       = uiState.scanDirDisplay.ifEmpty { strings.settingScanDirHint },
                    buttonLabel = strings.settingSelectFolder,
                    onPickClick = { scanDirLauncher.launch(uiState.scanDirUri) },
                )
            }

            HorizontalDivider()

            // ── 분류 유사도 섹션 ──────────────────────────────────────────────
            SettingSection(title = strings.settingSimilarityThreshold) {
                SimilarityInputField(
                    input      = uiState.similarityInput,
                    errorMsg   = uiState.similarityError,
                    hint       = strings.settingSimilarityHint,
                    currentLabel = strings.settingCurrentValue.format(uiState.similarityThreshold),
                    guide      = strings.settingSimilarityGuide,
                    onChanged  = onSimilarityChanged,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 저장하고 시작 버튼 ────────────────────────────────────────────
            Button(
                onClick  = onSaveAndStart,
                enabled  = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(text = strings.saveAndStart)
            }
        }
    }
}

// ─── 섹션 래퍼 ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

// ─── 언어 선택 ────────────────────────────────────────────────────────────────

@Composable
private fun LanguageSelector(
    selectedLanguage: SupportedLanguage,
    strings: AppStrings,
    modifier: Modifier = Modifier,
    onLanguageChange: (SupportedLanguage) -> Unit
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SupportedLanguage.entries.forEach { language ->
            val isSelected = language == selectedLanguage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick  = { onLanguageChange(language) },
                        role     = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick  = null, // 접근성: 부모 selectable에 위임
                )
                Text(
                    text  = language.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

// ─── 폴더 선택 행 ─────────────────────────────────────────────────────────────

@Composable
private fun FolderPickerRow(
    label: String,
    buttonLabel: String,
    modifier: Modifier = Modifier,
    onPickClick: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        OutlinedButton(onClick = onPickClick) {
            Text(text = "🗂️ $buttonLabel")
        }
    }
}

// ─── 유사도 입력 필드 ────────────────────────────────────────────────────────────

@SuppressLint("ComposeModifierReused")
@Composable
private fun SimilarityInputField(
    input: String,
    errorMsg: String,
    hint: String,
    currentLabel: String,
    guide: String,
    modifier: Modifier = Modifier,
    onChanged: (String) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier          = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value         = input,
                onValueChange = onChanged,
                label         = { Text(hint) },
                isError       = errorMsg.isNotEmpty(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix        = { Text("%") },
                modifier      = Modifier.weight(1f),
            )
            // 현재 적용된 임계값 표시
            Text(
                text  = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // 에러 메시지
        if (errorMsg.isNotEmpty()) {
            Text(
                text  = errorMsg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // 유사도 안내 텍스트
        Text(
            text  = guide,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "SettingScreen - Light")
@Composable
private fun SettingScreenPreview() {
    LoockingForMeTheme {
        Surface {
            SettingScreenContent(
                uiState          = SettingUiState(),
                onLanguageChange = {},
                onSourceDirSelected = {},
                onScanDirSelected = {},
                onSimilarityChanged = {},
                onSaveAndStart   = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "SettingScreen - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingScreenDarkPreview() {
    LoockingForMeTheme(darkTheme = true) {
        Surface {
            SettingScreenContent(
                uiState          = SettingUiState(selectedLanguage = SupportedLanguage.ENGLISH),
                onLanguageChange = {},
                onSourceDirSelected = {},
                onScanDirSelected = {},
                onSimilarityChanged = {},
                onSaveAndStart   = {},
            )
        }
    }
}