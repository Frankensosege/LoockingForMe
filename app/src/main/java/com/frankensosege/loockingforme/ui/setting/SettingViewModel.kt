package com.frankensosege.loockingforme.ui.setting

import androidx.core.net.toUri
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.frankensosege.loockingforme.data.model.SupportedLanguage
import com.frankensosege.loockingforme.data.strings.AppStrings
import com.frankensosege.loockingforme.data.strings.StringsRepository
import com.frankensosege.loockingforme.utils.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingUiState(
    val strings: AppStrings      = AppStrings.defaultStrings,
    val selectedLanguage: SupportedLanguage = SupportedLanguage.KOREAN,
    val sourceDirUri: Uri?       = null,
    val scanDirUri: Uri?         = null,
    val sourceDirDisplay: String = "",
    val scanDirDisplay: String   = "",
    /** 유사도 임계값 (50 ~ 99 정수, SharedPreferences에 저장) */
    val similarityThreshold: Int = DEFAULT_SIMILARITY_THRESHOLD,
    /** 유사도 입력 필드 문자열 (사용자 입력 중인 값) */
    val similarityInput: String  = DEFAULT_SIMILARITY_THRESHOLD.toString(),
    /** 유사도 입력 오류 메시지 */
    val similarityError: String  = "",
    val isSaving: Boolean        = false,
) {
    companion object {
        const val DEFAULT_SIMILARITY_THRESHOLD = 62
        const val MIN_THRESHOLD = 50
        const val MAX_THRESHOLD = 99
    }
}

class SettingViewModel(
    private val stringsRepository: StringsRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingUiState())
    val uiState: StateFlow<SettingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // DB 초기 시드
            stringsRepository.seedIfEmpty()
            // SharedPreferences에서 저장된 값 복원
            loadSavedSettings()
        }
        // 언어 변경에 따라 문자열 자동 갱신
        observeStrings()
    }

    private fun observeStrings() {
        stringsRepository
            .getStrings(_uiState.value.selectedLanguage.code)
            .onEach { strings ->
                _uiState.update { it.copy(strings = strings) }
            }
            .launchIn(viewModelScope)
    }

    fun changeLanguage(language: SupportedLanguage) {
        _uiState.update { it.copy(selectedLanguage = language) }
        // 언어 변경 즉시 새 문자열 Flow 구독
        stringsRepository
            .getStrings(language.code)
            .onEach { strings ->
                _uiState.update { it.copy(strings = strings) }
            }
            .launchIn(viewModelScope)
    }

    fun onSourceDirSelected(uri: Uri) {
        StorageHelper.takePersistablePermission(appContext, uri)
        _uiState.update {
            it.copy(
                sourceDirUri     = uri,
                sourceDirDisplay = StorageHelper.uriToDisplayPath(uri),
            )
        }
    }

    fun onScanDirSelected(uri: Uri) {
        StorageHelper.takePersistablePermission(appContext, uri)
        _uiState.update {
            it.copy(
                scanDirUri     = uri,
                scanDirDisplay = StorageHelper.uriToDisplayPath(uri),
            )
        }
    }

    fun onSimilarityInputChanged(input: String) {
        // 숫자만 허용, 최대 2자리
        val filtered = input.filter { it.isDigit() }.take(2)
        val intValue = filtered.toIntOrNull()
        val strings = _uiState.value.strings
        val error = when {
            filtered.isEmpty()                                      -> ""
            intValue == null                                        -> strings.settingErrorNotNumber
            intValue < SettingUiState.MIN_THRESHOLD                -> strings.settingErrorMin.format(SettingUiState.MIN_THRESHOLD)
            intValue > SettingUiState.MAX_THRESHOLD                -> strings.settingErrorMax.format(SettingUiState.MAX_THRESHOLD)
            else                                                    -> ""
        }
        _uiState.update {
            it.copy(
                similarityInput     = filtered,
                similarityThreshold = if (error.isEmpty() && intValue != null) intValue
                else it.similarityThreshold,
                similarityError     = error,
            )
        }
    }

    fun saveAndMarkSetupDone() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value
            val prefs = appContext.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("setup_done", true)
                putString("language_code", state.selectedLanguage.code)
                putString("source_dir_uri", state.sourceDirUri?.toString())
                putString("scan_dir_uri", state.scanDirUri?.toString())
                putInt("similarity_threshold", state.similarityThreshold)
                apply()
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun loadSavedSettings() {
        val prefs = appContext.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
        val langCode   = prefs.getString("language_code", SupportedLanguage.KOREAN.code)
            ?: SupportedLanguage.KOREAN.code
        val language   = SupportedLanguage.fromCode(langCode)
        val srcUri     = prefs.getString("source_dir_uri", null)?.toUri()
        val scanUri    = prefs.getString("scan_dir_uri", null)?.toUri()

        val threshold = prefs.getInt(
            "similarity_threshold",
            SettingUiState.DEFAULT_SIMILARITY_THRESHOLD,
        ).coerceIn(SettingUiState.MIN_THRESHOLD, SettingUiState.MAX_THRESHOLD)

        _uiState.update {
            it.copy(
                selectedLanguage    = language,
                sourceDirUri        = srcUri,
                sourceDirDisplay    = srcUri?.let { u -> StorageHelper.uriToDisplayPath(u) } ?: "",
                scanDirUri          = scanUri,
                scanDirDisplay      = scanUri?.let { u -> StorageHelper.uriToDisplayPath(u) } ?: "",
                similarityThreshold = threshold,
                similarityInput     = threshold.toString(),
            )
        }
    }

    class Factory(
        private val stringsRepository: StringsRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingViewModel(stringsRepository, appContext) as T
    }
}