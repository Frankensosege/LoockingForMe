package com.frankensosege.loockingforme.ui.main

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.frankensosege.loockingforme.BuildConfig
import com.frankensosege.loockingforme.data.model.FaceFeature
import com.frankensosege.loockingforme.data.model.PhotoList
import com.frankensosege.loockingforme.data.model.SupportedLanguage
import com.frankensosege.loockingforme.data.repository.PhotoRepository
import com.frankensosege.loockingforme.data.strings.AppStrings
import com.frankensosege.loockingforme.data.strings.StringsRepository
import com.frankensosege.loockingforme.service.FaceScanService
import com.frankensosege.loockingforme.utils.FaceAnalyzer
import com.frankensosege.loockingforme.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.core.content.edit
import com.frankensosege.loockingforme.ui.setting.SettingUiState

// ─── UI State ────────────────────────────────────────────────────────────────

data class MainUiState(
    val strings: AppStrings           = AppStrings.defaultStrings,
    val lists: List<PhotoList>        = emptyList(),
    val newListName: String           = "",
    val scanDirUri: Uri?              = null,
    val scanDirDisplay: String        = "",
    /** 설정 화면에서 지정한 소스 사진 폴더 — 얼굴 선택 팝업의 사진 출처 */
    val sourceDirUri: Uri?            = null,
    val isScanning: Boolean           = false,
    val scanProgress: Float           = 0f,
    val scanStatusMessage: String     = "",
    val scanResultCount: Int          = 0,
    val facePickerTargetListId: Long? = null,
    val errorMessage: String?         = null,
    /** 현재 선택 중인 슬롯 인덱스 (0~3) */
    val facePickerTargetSlotIndex: Int = 0,
    /** 재분류 방식 선택 다이얼로그 표시 여부 */
    val showRescanConfirmDialog: Boolean = false,
    val pendingScanUri: Uri?          = null,
    val pendingThreshold: Float       = 0.75f,
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class MainViewModel(
    private val stringsRepository: StringsRepository,
    private val photoRepository: PhotoRepository,
    private val faceAnalyzer: FaceAnalyzer,
    private val appContext: android.app.Application,   // Context 누수 방지: Application 사용
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _faceFeatures = MutableStateFlow<Map<Long, FaceFeature?>>(emptyMap())
    val faceFeatures: StateFlow<Map<Long, FaceFeature?>> = _faceFeatures.asStateFlow()

    private val _photoItemCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val photoItemCounts: StateFlow<Map<Long, Int>> = _photoItemCounts.asStateFlow()

    init {
        // 얼굴 모델 평가 — 디버그 빌드에서 eval/RUN 파일이 있을 때 1회 실행
        if (BuildConfig.DEBUG) runFaceEvalIfRequested()
    }

    // ─── Service 연결 ─────────────────────────────────────────────────────────

    @SuppressLint("StaticFieldLeak")
    private var scanService: FaceScanService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as FaceScanService.ScanBinder).getService()
            scanService = service
            isBound = true

            // Service의 ScanState Flow를 ViewModel에서 구독
            service.scanState
                .onEach { state -> handleScanState(state) }
                .launchIn(viewModelScope)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            scanService = null
            isBound = false
        }
    }

    private fun handleScanState(state: FaceScanService.ScanState) {
        when (state) {
            is FaceScanService.ScanState.Idle -> { /* 초기 상태, 무시 */ }

            is FaceScanService.ScanState.Running -> {
                _uiState.update { s ->
                    s.copy(
                        isScanning        = true,
                        scanProgress      = state.progress,
                        scanStatusMessage = "${s.strings.scanInProgress} (${state.scanned} / ${state.total})",
                        scanResultCount   = state.found,
                    )
                }
            }

            is FaceScanService.ScanState.Completed -> {
                _uiState.update { s ->
                    s.copy(
                        isScanning        = false,
                        scanProgress      = 1f,
                        scanStatusMessage = s.strings.scanComplete,
                        scanResultCount   = state.found,
                    )
                }
                // 스캔 완료 후 미분류 목록 정리 (분류된 파일 제거)
                viewModelScope.launch { photoRepository.pruneUnclassified() }
                unbindScanService()
            }

            is FaceScanService.ScanState.Stopped -> {
                _uiState.update { s ->
                    s.copy(
                        isScanning        = false,
                        scanProgress      = 0f,
                        scanStatusMessage = s.strings.scanStoppedStatus.format(state.found),
                        scanResultCount   = state.found,
                    )
                }
                unbindScanService()
            }

            is FaceScanService.ScanState.Error -> {
                _uiState.update { s ->
                    s.copy(
                        isScanning        = false,
                        scanProgress      = 0f,
                        scanStatusMessage = "",
                        errorMessage      = state.message,
                    )
                }
                unbindScanService()
            }
        }
    }

    private fun unbindScanService() {
        if (isBound) {
            appContext.unbindService(serviceConnection)
            isBound    = false
            scanService = null
        }
    }

    // ─── 초기화 ──────────────────────────────────────────────────────────────

    init {
        loadSettings()
        observeLists()
    }

    private fun loadSettings() {
        val prefs    = appContext.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("language_code", SupportedLanguage.KOREAN.code)
            ?: SupportedLanguage.KOREAN.code
        val scanUri  = prefs.getString("scan_dir_uri", null)?.toUri()
        val srcUri   = prefs.getString("source_dir_uri", null)?.toUri()
        _uiState.update {
            it.copy(
                scanDirUri     = scanUri,
                scanDirDisplay = scanUri?.let { u -> StorageHelper.uriToDisplayPath(u) } ?: "",
                sourceDirUri   = srcUri,
            )
        }
        stringsRepository.getStrings(langCode)
            .onEach { strings -> _uiState.update { it.copy(strings = strings) } }
            .launchIn(viewModelScope)
    }

    private fun observeLists() {
        photoRepository.getAllLists()
            .onEach { lists ->
                _uiState.update { it.copy(lists = lists) }
                lists.forEach { list ->
                    photoRepository.getFaceFeature(list.id)
                        .onEach { feature ->
                            _faceFeatures.update { current ->
                                current.toMutableMap().also { it[list.id] = feature }
                            }
                        }
                        .launchIn(viewModelScope)

                    photoRepository.getPhotoItemCount(list.id)
                        .onEach { count ->
                            _photoItemCounts.update { current ->
                                current.toMutableMap().also { it[list.id] = count }
                            }
                        }
                        .launchIn(viewModelScope)
                }
            }
            .launchIn(viewModelScope)
    }

    // ─── 목록 CRUD ────────────────────────────────────────────────────────────

    fun onNewListNameChanged(name: String) {
        _uiState.update { it.copy(newListName = name) }
    }

    fun addList() {
        val name = _uiState.value.newListName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            photoRepository.insertList(name = name)
            _uiState.update { it.copy(newListName = "") }
        }
    }

    fun deleteList(list: PhotoList) {
        viewModelScope.launch {
            // 이 목록의 사진은 미분류로 이동 (다른 목록에도 분류된 사진은 제외)
            photoRepository.deleteList(list, _uiState.value.strings.unclassifiedListName)
            _faceFeatures.update { it.toMutableMap().also { m -> m.remove(list.id) } }
        }
    }

    // ─── 스캔 디렉토리 ────────────────────────────────────────────────────────

    fun onScanDirSelected(uri: Uri) {
        StorageHelper.takePersistablePermission(appContext, uri)
        _uiState.update {
            it.copy(
                scanDirUri     = uri,
                scanDirDisplay = StorageHelper.uriToDisplayPath(uri),
            )
        }
//        appContext.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
//            .edit().putString("scan_dir_uri", uri.toString()).apply()
        appContext.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
            .edit { putString("scan_dir_uri", uri.toString()) }
    }

    // ─── 얼굴 선택 ───────────────────────────────────────────────────────────

    /**
     * 얼굴 선택 팝업 열기
     * - 설정 화면에서 지정한 소스 사진 폴더(source_dir_uri)를 열 때마다 다시 읽는다
     *   (설정에서 폴더를 바꾼 직후에도 최신 값 반영)
     */
    fun openFacePicker(listId: Long, slotIndex: Int = 0) {
        val srcUri = appContext.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
            .getString("source_dir_uri", null)?.toUri()
        _uiState.update {
            it.copy(
                facePickerTargetListId    = listId,
                facePickerTargetSlotIndex = slotIndex,
                sourceDirUri              = srcUri,
            )
        }
    }

    fun closeFacePicker() {
        _uiState.update { it.copy(facePickerTargetListId = null, facePickerTargetSlotIndex = 0) }
    }

    /**
     * 전처리·정규화 비교 평가
     *
     * 사용법: 사람별 하위 폴더에 사진을 넣은 eval/ 폴더와 eval/RUN 파일을 만든 뒤 디버그 앱을 실행하면
     * 평가가 1회 실행된다 (RUN 파일은 실행 후 자동 삭제).
     * eval/ 위치는 앱 내부 저장소(files/eval)를 먼저, 없으면 외부 전용 폴더(Android/data/<패키지>/files/eval)를 본다.
     * (adb push한 외부 폴더가 앱에서 안 보이는 기기가 있어 내부 저장소를 지원)
     * 결과: logcat 태그 FaceEval, 외부 전용 폴더의 eval_result.txt
     * 모델 확정 후 이 함수와 init 블록, FaceAnalyzer.debugEvaluate를 삭제할 것.
     */
    private fun runFaceEvalIfRequested() {
        val externalBase = appContext.getExternalFilesDir(null)
        val candidates = listOfNotNull(
            java.io.File(appContext.filesDir, "eval"),
            externalBase?.let { java.io.File(it, "eval") },
        )
        candidates.forEach {
            android.util.Log.i("FaceEval", "평가 트리거 확인: ${it.absolutePath} " +
                    "폴더=${it.exists()} RUN=${java.io.File(it, "RUN").exists()} 하위=${it.list()?.toList()}")
        }
        val evalDir = candidates.firstOrNull { java.io.File(it, "RUN").exists() } ?: return
        val trigger = java.io.File(evalDir, "RUN")
        android.util.Log.i("FaceEval", "RUN 삭제=${trigger.delete()} → 평가 시작 (${evalDir.absolutePath})")
        viewModelScope.launch {
            val report = faceAnalyzer.debugEvaluate(evalDir)
            // adb pull 하기 쉽도록 외부 전용 폴더 루트에도 결과 사본 저장
            try {
                val outDir = externalBase ?: evalDir
                java.io.File(outDir, "eval_result.txt").writeText(report)
            } catch (e: Exception) {
                android.util.Log.w("FaceEval", "결과 사본 저장 실패: ${e.message}")
            }
            val first = report.lines().firstOrNull().orEmpty()
            _uiState.update {
                it.copy(errorMessage = "$first\n(자세한 결과: logcat FaceEval / eval_result.txt)")
            }
        }
    }

    /**
     * 기존 얼굴에 추가 샘플 등록 (목록 슬롯 클릭 시)
     *
     * @param slotIndex 클릭한 슬롯 번호 (0~3) — 해당 슬롯의 임베딩과 썸네일을 저장(이미 있으면 교체)
     * 최대 4개 슬롯 — 많을수록 centroid 정확도 향상
     * 4개 등록이 필수는 아님
     */
    fun onAddFaceSample(
        bitmap: Bitmap,
        face: Face,
        sourceUri: Uri,
        listId: Long,
        slotIndex: Int,
    ) {
        viewModelScope.launch {
            val embeddings = faceAnalyzer.extractAugmentedEmbeddings(bitmap, face)
            if (embeddings.isEmpty()) {
                _uiState.update { it.copy(errorMessage = it.strings.facePickerNoFace) }
                return@launch
            }

            // 슬롯 인덱스 기반 파일명으로 저장 — 각 슬롯 썸네일이 독립 유지됨
            val thumbnailPath = faceAnalyzer.cropAndSaveFaceThumbnail(
                context   = appContext,
                bitmap    = bitmap,
                face      = face,
                listId    = listId,
                slotIndex = slotIndex,
            ) ?: ""

            // 슬롯 단위로 저장 — 해당 슬롯의 임베딩·썸네일만 교체/추가하고 다른 슬롯은 유지
            val existing: FaceFeature? = photoRepository.getFaceFeatureOnce(listId)
            val base = existing ?: FaceFeature(
                listId    = listId,
                embedding = "",
                sourceUri = sourceUri.toString(),
            )
            val newFeature = base.withSlot(slotIndex, embeddings, thumbnailPath)
            photoRepository.deleteFaceFeature(listId)
            photoRepository.saveFaceFeature(newFeature)
            android.util.Log.d("MainViewModel",
                "샘플 추가: listId=$listId, slot=$slotIndex, 임베딩 수=${newFeature.embeddingCount}")
            closeFacePicker()
        }
    }

    /**
     * 목록의 샘플 얼굴 1개 삭제 (슬롯 스와이프)
     *
     * - 해당 슬롯의 임베딩과 썸네일 파일을 삭제하고, 나머지 슬롯은 유지한다. (centroid는 남은 샘플로 재계산됨)
     * - 남은 샘플이 없으면 얼굴 정보 전체를 삭제한다. (목록은 얼굴 미등록 상태가 됨)
     * - 구 형식(슬롯 구분 없이 저장된) 데이터는 슬롯별 분리가 불가능해 전체를 삭제한다. (다시 등록 필요)
     */
    fun deleteFaceSample(listId: Long, slotIndex: Int) {
        viewModelScope.launch {
            val existing = photoRepository.getFaceFeatureOnce(listId) ?: return@launch
            val updated  = existing.withoutSlot(slotIndex)

            // 삭제할 썸네일 파일 — 남는 샘플이 없으면 전체
            val removePaths = if (updated == null) existing.getThumbnailPaths()
            else listOf(existing.getThumbnailPath(slotIndex))

            photoRepository.deleteFaceFeature(listId)
            if (updated != null) photoRepository.saveFaceFeature(updated)

            withContext(Dispatchers.IO) {
                removePaths.filter { it.isNotEmpty() }.forEach { path ->
                    try { java.io.File(path).delete() } catch (_: Exception) { }
                }
            }
            android.util.Log.d("MainViewModel",
                "샘플 삭제: listId=$listId, slot=$slotIndex, 남은 샘플=${updated?.embeddingCount ?: 0}")
        }
    }

    /**
     * 사용자가 선택한 Face 객체로 임베딩 추출 + 썸네일 저장
     *
     * ★ Augmentation 멀티 임베딩:
     *   원본 / 좌우반전 / 밝기+30 / 밝기-30 총 4가지 변형 임베딩을 생성하여
     *   FaceFeature에 함께 저장 → 검색 시 centroid(평균) 임베딩으로 비교
     *   → 표정·각도·조명이 다른 사진도 더 잘 찾을 수 있음
     */
    fun onFaceTouched(
        bitmap: Bitmap,
        face: Face,
        sourceUri: Uri,
        listId: Long,
    ) {
        viewModelScope.launch {
            val embeddings = faceAnalyzer.extractAugmentedEmbeddings(bitmap, face)
            if (embeddings.isEmpty()) {
                _uiState.update { it.copy(errorMessage = it.strings.facePickerNoFace) }
                return@launch
            }

            // 첫 등록은 슬롯 0번에 썸네일 저장
            val thumbnailPath = faceAnalyzer.cropAndSaveFaceThumbnail(
                context   = appContext,
                bitmap    = bitmap,
                face      = face,
                listId    = listId,
                slotIndex = 0,
            ) ?: ""

            photoRepository.deleteFaceFeature(listId)
            photoRepository.saveFaceFeature(
                FaceFeature.fromFloatArrayList(
                    listId            = listId,
                    embeddings        = embeddings,
                    sourceUri         = sourceUri.toString(),
                    faceThumbnailPath = thumbnailPath,
                )
            )
            android.util.Log.d("MainViewModel",
                "얼굴 등록 완료: listId=$listId, 임베딩 수=${embeddings.size}, slot=0")
            closeFacePicker()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ─── 스캔 시작 / 중지 ────────────────────────────────────────────────────

    /**
     * 스캔 시작 — 전면 광고는 UI(FindMyFaceScreen)에서 먼저 표시한 뒤 이 함수를 호출한다.
     * (ViewModel이 Activity를 참조하지 않도록 광고 표시 책임을 UI로 이동)
     *
     * - 미분류 제외 일반 목록에 분류된 사진이 하나라도 있으면 다이얼로그 표시
     *   → 예: 기존 분류 삭제 후 전체 재분류
     *   → 아니오: 이미 분류된 사진 건너뛰고 미분류만 분류
     * - 분류된 사진 없으면 바로 스캔 시작
     */
    fun startScan() {
        val state = _uiState.value
        val scanUri = state.scanDirUri ?: run {
            _uiState.update { it.copy(errorMessage = it.strings.settingScanDirHint) }
            return
        }
        if (_faceFeatures.value.values.none { it != null }) {
            _uiState.update { it.copy(errorMessage = it.strings.mainFaceNotSet) }
            return
        }
        if (state.isScanning) return

        val prefs = appContext.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
        val threshold = prefs.getInt("similarity_threshold", SettingUiState.DEFAULT_SIMILARITY_THRESHOLD).coerceIn(50, 99) / 100f

        // 미분류 제외 일반 목록에 분류된 사진이 하나라도 있는지 확인
        val hasExisting = _photoItemCounts.value.entries.any { (listId, count) ->
            count > 0 && state.lists.find { it.id == listId }?.isUnclassified == false
        }
        if (hasExisting) {
            // 다이얼로그 표시
            _uiState.update {
                it.copy(
                    showRescanConfirmDialog = true,
                    pendingScanUri          = scanUri,
                    pendingThreshold        = threshold,
                )
            }
        } else {
            // 분류된 사진 없음 → 바로 스캔
            executeScan(scanUri, threshold, deleteExisting = true)
        }
    }

    /** 예 — 기존 분류 삭제 후 전체 재분류 */
    fun confirmRescanDelete() {
        val state = _uiState.value
        val uri   = state.pendingScanUri ?: return
        _uiState.update { it.copy(showRescanConfirmDialog = false) }
        executeScan(uri, state.pendingThreshold, deleteExisting = true)
    }

    /** 아니오 — 이미 분류된 사진 건너뛰고 미분류만 분류 */
    fun confirmRescanSkip() {
        val state = _uiState.value
        val uri   = state.pendingScanUri ?: return
        _uiState.update { it.copy(showRescanConfirmDialog = false) }
        executeScan(uri, state.pendingThreshold, deleteExisting = false)
    }

    fun dismissRescanConfirm() {
        _uiState.update { it.copy(showRescanConfirmDialog = false, pendingScanUri = null) }
    }

    private fun executeScan(scanUri: Uri, threshold: Float, deleteExisting: Boolean) {
        val unclassifiedName = _uiState.value.strings.unclassifiedListName
        _uiState.update {
            it.copy(
                isScanning        = true,
                scanProgress      = 0f,
                scanStatusMessage = it.strings.scanInProgress,
                scanResultCount   = 0,
                pendingScanUri    = null,
            )
        }
        viewModelScope.launch {
            photoRepository.buildUnclassifiedList(
                scanDirUri       = scanUri,
                unclassifiedName = unclassifiedName,
            )
        }
        val startIntent = FaceScanService.buildStartIntent(
            appContext, scanUri, threshold, deleteExisting
        )
        appContext.startForegroundService(startIntent)
        appContext.bindService(startIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** 중지 — Service에 취소 신호 → 그때까지 저장된 결과 보존 */
    fun stopScan() {
        if (isBound) {
            scanService?.stopScan()          // Binder를 통한 직접 호출
        } else {
            // Binder 연결 전이라면 Intent로 전달
            appContext.startService(FaceScanService.buildStopIntent(appContext))
        }
    }

    fun clearScanStatus() {
        _uiState.update { it.copy(scanStatusMessage = "", scanProgress = 0f, scanResultCount = 0) }
    }

    override fun onCleared() {
        super.onCleared()
        unbindScanService()
    }

    // ─── Factory ─────────────────────────────────────────────────────────────

    class Factory(
        private val stringsRepository: StringsRepository,
        private val photoRepository: PhotoRepository,
        private val faceAnalyzer: FaceAnalyzer,
        private val appContext: android.app.Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(stringsRepository, photoRepository, faceAnalyzer, appContext) as T
    }
}