package com.frankensosege.loockingforme.service

import androidx.core.net.toUri
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frankensosege.loockingforme.LoockingForMeApp
import com.frankensosege.loockingforme.MainActivity
import com.frankensosege.loockingforme.data.model.SupportedLanguage
import com.frankensosege.loockingforme.data.repository.PhotoRepository
import com.frankensosege.loockingforme.data.strings.AppStrings
import com.frankensosege.loockingforme.data.strings.StringsRepository
import com.frankensosege.loockingforme.ui.setting.SettingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 얼굴 스캔 Foreground Service
 *
 * ★ Binder 패턴으로 ViewModel과 연결
 *   - 진행률/상태를 StateFlow로 실시간 제공
 *   - stopScan() 호출 시 코루틴 취소 → 그때까지 저장된 결과 유지
 *   - 화면이 꺼지거나 다른 앱으로 전환해도 계속 동작
 */
class FaceScanService : Service() {

    // ─── Binder ──────────────────────────────────────────────────────────────

    inner class ScanBinder : Binder() {
        fun getService(): FaceScanService = this@FaceScanService
    }

    private val binder = ScanBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    // ─── 상태 Flow (ViewModel이 collect) ────────────────────────────────────

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    sealed class ScanState {
        data object Idle : ScanState()
        data class Running(
            val progress: Float,
            val scanned: Int,
            val total: Int,
            val found: Int,
        ) : ScanState()
        data class Stopped(val found: Int) : ScanState()   // 중지
        data class Completed(val found: Int) : ScanState() // 완료
        data class Error(val message: String) : ScanState()
    }

    // ─── 내부 상태 ────────────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private lateinit var repository: PhotoRepository
    private lateinit var notificationManager: NotificationManager

    /** 알림/상태 문구 — 시작 시 설정된 언어로 로드 (로드 전에는 기본값) */
    @Volatile private var strings: AppStrings = AppStrings.defaultStrings

    // ─── 생명주기 ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val app = application as LoockingForMeApp
        val db  = app.appDatabase
        repository = PhotoRepository(
            context        = applicationContext,
            photoListDao   = db.photoListDao(),
            photoItemDao   = db.photoItemDao(),
            faceFeatureDao = db.faceFeatureDao(),
            faceAnalyzer   = app.faceAnalyzer,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uriString      = intent.getStringExtra(EXTRA_SCAN_DIR_URI) ?: return START_NOT_STICKY
                val threshold      = intent.getFloatExtra(
                    EXTRA_THRESHOLD,
                    SettingUiState.DEFAULT_SIMILARITY_THRESHOLD / 100f,
                )
                val deleteExisting = intent.getBooleanExtra(EXTRA_DELETE_EXISTING, true)
                serviceScope.launch {
                    // 설정된 언어의 문구를 먼저 로드한 뒤 스캔 시작 (알림 문구에 사용)
                    loadStrings()
                    startScan(uriString.toUri(), threshold, deleteExisting)
                }
            }
            ACTION_STOP -> stopScan()
        }
        return START_NOT_STICKY
    }

    /** 설정된 언어의 문구를 DB에서 읽어 알림 채널까지 갱신 */
    private suspend fun loadStrings() {
        try {
            val app  = application as LoockingForMeApp
            val prefs = getSharedPreferences("lfmf_settings", Context.MODE_PRIVATE)
            val lang = prefs.getString("language_code", SupportedLanguage.KOREAN.code)
                ?: SupportedLanguage.KOREAN.code
            strings = StringsRepository(app.stringsDatabase.stringsDao()).getStringsOnce(lang)
            createNotificationChannel()
        } catch (e: Exception) {
            android.util.Log.w("FaceScanService", "문구 로드 실패, 기본값 사용: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ─── 스캔 시작 ────────────────────────────────────────────────────────────

    fun startScan(scanDirUri: Uri, threshold: Float, deleteExisting: Boolean = true) {
        if (scanJob?.isActive == true) return  // 중복 실행 방지

        startForeground(NOTIFICATION_ID, buildNotification(progress = 0, scanned = 0, total = 0))

        var totalFound = 0

        scanJob = serviceScope.launch {
            try {
                val lists = repository.getAllLists().first()
                repository.scanAndClassify(
                    lists          = lists,
                    scanDirUri     = scanDirUri,
                    threshold      = threshold,
                    deleteExisting = deleteExisting,
                    onProgress = { progress, scanned, total ->
                        _scanState.value = ScanState.Running(progress, scanned, total, totalFound)
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification(
                                progress = (progress * 100).toInt(),
                                scanned  = scanned,
                                total    = total,
                            ),
                        )
                    },
                    onFound = { count ->
                        totalFound += count
                        // 현재 Running 상태라면 found 갱신
                        (_scanState.value as? ScanState.Running)?.let {
                            _scanState.value = it.copy(found = totalFound)
                        }
                    },
                )
                // 정상 완료
                _scanState.value = ScanState.Completed(totalFound)
                notificationManager.notify(NOTIFICATION_ID, buildCompletedNotification(totalFound))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // 중지 버튼에 의한 취소 — 정상 흐름
                    _scanState.value = ScanState.Stopped(totalFound)
                    notificationManager.notify(NOTIFICATION_ID, buildStoppedNotification(totalFound))
                } else {
                    _scanState.value = ScanState.Error(e.message ?: strings.scanUnknownError)
                }
            } finally {
                // 완료 / 중지 / 오류 어느 경우든, 이번 스캔으로 분류된 사진을 미분류 목록에서 제거한다.
                // (ViewModel 연결 여부와 무관하게 서비스에서 보장 — 앱을 벗어나 있어도 정리됨)
                // 취소된 코루틴에서도 끝까지 실행되도록 NonCancellable 사용
                withContext(NonCancellable) {
                    try {
                        repository.pruneUnclassified()
                    } catch (e: Exception) {
                        android.util.Log.w("FaceScanService", "미분류 정리 실패: ${e.message}")
                    }
                }
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    // ─── 스캔 중지 (그때까지 결과 보존) ────────────────────────────────────────

    fun stopScan() {
        scanJob?.cancel()   // → CancellationException 발생 → finally에서 처리
    }

    // ─── 알림 ────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.notifChannelName,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = strings.notifChannelDesc
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun mainActivityPendingIntent() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    /** 중지 PendingIntent — 알림에서 직접 중지 가능 */
    private fun stopPendingIntent(): PendingIntent {
        val stopIntent = Intent(this, FaceScanService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun buildNotification(progress: Int, scanned: Int, total: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${strings.appName} — ${strings.notifSearching}")
            .setContentText(
                if (total > 0) strings.notifProgressText.format(scanned, total, progress)
                else strings.notifPreparing
            )
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setProgress(100, progress, total == 0)
            .setContentIntent(mainActivityPendingIntent())
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // 알림에서 중지 버튼 제공
            .addAction(
                android.R.drawable.ic_media_pause,
                strings.notifStop,
                stopPendingIntent(),
            )
            .build()

    private fun buildCompletedNotification(found: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${strings.appName} — ${strings.notifCompleted}")
            .setContentText(strings.notifFoundText.format(found))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .build()

    private fun buildStoppedNotification(found: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${strings.appName} — ${strings.notifStopped}")
            .setContentText(strings.notifStoppedText.format(found))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .build()

    companion object {
        private const val CHANNEL_ID      = "face_scan_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_SCAN_DIR_URI      = "extra_scan_dir_uri"
        const val EXTRA_THRESHOLD         = "extra_threshold"
        const val EXTRA_DELETE_EXISTING   = "extra_delete_existing"
        const val ACTION_START            = "action_start"
        const val ACTION_STOP             = "action_stop"

        fun buildStartIntent(
            context: Context,
            scanDirUri: Uri,
            threshold: Float,
            deleteExisting: Boolean = true,
        ): Intent = Intent(context, FaceScanService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_SCAN_DIR_URI,    scanDirUri.toString())
            putExtra(EXTRA_THRESHOLD,       threshold)
            putExtra(EXTRA_DELETE_EXISTING, deleteExisting)
        }

        fun buildStopIntent(context: Context): Intent =
            Intent(context, FaceScanService::class.java).apply {
                action = ACTION_STOP
            }
    }
}