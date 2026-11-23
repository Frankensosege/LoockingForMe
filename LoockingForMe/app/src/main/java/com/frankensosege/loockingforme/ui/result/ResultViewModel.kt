package com.frankensosege.loockingforme.ui.result

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.frankensosege.loockingforme.data.model.PhotoItem
import com.frankensosege.loockingforme.data.model.PhotoList
import com.frankensosege.loockingforme.data.model.SupportedLanguage
import com.frankensosege.loockingforme.data.repository.PhotoRepository
import com.frankensosege.loockingforme.data.strings.AppStrings
import com.frankensosege.loockingforme.data.strings.StringsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.net.toUri

data class ResultUiState(
    val strings: AppStrings          = AppStrings.defaultStrings,
    val currentList: PhotoList?      = null,
    val photoItems: List<PhotoItem>  = emptyList(),
    val isLoading: Boolean           = true,
    // ── 선택 모드 ──────────────────────────────────────────────────────────────
    val selectedIds: Set<Long>       = emptySet(),
    val showDeleteConfirm: Boolean   = false,
    // ── 재분류 다이얼로그 ──────────────────────────────────────────────────────
    val showReclassifyDialog: Boolean       = false,
    val availableLists: List<PhotoList>    = emptyList(),   // 현재 목록 제외한 일반 목록
    val reclassifyTargetIds: Set<Long>     = emptySet(),    // 재분류 대상 목록 체크 상태
    val isReclassifying: Boolean           = false,
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val isAllSelected: Boolean get() =
        photoItems.isNotEmpty() && selectedIds.size == photoItems.size
}

class ResultViewModel(
    private val listId: Long,
    private val stringsRepository: StringsRepository,
    private val photoRepository: PhotoRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        loadStrings()
        observePhotoItems()
    }

    // ─── 초기화 ──────────────────────────────────────────────────────────────

    private fun loadStrings() {
        val prefs = appContext.getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("language_code", SupportedLanguage.KOREAN.code)
            ?: SupportedLanguage.KOREAN.code
        stringsRepository.getStrings(langCode)
            .onEach { strings -> _uiState.update { it.copy(strings = strings) } }
            .launchIn(viewModelScope)
    }

    private fun observePhotoItems() {
        photoRepository.getPhotoItems(listId)
            .onEach { items ->
                _uiState.update { state ->
                    val validIds = state.selectedIds
                        .filter { id -> items.any { it.id == id } }.toSet()
                    state.copy(
                        photoItems  = items,
                        isLoading   = false,
                        selectedIds = validIds,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // ─── 선택 모드 ────────────────────────────────────────────────────────────

    fun onItemLongClick(itemId: Long) {
        _uiState.update { state ->
            state.copy(selectedIds = state.selectedIds + itemId)
        }
    }

    fun onItemClick(itemId: Long) {
        if (!_uiState.value.isSelectionMode) return
        _uiState.update { state ->
            val newIds = if (itemId in state.selectedIds)
                state.selectedIds - itemId
            else
                state.selectedIds + itemId
            state.copy(selectedIds = newIds)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            val newIds = if (state.isAllSelected) emptySet()
            else state.photoItems.map { it.id }.toSet()
            state.copy(selectedIds = newIds)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    // ─── 삭제 ────────────────────────────────────────────────────────────────

    fun requestDelete() {
        if (_uiState.value.selectedIds.isEmpty()) return
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDelete() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            photoRepository.deletePhotoItemsByIds(ids, _uiState.value.strings.unclassifiedListName)
            _uiState.update { it.copy(showDeleteConfirm = false, selectedIds = emptySet()) }
        }
    }

    // ─── 블루투스 공유 ────────────────────────────────────────────────────────

    fun getSelectedUris(): List<android.net.Uri> {
        val state = _uiState.value
        return state.photoItems
            .filter { it.id in state.selectedIds }
            .mapNotNull { try {
                it.fileUri.toUri() } catch (e: Exception) { null } }
    }

    // ─── 재분류 ──────────────────────────────────────────────────────────────

    /** 재분류 다이얼로그 열기 — 현재 목록 제외한 일반 목록 로드 */
    fun openReclassifyDialog() {
        if (_uiState.value.selectedIds.isEmpty()) return
        viewModelScope.launch {
            val allLists     = photoRepository.getClassifiedLists()
            val availableLists = allLists.filter { it.id != listId }
            _uiState.update {
                it.copy(
                    showReclassifyDialog = true,
                    availableLists       = availableLists,
                    reclassifyTargetIds  = emptySet(),
                )
            }
        }
    }

    fun dismissReclassifyDialog() {
        _uiState.update { it.copy(showReclassifyDialog = false, reclassifyTargetIds = emptySet()) }
    }

    /** 재분류 목록 체크 토글 */
    fun toggleReclassifyTarget(listId: Long) {
        _uiState.update { state ->
            val newIds = if (listId in state.reclassifyTargetIds)
                state.reclassifyTargetIds - listId
            else
                state.reclassifyTargetIds + listId
            state.copy(reclassifyTargetIds = newIds)
        }
    }

    /** 재분류 실행 — 선택 항목을 체크된 목록으로 이동, 현재 분류에서 삭제 */
    fun confirmReclassify() {
        val state = _uiState.value
        val itemIds    = state.selectedIds.toList()
        val targetIds  = state.reclassifyTargetIds.toList()
        if (itemIds.isEmpty() || targetIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isReclassifying = true) }
            photoRepository.reclassifyItems(
                itemIds       = itemIds,
                currentListId = listId,
                targetListIds = targetIds,
            )
            _uiState.update {
                it.copy(
                    isReclassifying      = false,
                    showReclassifyDialog = false,
                    reclassifyTargetIds  = emptySet(),
                    selectedIds          = emptySet(),
                )
            }
        }
    }

    // ─── Factory ─────────────────────────────────────────────────────────────

    class Factory(
        private val listId: Long,
        private val stringsRepository: StringsRepository,
        private val photoRepository: PhotoRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ResultViewModel(listId, stringsRepository, photoRepository, appContext) as T
    }
}