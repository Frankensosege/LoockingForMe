
package com.frankensosege.loockingforme.ui.result

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.frankensosege.loockingforme.LoockingForMeApp
import com.frankensosege.loockingforme.data.model.PhotoItem
import com.frankensosege.loockingforme.data.model.PhotoList
import com.frankensosege.loockingforme.data.repository.PhotoRepository
import com.frankensosege.loockingforme.data.strings.AppStrings
import com.frankensosege.loockingforme.data.strings.StringsRepository
import com.frankensosege.loockingforme.ui.theme.LoockingForMeTheme
import com.frankensosege.loockingforme.utils.FaceAnalyzer
import com.frankensosege.loockingforme.BuildConfig
import com.frankensosege.loockingforme.utils.AdBanner
import androidx.core.net.toUri

/** ResultViewModel 기본 생성 — 테스트에서 vm 파라미터로 교체 가능 */
@Composable
private fun resultViewModel(listId: Long): ResultViewModel {
    val context = LocalContext.current
    val app = context.applicationContext as LoockingForMeApp
    val db  = app.appDatabase
    return viewModel(
        factory = ResultViewModel.Factory(
            listId            = listId,
            stringsRepository = StringsRepository(app.stringsDatabase.stringsDao()),
            photoRepository   = PhotoRepository(
                context        = context.applicationContext,
                photoListDao   = db.photoListDao(),
                photoItemDao   = db.photoItemDao(),
                faceFeatureDao = db.faceFeatureDao(),
                faceAnalyzer   = app.faceAnalyzer,
            ),
            appContext = context.applicationContext,
        ),
    )
}

@SuppressLint("ComposeViewModelInjection")
@Composable
fun ResultScreen(
    listId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ResultViewModel = resultViewModel(listId)
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    ResultScreenContent(
        modifier               = modifier,
        uiState                = uiState,
        onNavigateBack         = {
            if (uiState.isSelectionMode) vm.clearSelection() else onNavigateBack()
        },
        onItemClick            = vm::onItemClick,
        onItemLongClick        = vm::onItemLongClick,
        onToggleSelectAll      = vm::toggleSelectAll,
        onRequestDelete        = vm::requestDelete,
        onDismissDeleteConfirm = vm::dismissDeleteConfirm,
        onConfirmDelete        = vm::confirmDelete,
        onShareBluetooth       = {
            val uris = vm.getSelectedUris()
            if (uris.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    `package` = "com.android.bluetooth"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                bluetoothLauncher.launch(Intent.createChooser(intent, uiState.strings.resultShareBluetooth))
            }
        },
        onOpenReclassify       = vm::openReclassifyDialog,
        onDismissReclassify    = vm::dismissReclassifyDialog,
        onToggleReclassifyTarget = vm::toggleReclassifyTarget,
        onConfirmReclassify    = vm::confirmReclassify,
    )
}

// ─── Content ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultScreenContent(
    uiState: ResultUiState,
    onNavigateBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onItemLongClick: (Long) -> Unit,
    onToggleSelectAll: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onConfirmDelete: () -> Unit,
    onShareBluetooth: () -> Unit,
    onOpenReclassify: () -> Unit,
    onDismissReclassify: () -> Unit,
    onToggleReclassifyTarget: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onConfirmReclassify: () -> Unit
) {
    val strings = uiState.strings

    // ── 삭제 확인 다이얼로그 ──────────────────────────────────────────────────
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirm,
            title   = { Text(strings.resultDeleteSelected) },
            text    = { Text(strings.resultDeleteConfirm) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(strings.resultDeleteSelected) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirm) { Text(strings.cancel) }
            },
        )
    }

    // ── 재분류 다이얼로그 ──────────────────────────────────────────────────────
    if (uiState.showReclassifyDialog) {
        ReclassifyDialog(
            strings                  = strings,
            availableLists           = uiState.availableLists,
            selectedTargetIds        = uiState.reclassifyTargetIds,
            isReclassifying          = uiState.isReclassifying,
            onToggleTarget           = onToggleReclassifyTarget,
            onConfirm                = onConfirmReclassify,
            onDismiss                = onDismissReclassify,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isSelectionMode)
                            "${uiState.selectedIds.size}${strings.resultSelectedCount}"
                        else strings.resultTitle,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.resultBack,
                        )
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        // 전체 선택 토글
                        IconButton(onClick = onToggleSelectAll) {
                            Icon(
                                imageVector = if (uiState.isAllSelected)
                                    Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = if (uiState.isAllSelected)
                                    strings.resultDeselectAll else strings.resultSelectAll,
                            )
                        }
                        // 재분류
                        IconButton(onClick = onOpenReclassify) {
                            Icon(
                                imageVector        = Icons.Default.SwapHoriz,
                                contentDescription = strings.resultReclassify,
                                tint               = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // 블루투스 공유
                        IconButton(onClick = onShareBluetooth) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = strings.resultShareBluetooth,
                            )
                        }
                        // 삭제
                        IconButton(onClick = onRequestDelete) {
                            Icon(
                                imageVector        = Icons.Default.Delete,
                                contentDescription = strings.resultDeleteSelected,
                                tint               = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (uiState.isSelectionMode)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        // 배너 광고 — 화면 하단 고정 (Scaffold가 본문 하단 여백을 자동 확보)
        bottomBar = {
            AdBanner(
                adUnitId = BuildConfig.AD_BANNER_RESULT_ID,
                modifier = Modifier.navigationBarsPadding(),   // 제스처/내비게이션 바와 겹침 방지
            )
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingContent(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )

            uiState.photoItems.isEmpty() -> EmptyResultContent(
                modifier       = Modifier.fillMaxSize().padding(paddingValues),
                strings        = strings,
                onNavigateBack = onNavigateBack,
            )

            else -> Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
                if (!uiState.isSelectionMode) {
                    Text(
                        text      = strings.resultLongPressHint,
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier  = Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                PhotoGridContent(
                    strings         = strings,
                    modifier        = Modifier.weight(1f),
                    photoItems      = uiState.photoItems,
                    selectedIds     = uiState.selectedIds,
                    isSelectionMode = uiState.isSelectionMode,
                    onItemClick     = onItemClick,
                    onItemLongClick = onItemLongClick,
                )
            }
        }
    }
}

// ─── 재분류 다이얼로그 ────────────────────────────────────────────────────────

@Composable
private fun ReclassifyDialog(
    strings: AppStrings,
    @SuppressLint("ComposeUnstableCollections") availableLists: List<PhotoList>,
    @SuppressLint("ComposeUnstableCollections") selectedTargetIds: Set<Long>,
    isReclassifying: Boolean,
    onToggleTarget: (Long) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier         = modifier,
        title = {
            Text(
                text  = strings.resultReclassifyTitle,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                Text(
                    text     = strings.resultReclassifyHint,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                HorizontalDivider()
                if (availableLists.isEmpty()) {
                    Text(
                        text      = strings.resultReclassifyEmpty,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier  = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        modifier            = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(
                            items = availableLists,
                            key   = { it.id },
                        ) { list ->
                            val isChecked = list.id in selectedTargetIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleTarget(list.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // 왼쪽 체크박스
                                Checkbox(
                                    checked        = isChecked,
                                    onCheckedChange = { onToggleTarget(list.id) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text     = list.name,
                                        style    = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.SemiBold
                                        else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isReclassifying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(
                    onClick  = onConfirm,
                    enabled  = selectedTargetIds.isNotEmpty(),
                ) {
                    Text(strings.resultReclassifySave)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )
}

// ─── 서브 컴포저블 ─────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyResultContent(
    strings: AppStrings,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text      = strings.resultEmpty,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateBack) { Text(strings.resultBack) }
    }
}

@Composable
private fun PhotoGridContent(
    strings: AppStrings,
    @SuppressLint("ComposeUnstableCollections") photoItems: List<PhotoItem>,
    @SuppressLint("ComposeUnstableCollections") selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onItemLongClick: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        modifier              = modifier,
        contentPadding        = PaddingValues(4.dp),
        verticalArrangement   = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items = photoItems, key = { it.id }) { item ->
            PhotoGridItem(
                strings         = strings,
                item            = item,
                isSelected      = item.id in selectedIds,
                isSelectionMode = isSelectionMode,
                onItemClick     = { onItemClick(item.id) },
                onItemLongClick = { onItemLongClick(item.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    strings: AppStrings,
    item: PhotoItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
    onItemLongClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (isSelected) Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                ) else Modifier
            )
            .combinedClickable(onClick = onItemClick, onLongClick = onItemLongClick),
    ) {
        AsyncImage(
            model              = item.fileUri.toUri(),
            contentDescription = item.displayName,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )

        // 선택 오버레이
        AnimatedVisibility(
            visible  = isSelected,
            enter    = fadeIn(),
            exit     = fadeOut(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            )
        }

        // 유사도 배지 (좌측 하단)
        if (item.similarityScore > 0f) {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                shape    = RoundedCornerShape(4.dp),
                color    = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                tonalElevation = 2.dp,
            ) {
                Text(
                    text     = "%.0f%%".format(item.similarityScore * 100),
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        // 체크박스 (우측 상단) — 선택 모드일 때만
        AnimatedVisibility(
            visible  = isSelectionMode,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.85f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle
                    else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) strings.resultItemSelected else strings.resultItemNotSelected,
                    tint     = if (isSelected) Color.White
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "ResultScreen - Items")
@Composable
private fun ResultScreenPreview() {
    LoockingForMeTheme {
        ResultScreenContent(
            uiState = ResultUiState(
                isLoading  = false,
                photoItems = listOf(
                    PhotoItem(id = 1, listId = 1, fileUri = "", displayName = "photo_001.jpg", similarityScore = 0.92f),
                    PhotoItem(id = 2, listId = 1, fileUri = "", displayName = "photo_002.jpg", similarityScore = 0.88f),
                ),
            ),
            onNavigateBack = {}, onItemClick = {}, onItemLongClick = {},
            onToggleSelectAll = {}, onRequestDelete = {}, onDismissDeleteConfirm = {},
            onConfirmDelete = {}, onShareBluetooth = {}, onOpenReclassify = {},
            onDismissReclassify = {}, onToggleReclassifyTarget = {}, onConfirmReclassify = {},
        )
    }
}

@Preview(showBackground = true, name = "ReclassifyDialog")
@Composable
private fun ReclassifyDialogPreview() {
    LoockingForMeTheme {
        ReclassifyDialog(
            strings   = AppStrings.defaultStrings,
            availableLists = listOf(
                PhotoList(id = 1, name = "우리 가족"),
                PhotoList(id = 2, name = "친구들"),
                PhotoList(id = 3, name = "회사"),
            ),
            selectedTargetIds = setOf(1L),
            isReclassifying   = false,
            onToggleTarget    = {},
            onConfirm         = {},
            onDismiss         = {},
        )
    }
}

