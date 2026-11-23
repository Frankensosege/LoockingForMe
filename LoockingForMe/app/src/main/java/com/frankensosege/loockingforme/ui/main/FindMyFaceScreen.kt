package com.frankensosege.loockingforme.ui.main

import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.frankensosege.loockingforme.LoockingForMeApp
import com.frankensosege.loockingforme.data.model.FaceFeature
import com.frankensosege.loockingforme.data.model.PhotoList
import com.frankensosege.loockingforme.data.repository.PhotoRepository
import com.frankensosege.loockingforme.data.strings.AppStrings
import com.frankensosege.loockingforme.data.strings.StringsRepository
import com.frankensosege.loockingforme.ui.theme.LoockingForMeTheme
import com.frankensosege.loockingforme.utils.FaceAnalyzer
import com.frankensosege.loockingforme.utils.StorageHelper
import kotlinx.coroutines.launch
import com.frankensosege.loockingforme.BuildConfig
import com.frankensosege.loockingforme.MainActivity
import com.frankensosege.loockingforme.utils.AdManager
import com.frankensosege.loockingforme.utils.AdBanner
import androidx.compose.runtime.produceState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MainViewModel 생성에 필요한 의존성을 조립한다. (Composable 기본 파라미터에서 사용)
 * Application Context만 사용하므로 Context 누수가 없다.
 */
private fun createMainViewModelFactory(context: Context): MainViewModel.Factory {
    val app = context.applicationContext as LoockingForMeApp
    val db = app.appDatabase
    return MainViewModel.Factory(
        stringsRepository = StringsRepository(app.stringsDatabase.stringsDao()),
        photoRepository   = PhotoRepository(
            context        = app,
            photoListDao   = db.photoListDao(),
            photoItemDao   = db.photoItemDao(),
            faceFeatureDao = db.faceFeatureDao(),
            faceAnalyzer   = app.faceAnalyzer,
        ),
        faceAnalyzer = app.faceAnalyzer,
        appContext   = app,   // Application 전달 — Context 누수 방지
    )
}

@Composable
fun FindMyFaceScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    vm: MainViewModel = viewModel(factory = createMainViewModelFactory(LocalContext.current)),
    onNavigateToResult: (Long) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val uiState         by vm.uiState.collectAsStateWithLifecycle()
    val faceFeatures    by vm.faceFeatures.collectAsStateWithLifecycle()
    val photoItemCounts by vm.photoItemCounts.collectAsStateWithLifecycle()

    // ── 권한 요청 ────────────────────────────────────────────────────────────────
    var showPermissionRationale by remember { mutableStateOf(false) }

    // 미디어 읽기 권한 런처
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showPermissionRationale = true
    }

    // 알림 권한 런처 (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 알림 권한 거부 시 앱은 계속 동작, 알림만 미표시 */ }

    // 최초 1회 권한 요청
    LaunchedEffect(Unit) {
        // 미디어 읽기 권한
        mediaPermissionLauncher.launch(StorageHelper.getRequiredMediaPermission())
        // 알림 권한 — Android 13(API 33) 이상에서만 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    // 스캔 디렉토리 선택 런처
    val scanDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.onScanDirSelected(it) } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 에러 메시지 → Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
            vm.clearError()
        }
    }

    // 권한 안내 다이얼로그
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title   = { Text(uiState.strings.permissionRequired) },
            text    = { Text(uiState.strings.permissionRationale) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    context.startActivity(StorageHelper.buildAppSettingsIntent(context))
                }) {
                    Text(uiState.strings.permissionGoSettings)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(uiState.strings.cancel)
                }
            },
        )
    }

    // 얼굴 선택 팝업
    uiState.facePickerTargetListId?.let { listId ->
        val slotIndex      = uiState.facePickerTargetSlotIndex
        val feature        = faceFeatures[listId]
        // 이미 등록된 얼굴이 있으면 슬롯 단위로 추가/교체, 없으면 첫 등록
        val hasFeature     = feature != null
        // 팝업 제목: 빈 슬롯이면 '사진 추가', 채워진 슬롯이면 '얼굴 선택'(교체)
        val isAddingSample = feature != null && !feature.isSlotFilled(slotIndex)
        FacePickerDialog(
            strings        = uiState.strings,
            sourceDirUri   = uiState.sourceDirUri,
            isAddingSample = isAddingSample,
            onDismiss      = vm::closeFacePicker,
            onFacePicked   = { bitmap, face, uri ->
                if (hasFeature) {
                    // 슬롯 인덱스 포함하여 해당 슬롯만 추가/교체
                    vm.onAddFaceSample(bitmap, face, uri, listId, slotIndex)
                } else {
                    // 첫 등록 (슬롯 0)
                    vm.onFaceTouched(bitmap, face, uri, listId)
                }
            },
        )
    }

    // ── 앱 종료 확인 ──────────────────────────────────────────────────────────
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    // 뒤로 가기 → 바로 종료하지 않고 종료 확인 팝업 표시
    BackHandler { showExitDialog = true }

    if (showExitDialog) {
        ExitConfirmDialog(
            strings   = uiState.strings,
            onDismiss = { showExitDialog = false },
            onConfirm = {
                showExitDialog = false
                activity?.finishAffinity()
            },
        )
    }

    FindMyFaceScreenContent(
        modifier             = modifier,
        uiState              = uiState,
        faceFeatures         = faceFeatures,
        photoItemCounts      = photoItemCounts,
        snackbarHostState    = snackbarHostState,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToResult   = onNavigateToResult,
        onNewListNameChanged = vm::onNewListNameChanged,
        onAddList            = vm::addList,
        onDeleteList         = vm::deleteList,
        onDeleteFaceSample   = vm::deleteFaceSample,
        onOpenFacePicker     = { listId, slotIdx -> vm.openFacePicker(listId, slotIdx) },
        onScanDirClick       = { scanDirLauncher.launch(uiState.scanDirUri) },
        onStartScan = {
            if (activity != null) {
                AdManager.showInterstitialIfReady(
                    activity,
                    MainActivity.INTERSTITIAL_AD_UNIT_ID,
                    onDismissed = vm::startScan,
                )
            } else {
                vm.startScan()
            }
        },
        onStopScan             = vm::stopScan,
        onClearScanStatus      = vm::clearScanStatus,
        onCloseApp             = { showExitDialog = true },
        onRescanConfirmDelete  = vm::confirmRescanDelete,
        onRescanConfirmSkip    = vm::confirmRescanSkip,
        onDismissRescanConfirm = vm::dismissRescanConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindMyFaceScreenContent(
    uiState: MainUiState,
    faceFeatures: Map<Long, FaceFeature?>,
    photoItemCounts: Map<Long, Int>,
    snackbarHostState: SnackbarHostState,
    onNavigateToSettings: () -> Unit,
    onNavigateToResult: (Long) -> Unit,
    onNewListNameChanged: (String) -> Unit,
    onAddList: () -> Unit,
    onDeleteList: (PhotoList) -> Unit,
    onDeleteFaceSample: (Long, Int) -> Unit,   // listId, slotIndex
    onOpenFacePicker: (Long, Int) -> Unit,   // listId, slotIndex
    onScanDirClick: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onClearScanStatus: () -> Unit,
    onCloseApp: () -> Unit,
    onRescanConfirmDelete: () -> Unit,
    onRescanConfirmSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissRescanConfirm: () -> Unit,
) {
    val strings = uiState.strings

    // ── 재분류 방식 선택 다이얼로그 ────────────────────────────────────────────
    if (uiState.showRescanConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissRescanConfirm,
            title = { Text(strings.scanHasExistingTitle) },
            text  = { Text(strings.scanHasExistingMessage) },
            confirmButton = {
                Button(onClick = onRescanConfirmDelete) {
                    Text(strings.scanRestartDelete)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onRescanConfirmSkip) {
                    Text(strings.scanRestartSkip)
                }
            },
        )
    }

    Scaffold(
        modifier     = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = strings.mainTitle,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector        = Icons.Default.Settings,
                            contentDescription = strings.settings,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── 스캔 디렉토리 선택 행 ─────────────────────────────────────────
            ScanDirRow(
                label      = uiState.scanDirDisplay.ifEmpty { strings.settingScanDirHint },
                strings    = strings,
                onDirClick = onScanDirClick,
            )

            // ── 새 목록 입력 행 (목록 유무와 상관없이 항상 목록 위, 상단 고정) ──────
            NewListInputRow(
                newListName  = uiState.newListName,
                strings      = strings,
                onNameChange = onNewListNameChanged,
                onAddList    = onAddList,
            )

            // ── 목록 영역 ─────────────────────────────────────────────────────
            if (uiState.lists.isEmpty()) {
                EmptyListPlaceholder(
                    modifier = Modifier.weight(1f),
                    strings  = strings,
                )
            } else {
                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.lists,
                        key   = { it.id },
                    ) { list ->
                        PhotoListItem(
                            list          = list,
                            strings       = strings,
                            faceFeature   = faceFeatures[list.id],
                            photoCount    = photoItemCounts[list.id] ?: 0,
                            onFaceClick   = { slotIdx -> onOpenFacePicker(list.id, slotIdx) },
                            onFaceSwipeDelete = { slotIdx -> onDeleteFaceSample(list.id, slotIdx) },
                            onDeleteClick = { onDeleteList(list) },
                            onListClick   = { onNavigateToResult(list.id) },
                        )
                    }
                }
            }

            // ── 스캔 진행 상태 영역 ──────────────────────────────────────────
            ScanProgressSection(
                uiState                = uiState,
                strings                = strings,
                onStartScan            = onStartScan,
                onStopScan             = onStopScan,
                onClearScanStatus      = onClearScanStatus,
                onCloseApp             = onCloseApp,
                onRescanConfirmDelete  = onRescanConfirmDelete,
                onRescanConfirmSkip    = onRescanConfirmSkip,
                onDismissRescanConfirm = onDismissRescanConfirm,
            )
        }
    }
}

// ─── 스캔 진행 섹션 ──────────────────────────────────────────────────────────────

@Composable
private fun ScanProgressSection(
    uiState: MainUiState,
    strings: AppStrings,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onClearScanStatus: () -> Unit,
    onCloseApp: () -> Unit,
    onRescanConfirmDelete: () -> Unit,
    onRescanConfirmSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissRescanConfirm: () -> Unit,
) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 스캔 중일 때: 진행률 바 + 상태 메시지
        if (uiState.isScanning) {
            HorizontalDivider()

            // 상태 메시지
            Text(
                text     = uiState.scanStatusMessage.ifEmpty { strings.scanInProgress },
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )

            // 진행률 바 — Compose 1.6+ 람다 형식
            LinearProgressIndicator(
                progress = { uiState.scanProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            )

            // 발견된 수 표시
            if (uiState.scanResultCount > 0) {
                Text(
                    text     = strings.mainFoundCount.format(uiState.scanResultCount),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // 스캔 완료 메시지
        if (!uiState.isScanning && uiState.scanStatusMessage.isNotEmpty()) {
            HorizontalDivider()
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text     = uiState.scanStatusMessage,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearScanStatus) {
                    Text("✕")
                }
            }
        }

        // 나를 찾아줘 / 중지 버튼 — 스캔 상태에 따라 전환
        Button(
            onClick  = if (uiState.isScanning) onStopScan else onStartScan,
            modifier = Modifier.fillMaxWidth(),
            colors   = if (uiState.isScanning)
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor   = MaterialTheme.colorScheme.onError,
                )
            else ButtonDefaults.buttonColors(),
        ) {
            if (uiState.isScanning) {
                CircularProgressIndicator(
                    modifier    = Modifier
                        .size(18.dp)
                        .padding(end = 6.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onError,
                )
            }
            Text(
                text = if (uiState.isScanning) strings.mainScanStop else strings.mainFindMe,
            )
        }

        // 앱 종료 버튼
        OutlinedButton(
            onClick  = onCloseApp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = strings.closeApp)
        }

        Spacer(Modifier.height(12.dp))

        // 배너 광고
        AdBanner(
            adUnitId = BuildConfig.AD_BANNER_MAIN_ID,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))


    }
}

// ─── 스캔 폴더 행 ──────────────────────────────────────────────────────────────

@Composable
private fun ScanDirRow(
    label: String,
    strings: AppStrings,
    modifier: Modifier = Modifier,
    onDirClick: () -> Unit,
) {
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text     = "${strings.mainScanDirLabel}: $label",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onDirClick) {
            Text("🗂️")
        }
    }
}

// ─── 목록 없을 때 안내 영역 (입력 필드는 목록 위에 별도 배치) ───────────────────────

@Composable
private fun EmptyListPlaceholder(
    strings: AppStrings,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text      = strings.mainNoLists,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(top = 16.dp),
        )
    }
}

// ─── 새 목록 입력 행 ───────────────────────────────────────────────────────────

@Composable
private fun NewListInputRow(
    newListName: String,
    strings: AppStrings,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddList: () -> Unit,
) {
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value         = newListName,
            onValueChange = onNameChange,
            placeholder   = { Text(strings.mainListNameHint) },
            singleLine    = true,
            modifier      = Modifier.weight(1f),
        )
        IconButton(
            onClick  = onAddList,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = strings.mainAddList,
                tint               = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

// ─── 목록 아이템 카드 ─────────────────────────────────────────────────────────

@Composable
private fun PhotoListItem(
    list: PhotoList,
    strings: AppStrings,
    faceFeature: FaceFeature?,
    photoCount: Int,
    onFaceClick: (slotIndex: Int) -> Unit,
    onFaceSwipeDelete: (slotIndex: Int) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onListClick: () -> Unit,
) {
    Card(
        modifier  = modifier
            .fillMaxWidth()
            .clickable(onClick = onListClick),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 목록명 + 검색 결과 수
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text     = list.name,
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 등록된 임베딩 수 배지 (멀티 임베딩 등록 시 표시)
                if (!list.isUnclassified && faceFeature != null) {
                    val embCount = faceFeature.embeddingCount
                    if (embCount > 1) {
                        Text(
                            text  = strings.mainSampleRegistered.format(embCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                if (photoCount > 0) {
                    Text(
                        text  = formatPhotoCount(photoCount, strings),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (list.isScanned) {
                    Text(
                        text  = strings.scanNoResult,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 미분류 목록은 얼굴 선택·삭제 버튼 숨김
            if (!list.isUnclassified) {
                // 얼굴 샘플 슬롯 그리드 (최대 4개)
                FaceSampleGrid(
                    faceFeature = faceFeature,
                    strings     = strings,
                    onSlotClick = { slotIdx -> onFaceClick(slotIdx) },
                    onSlotSwipeDelete = onFaceSwipeDelete,
                )

                // 삭제 버튼
                IconButton(
                    onClick  = onDeleteClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.Delete,
                        contentDescription = strings.delete,
                        tint               = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// ─── 얼굴 샘플 슬롯 그리드 (4×1 가로 배치) ──────────────────────────────────

/**
 * 얼굴 샘플 슬롯 — 4×1 가로 배치
 * - 각 슬롯마다 등록된 썸네일을 개별 표시
 * - 빈 슬롯 클릭 → 새 샘플 추가
 * - 채워진 슬롯 클릭 → 해당 샘플 교체
 * - 채워진 슬롯을 좌우로 스와이프 → 해당 샘플 삭제
 * - 4개 필수 아님 — 1개만 등록해도 스캔 가능
 */
@Composable
private fun FaceSampleGrid(
    faceFeature: FaceFeature?,
    strings: AppStrings,
    onSlotSwipeDelete: (slotIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    onSlotClick: (slotIndex: Int) -> Unit,
) {
    val thumbnailPaths  = faceFeature?.getThumbnailPaths() ?: emptyList()
    // 채워진 슬롯 수 = 실제 사용자가 선택한 샘플 수 (가운데 슬롯이 삭제돼 비어 있을 수 있음)
    // embeddingCount는 augmentation으로 자동 생성된 수이므로 표시에 부적합
    val embCount = (0 until MAX_FACE_SLOTS).count { faceFeature?.isSlotFilled(it) == true }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier            = modifier,
    ) {
        // 4×1 가로 배치
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (slotIndex in 0 until MAX_FACE_SLOTS) {
                val thumbPath = thumbnailPaths.getOrElse(slotIndex) { "" }
                val isFilled  = faceFeature?.isSlotFilled(slotIndex) == true
                FaceSampleSlot(
                    isFilled      = isFilled,
                    thumbnailPath = thumbPath,
                    description   = if (isFilled) strings.mainFaceSet
                    else strings.mainFaceNotSet,
                    onSwipeDelete = { onSlotSwipeDelete(slotIndex) },
                    onClick       = { onSlotClick(slotIndex) },
                )
            }
        }
        // 샘플 수 표시
        if (embCount > 0) {
            Text(
                text  = "$embCount/${MAX_FACE_SLOTS} ${strings.faceSampleCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FaceSampleSlot(
    isFilled: Boolean,
    thumbnailPath: String,
    description: String,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 스와이프 삭제 — 채워진 슬롯만. 기준 거리 이상 밀고 놓으면 삭제
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_DELETE_THRESHOLD_DP.dp.toPx() }
    val willDelete = abs(dragOffsetX) >= swipeThresholdPx

    Box(
        modifier = modifier
            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
            .graphicsLayer { alpha = if (dragOffsetX == 0f) 1f else if (willDelete) 0.5f else 0.8f }
            .size(44.dp)                              // 2×2(28dp) → 4×1(44dp) 크기 확대
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    willDelete -> MaterialTheme.colorScheme.errorContainer
                    isFilled   -> MaterialTheme.colorScheme.primaryContainer
                    else       -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .pointerInput(isFilled, swipeThresholdPx) {
                if (!isFilled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragOffsetX) >= swipeThresholdPx) onSwipeDelete()
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount
                    },
                )
            }
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isFilled && thumbnailPath.isNotEmpty() -> {
                // 썸네일 표시 — 슬롯별 개별 이미지
                AsyncImage(
                    model              = Uri.fromFile(java.io.File(thumbnailPath)),
                    contentDescription = description,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
            isFilled -> {
                // 썸네일 없는 채워진 슬롯 (augmentation 임베딩 추가분)
                Icon(
                    imageVector        = Icons.Default.CheckCircle,
                    contentDescription = description,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(26.dp),
                )
            }
            else -> {
                // 빈 슬롯
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = description,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(22.dp),
                )
            }
        }
    }
}

private const val MAX_FACE_SLOTS = 4

/** 샘플 얼굴을 이 거리(dp) 이상 좌우로 밀고 놓으면 삭제 */
private const val SWIPE_DELETE_THRESHOLD_DP = 28

// ─── 앱 종료 확인 다이얼로그 (배너 광고 포함) ─────────────────────────────────

/** 종료 팝업 배너 광고 ID — 메인 화면 배너와 같은 BuildConfig 값 사용 (전용 단위가 생기면 별도 필드로 교체) */
private val EXIT_DIALOG_BANNER_AD_UNIT_ID: String = BuildConfig.AD_BANNER_MAIN_ID

/**
 * 앱 종료 확인 팝업
 * - 배너 광고(320dp)가 잘리지 않도록 AlertDialog 대신 전체 너비 Dialog 사용
 * - 광고는 버튼에서 가장 먼 맨 위에 배치 (실수 클릭 방지)
 */
@Composable
private fun ExitConfirmDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier       = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape          = AlertDialogDefaults.shape,
            color          = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier            = Modifier.padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 배너 광고 — 좌우 여백 없이 전체 너비 사용
                AdBanner(adUnitId = EXIT_DIALOG_BANNER_AD_UNIT_ID)

                Column(
                    modifier            = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text  = strings.closeApp,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text  = strings.exitConfirmMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                    Button(onClick = onConfirm) { Text(strings.closeApp) }
                }
            }
        }
    }
}

// ─── 얼굴 선택 다이얼로그 ─────────────────────────────────────────────────────

@Composable
private fun FacePickerDialog(
    strings: AppStrings,
    sourceDirUri: Uri?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isAddingSample: Boolean = false,
    faceAnalyzer: FaceAnalyzer = (LocalContext.current.applicationContext as LoockingForMeApp).faceAnalyzer,
    onFacePicked: (Bitmap, Face, Uri) -> Unit,
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedUri    by remember { mutableStateOf<Uri?>(null) }
    var detectedFaces  by remember { mutableStateOf<List<Face>>(emptyList()) }
    var detectedBounds by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var isDetecting    by remember { mutableStateOf(false) }

    // 소스 폴더의 사진 목록 (IO 스레드)
    val sourceImages by produceState<List<Uri>?>(initialValue = null, sourceDirUri) {
        value = if (sourceDirUri == null) emptyList()
        else withContext(Dispatchers.IO) { faceAnalyzer.listImagesInDirectory(context, sourceDirUri) }
    }

    fun onImageClick(uri: Uri) {
        selectedUri    = uri
        detectedFaces  = emptyList()
        detectedBounds = emptyList()
        isDetecting    = true
        coroutineScope.launch {
            val bmp = withContext(Dispatchers.IO) { loadBitmapWithExifCorrection(context, uri) }
            selectedBitmap = bmp
            if (bmp != null) {
                val faces = faceAnalyzer.detectFaceObjects(bmp)
                detectedFaces  = faces
                detectedBounds = faceAnalyzer.facesToBounds(faces, bmp.width, bmp.height)
            }
            isDetecting = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier         = modifier,
        title = { Text(if (isAddingSample) strings.faceAddSample else strings.facePickerTitle) },
        text  = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val bitmap = selectedBitmap
                val images = sourceImages
                when {
                    bitmap != null -> {
                        Text(
                            text = when {
                                isDetecting           -> strings.facePickerDetecting
                                detectedBounds.isEmpty() -> strings.facePickerNoFace
                                else                  -> strings.facePickerTapHint
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!isDetecting && detectedBounds.isEmpty())
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                        FaceTouchableImage(
                            bitmap        = bitmap,
                            detectedFaces = detectedBounds,
                            onTouch       = { x, y ->
                                val idx = detectedBounds.indexOfFirst { it.contains(x.toInt(), y.toInt()) }
                                val uri = selectedUri
                                if (idx >= 0 && uri != null) onFacePicked(bitmap, detectedFaces[idx], uri)
                            },
                        )
                        OutlinedButton(
                            onClick  = { selectedBitmap = null; selectedUri = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(strings.facePickerSelectPhoto) }
                    }
                    sourceDirUri == null -> Text(strings.facePickerNoSourceDir)
                    images == null       -> CircularProgressIndicator()
                    images.isEmpty()     -> Text(strings.facePickerEmptyDir)
                    else -> LazyVerticalGrid(
                        columns               = GridCells.Fixed(3),
                        modifier              = Modifier.height(300.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items = images, key = { it.toString() }) { uri ->
                            AsyncImage(
                                model              = uri,
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onImageClick(uri) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

// ─── 얼굴 터치 이미지 ─────────────────────────────────────────────────────────

/**
 * 얼굴 감지 결과를 점선 원으로 오버레이하는 이미지 컴포저블
 *
 * - detectedFaces: ML Kit가 감지한 얼굴 BoundingBox 목록 (비트맵 좌표계)
 * - 미선택 얼굴: 흰색 점선 원 (Dash 애니메이션)
 * - 선택된 얼굴: 파란 실선 원 + 반투명 배경
 * - 터치 시 비트맵 좌표로 변환 후 onTouch 콜백
 */
@Composable
private fun FaceTouchableImage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    detectedFaces: List<Rect> = emptyList(),
    onTouch: (Float, Float) -> Unit,
) {
    var renderedWidth  by remember { mutableFloatStateOf(0f) }
    var renderedHeight by remember { mutableFloatStateOf(0f) }
    // 선택된 얼굴 인덱스 (-1 = 미선택)
    var selectedFaceIndex by remember { mutableIntStateOf(-1) }

    // 점선 애니메이션 — Slack Compose Lint: label 필수
    val infiniteTransition = rememberInfiniteTransition(label = "face_dash_anim")
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "dash_phase",
    )

    val primaryColor   = MaterialTheme.colorScheme.primary
    val selectedColor  = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        // ① 사진 이미지
        Image(
            bitmap             = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    renderedWidth  = coords.size.width.toFloat()
                    renderedHeight = coords.size.height.toFloat()
                }
                .pointerInput(bitmap) {
                    detectTapGestures { offset ->
                        if (renderedWidth <= 0f || renderedHeight <= 0f) return@detectTapGestures

                        // ContentScale.Fit letterbox 보정
                        val bitmapAspect   = bitmap.width.toFloat() / bitmap.height
                        val viewAspect     = renderedWidth / renderedHeight
                        val (drawW, drawH) = if (bitmapAspect > viewAspect)
                            renderedWidth to (renderedWidth / bitmapAspect)
                        else
                            (renderedHeight * bitmapAspect) to renderedHeight
                        val ox = (renderedWidth  - drawW) / 2f
                        val oy = (renderedHeight - drawH) / 2f

                        val bitmapX = ((offset.x - ox) / drawW * bitmap.width)
                            .coerceIn(0f, bitmap.width.toFloat())
                        val bitmapY = ((offset.y - oy) / drawH * bitmap.height)
                            .coerceIn(0f, bitmap.height.toFloat())

                        // 터치한 위치가 감지된 얼굴 원 안인지 확인
                        val tappedFaceIdx = detectedFaces.indexOfFirst { rect ->
                            rect.contains(bitmapX.toInt(), bitmapY.toInt())
                        }
                        if (tappedFaceIdx >= 0) {
                            selectedFaceIndex = tappedFaceIdx
                        }
                        // 얼굴 안팎 관계없이 비트맵 좌표 콜백 (ViewModel에서 최종 판단)
                        onTouch(bitmapX, bitmapY)
                    }
                },
        )

        // ② 얼굴 오버레이 Canvas — 점선/실선 원 그리기
        if (detectedFaces.isNotEmpty() && renderedWidth > 0f && renderedHeight > 0f) {
            val bitmapAspect   = bitmap.width.toFloat() / bitmap.height
            val viewAspect     = renderedWidth / renderedHeight
            val (drawW, drawH) = if (bitmapAspect > viewAspect)
                renderedWidth to (renderedWidth / bitmapAspect)
            else
                (renderedHeight * bitmapAspect) to renderedHeight
            val ox = (renderedWidth  - drawW) / 2f
            val oy = (renderedHeight - drawH) / 2f
            val scaleX = drawW / bitmap.width
            val scaleY = drawH / bitmap.height

            Canvas(modifier = Modifier.fillMaxSize()) {
                detectedFaces.forEachIndexed { idx, rect ->
                    val isSelected = idx == selectedFaceIndex

                    // 비트맵 좌표 → 화면 좌표 변환
                    val left   = ox + rect.left   * scaleX
                    val top    = oy + rect.top    * scaleY
                    val right  = ox + rect.right  * scaleX
                    val bottom = oy + rect.bottom * scaleY
                    val cx     = (left + right)  / 2f
                    val cy     = (top  + bottom) / 2f
                    // 얼굴 영역을 감싸는 원의 반지름 (여유 10%)
                    val radius = ((right - left).coerceAtLeast(bottom - top) / 2f) * 1.1f

                    if (isSelected) {
                        // 선택된 얼굴: 반투명 채우기 + 파란 실선 원
                        drawCircle(
                            color  = selectedColor.copy(alpha = 0.20f),
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                        )
                        drawCircle(
                            color  = selectedColor,
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            style  = Stroke(width = 4f),
                        )
                    } else {
                        // 미선택 얼굴: 흰색 점선 원 (애니메이션)
                        drawCircle(
                            color  = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            style  = Stroke(
                                width       = 3f,
                                pathEffect  = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    intervals  = floatArrayOf(12f, 8f),
                                    phase      = dashPhase,
                                ),
                            ),
                        )
                        // 원 외곽에 번호 표시 (얼굴 여러 개일 때)
                        if (detectedFaces.size > 1) {
                            drawCircle(
                                color  = primaryColor,
                                radius = 14f,
                                center = androidx.compose.ui.geometry.Offset(
                                    cx + radius * 0.7f,
                                    cy - radius * 0.7f,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── 헬퍼 함수 ───────────────────────────────────────────────────────────────────

/** 사진 수 표시 문구 — 언어별 형식은 AppStrings(resultPhotoCount)에서 제공 */
private fun formatPhotoCount(count: Int, strings: AppStrings): String =
    strings.resultPhotoCount.format(count)

// ─── 이미지 로딩 헬퍼 ───────────────────────────────────────────────────────────

/**
 * EXIF 회전 정보를 반영하여 Bitmap 로드
 * - 세로/가로/뒤집힌 사진 모두 정립(upright) 상태로 반환
 * - FacePickerDialog에서 누운 사진의 얼굴이 감지되지 않는 문제 해결
 */
private fun loadBitmapWithExifCorrection(
    context: Context,
    uri: Uri,
    maxSide: Int = 2048,
): Bitmap? {
    return try {
        // 1) 크기만 먼저 읽어서 축소 비율 계산 (OOM 방지)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

        // 2) 축소하여 실제 디코딩
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it, null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } ?: return null

        // 3) EXIF 회전 보정 (기존 로직 그대로)
        val degrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        if (degrees == 0f) raw
        else Bitmap.createBitmap(
            raw, 0, 0, raw.width, raw.height,
            Matrix().apply { postRotate(degrees) }, true,
        )
    } catch (e: Exception) { null }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "ExitConfirmDialog")
@Composable
private fun ExitConfirmDialogPreview() {
    LoockingForMeTheme {
        ExitConfirmDialog(
            strings   = AppStrings.defaultStrings,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(showBackground = true, name = "FindMyFaceScreen")
@Composable
private fun FindMyFaceScreenPreview() {
    LoockingForMeTheme {
        FindMyFaceScreenContent(
            uiState = MainUiState(
                lists = listOf(
                    PhotoList(id = 1L, name = "우리 가족"),
                    PhotoList(id = 2L, name = "친구들"),
                ),
                scanDirDisplay = "DCIM/Camera",
            ),
            faceFeatures         = emptyMap(),
            photoItemCounts      = mapOf(1L to 12, 2L to 0),
            snackbarHostState    = remember { SnackbarHostState() },
            onNavigateToSettings = {},
            onNavigateToResult   = {},
            onNewListNameChanged = {},
            onAddList            = {},
            onDeleteList         = {},
            onDeleteFaceSample   = { _, _ -> },
            onOpenFacePicker     = { _, _ -> },
            onScanDirClick       = {},
            onStartScan            = {},
            onStopScan             = {},
            onClearScanStatus      = {},
            onCloseApp             = {},
            onRescanConfirmDelete  = {},
            onRescanConfirmSkip    = {},
            onDismissRescanConfirm = {},
        )
    }
}

@Preview(showBackground = true, name = "FindMyFaceScreen - Empty")
@Composable
private fun FindMyFaceScreenEmptyPreview() {
    LoockingForMeTheme {
        FindMyFaceScreenContent(
            uiState           = MainUiState(),
            faceFeatures         = emptyMap(),
            photoItemCounts      = emptyMap(),
            snackbarHostState    = remember { SnackbarHostState() },
            onNavigateToSettings = {},
            onNavigateToResult   = {},
            onNewListNameChanged = {},
            onAddList            = {},
            onDeleteList         = {},
            onDeleteFaceSample   = { _, _ -> },
            onOpenFacePicker     = { _, _ -> },
            onScanDirClick       = {},
            onStartScan            = {},
            onStopScan             = {},
            onClearScanStatus      = {},
            onCloseApp             = {},
            onRescanConfirmDelete  = {},
            onRescanConfirmSkip    = {},
            onDismissRescanConfirm = {},
        )
    }
}