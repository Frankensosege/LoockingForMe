package com.frankensosege.loockingforme.data.strings

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// ─── DAO ──────────────────────────────────────────────────────────────────────

@Dao
interface AppStringsDao {

    @Query("SELECT * FROM app_strings WHERE language_code = :code LIMIT 1")
    fun getStringsByLanguage(code: String): Flow<AppStrings?>

    @Query("SELECT * FROM app_strings WHERE language_code = :code LIMIT 1")
    suspend fun getStringsByLanguageOnce(code: String): AppStrings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg strings: AppStrings)

    @Query("SELECT COUNT(*) FROM app_strings")
    suspend fun count(): Int
}

// ─── Database ─────────────────────────────────────────────────────────────────

/**
 * 다국어 문자열 Room DB (v2)
 * - 언어 코드를 PK로 사용하여 4개 언어 행 저장
 */
@Database(
    entities  = [AppStrings::class],
    version   = 10,
    exportSchema = true,
)
abstract class StringsDatabase : RoomDatabase() {

    abstract fun stringsDao(): AppStringsDao

    companion object {
        @Volatile
        private var INSTANCE: StringsDatabase? = null

        fun getInstance(context: Context): StringsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StringsDatabase::class.java,
                    "lfmf_strings.db",
                )
                    .addMigrations(
                        // v2 → v3: setting_similarity_threshold, setting_similarity_hint 컬럼 추가
                        object : androidx.room.migration.Migration(2, 3) {
                            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN setting_similarity_threshold TEXT NOT NULL DEFAULT '분류 유사도'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN setting_similarity_hint TEXT NOT NULL DEFAULT '유사도 기준값 (50 ~ 99%)'")
                            }
                        },
                        object : androidx.room.migration.Migration(3, 4) {
                            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_select_all TEXT NOT NULL DEFAULT '전체 선택'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_deselect_all TEXT NOT NULL DEFAULT '선택 해제'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_delete_selected TEXT NOT NULL DEFAULT '삭제'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_share_bluetooth TEXT NOT NULL DEFAULT '블루투스 공유'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_selected_count TEXT NOT NULL DEFAULT '개 선택됨'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_delete_confirm TEXT NOT NULL DEFAULT '선택한 사진을 목록에서 삭제하시겠습니까?'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_long_press_hint TEXT NOT NULL DEFAULT '사진을 길게 눌러 선택하세요'")
                            }
                        },
                        object : androidx.room.migration.Migration(4, 5) {
                            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN unclassified_list_name TEXT NOT NULL DEFAULT '미분류'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_reclassify TEXT NOT NULL DEFAULT '재분류'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_reclassify_title TEXT NOT NULL DEFAULT '재분류할 목록 선택'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_reclassify_hint TEXT NOT NULL DEFAULT '이동할 목록을 선택하세요'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_reclassify_save TEXT NOT NULL DEFAULT '저장'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_reclassify_confirm TEXT NOT NULL DEFAULT '선택한 목록으로 재분류하시겠습니까?'")
                            }
                        }
                        ,
                        object : androidx.room.migration.Migration(5, 6) {
                            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN scan_has_existing_title TEXT NOT NULL DEFAULT '이미 분류된 사진이 있습니다'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN scan_has_existing_message TEXT NOT NULL DEFAULT '분류된 목록이 있습니다'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN scan_restart_delete TEXT NOT NULL DEFAULT '예 (전체 재분류)'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN scan_restart_skip TEXT NOT NULL DEFAULT '아니오 (미분류만)'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN face_add_sample TEXT NOT NULL DEFAULT '사진 추가'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN face_sample_count TEXT NOT NULL DEFAULT '샘플'")
                            }
                        }
                        ,
                        object : androidx.room.migration.Migration(6, 7) {
                            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN update_ready_msg TEXT NOT NULL DEFAULT '새 버전 다운로드가 완료되었습니다. 지금 재시작해서 업데이트를 적용할까요?'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN update_restart TEXT NOT NULL DEFAULT '재시작'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN update_later TEXT NOT NULL DEFAULT '나중에'")
                            }
                        }
                        ,
                        object : androidx.room.migration.Migration(7, 8) {
                            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                                // 앱 종료 확인 문구 — 기본값(한국어) 추가 후 언어별 값 갱신
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN exit_confirm_message TEXT NOT NULL DEFAULT '앱을 종료하시겠습니까?'")
                                database.execSQL("UPDATE app_strings SET exit_confirm_message = 'Do you want to exit the app?' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET exit_confirm_message = 'アプリを終了しますか？' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET exit_confirm_message = '要退出应用吗？' WHERE language_code = 'zh'")
                            }
                        }
                        ,
                        object : androidx.room.migration.Migration(8, 9) {
                            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                                // 스캔 상태/알림/결과·설정 화면 문구 — 기본값(한국어) 추가 후 언어별 값 갱신
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN main_scan_stop TEXT NOT NULL DEFAULT '■ 중지'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN main_found_count TEXT NOT NULL DEFAULT '발견: %1\$d장'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN main_sample_registered TEXT NOT NULL DEFAULT '📸 샘플 %1\$d개 등록됨'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_photo_count TEXT NOT NULL DEFAULT '검색된 사진: %1\$d장'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN scan_stopped_status TEXT NOT NULL DEFAULT '중지됨 — %1\$d장 저장됨'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN scan_unknown_error TEXT NOT NULL DEFAULT '알 수 없는 오류'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_channel_name TEXT NOT NULL DEFAULT '사진 스캔'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_channel_desc TEXT NOT NULL DEFAULT '얼굴 인식 사진 스캔 진행 알림'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_searching TEXT NOT NULL DEFAULT '사진 검색 중'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_completed TEXT NOT NULL DEFAULT '검색 완료'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_stopped TEXT NOT NULL DEFAULT '검색 중지됨'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_progress_text TEXT NOT NULL DEFAULT '%1\$d / %2\$d 처리 중 (%3\$d%%)'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_preparing TEXT NOT NULL DEFAULT '준비 중...'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_stop TEXT NOT NULL DEFAULT '중지'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_found_text TEXT NOT NULL DEFAULT '%1\$d장의 사진을 찾았습니다.'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN notif_stopped_text TEXT NOT NULL DEFAULT '%1\$d장까지 저장되었습니다.'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_reclassify_empty TEXT NOT NULL DEFAULT '이동할 수 있는 목록이 없습니다.'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_item_selected TEXT NOT NULL DEFAULT '선택됨'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN result_item_not_selected TEXT NOT NULL DEFAULT '미선택'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN setting_current_value TEXT NOT NULL DEFAULT '현재: %1\$d%%'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN setting_similarity_guide TEXT NOT NULL DEFAULT '• 높을수록 엄격하게 분류 (99% = 거의 동일 인물만)\n\n• 낮을수록 넓게 분류 (50% = 유사한 인물 포함)'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN setting_error_not_number TEXT NOT NULL DEFAULT '숫자를 입력하세요'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN setting_error_min TEXT NOT NULL DEFAULT '%1\$d 이상 입력하세요'")
                                database.execSQL("ALTER TABLE app_strings ADD COLUMN setting_error_max TEXT NOT NULL DEFAULT '%1\$d 이하 입력하세요'")
                                database.execSQL("UPDATE app_strings SET main_scan_stop = '■ Stop' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET main_found_count = 'Found: %1\$d' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET main_sample_registered = '📸 %1\$d samples registered' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET result_photo_count = 'Photos found: %1\$d' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET scan_stopped_status = 'Stopped — %1\$d saved' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET scan_unknown_error = 'Unknown error' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_channel_name = 'Photo scan' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_channel_desc = 'Face recognition photo scan progress' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_searching = 'Searching photos' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_completed = 'Search complete' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_stopped = 'Search stopped' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_progress_text = '%1\$d / %2\$d processed (%3\$d%%)' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_preparing = 'Preparing...' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_stop = 'Stop' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_found_text = 'Found %1\$d photo(s).' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET notif_stopped_text = '%1\$d saved so far.' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET result_reclassify_empty = 'No lists available to move to.' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET result_item_selected = 'Selected' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET result_item_not_selected = 'Not selected' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET setting_current_value = 'Current: %1\$d%%' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET setting_similarity_guide = '• Higher = stricter (99% = nearly identical person only)\n\n• Lower = broader (50% = similar people included)' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET setting_error_not_number = 'Enter a number' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET setting_error_min = 'Enter %1\$d or more' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET setting_error_max = 'Enter %1\$d or less' WHERE language_code = 'en'")
                                database.execSQL("UPDATE app_strings SET main_scan_stop = '■ 停止' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET main_found_count = '見つかった: %1\$d枚' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET main_sample_registered = '📸 サンプル%1\$d件登録済み' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET result_photo_count = '見つかった写真: %1\$d枚' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET scan_stopped_status = '停止しました — %1\$d枚保存' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET scan_unknown_error = '不明なエラー' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_channel_name = '写真スキャン' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_channel_desc = '顔認識による写真スキャンの進行状況' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_searching = '写真を検索中' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_completed = '検索完了' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_stopped = '検索を中止しました' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_progress_text = '%1\$d / %2\$d 件処理中 (%3\$d%%)' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_preparing = '準備中...' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_stop = '停止' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_found_text = '%1\$d枚の写真が見つかりました。' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET notif_stopped_text = '%1\$d枚まで保存されました。' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET result_reclassify_empty = '移動できるリストがありません。' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET result_item_selected = '選択済み' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET result_item_not_selected = '未選択' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET setting_current_value = '現在: %1\$d%%' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET setting_similarity_guide = '• 高いほど厳密に分類 (99% = ほぼ同一人物のみ)\n\n• 低いほど広く分類 (50% = 似た人物も含む)' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET setting_error_not_number = '数字を入力してください' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET setting_error_min = '%1\$d以上を入力してください' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET setting_error_max = '%1\$d以下を入力してください' WHERE language_code = 'ja'")
                                database.execSQL("UPDATE app_strings SET main_scan_stop = '■ 停止' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET main_found_count = '已找到: %1\$d张' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET main_sample_registered = '📸 已登记%1\$d个样本' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET result_photo_count = '找到照片: %1\$d张' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET scan_stopped_status = '已停止 — 已保存%1\$d张' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET scan_unknown_error = '未知错误' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_channel_name = '照片扫描' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_channel_desc = '人脸识别照片扫描进度通知' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_searching = '正在搜索照片' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_completed = '搜索完成' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_stopped = '搜索已停止' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_progress_text = '已处理 %1\$d / %2\$d (%3\$d%%)' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_preparing = '准备中...' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_stop = '停止' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_found_text = '找到了%1\$d张照片。' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET notif_stopped_text = '已保存%1\$d张。' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET result_reclassify_empty = '没有可移动到的列表。' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET result_item_selected = '已选择' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET result_item_not_selected = '未选择' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET setting_current_value = '当前: %1\$d%%' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET setting_similarity_guide = '• 越高越严格 (99% = 仅几乎相同的人)\n\n• 越低越宽松 (50% = 包含相似的人)' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET setting_error_not_number = '请输入数字' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET setting_error_min = '请输入不小于%1\$d的值' WHERE language_code = 'zh'")
                                database.execSQL("UPDATE app_strings SET setting_error_max = '请输入不大于%1\$d的值' WHERE language_code = 'zh'")
                            }
                        }
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { db ->
                        INSTANCE = db
                    }
            }
    }
}

// ─── 초기 데이터 (4개 언어) ───────────────────────────────────────────────────

/** DB가 비어 있을 때 삽입할 기본 문자열 집합 */
val initialStrings: Array<AppStrings> = arrayOf(

    // ── 한국어 ────────────────────────────────────────────────────────────────
    AppStrings(
        languageCode         = "ko",
        appName              = "나를 찾아다오",
        saveAndStart         = "저장하고 시작",
        settings             = "설정",
        closeApp             = "앱 종료",
        cancel               = "취소",
        delete               = "삭제",
        settingTitle         = "앱 설정",
        settingLanguage      = "언어 선택",
        settingSourceDir     = "소스 사진 폴더",
        settingSourceDirHint = "얼굴을 선택할 사진이 있는 폴더",
        settingScanDir       = "탐색 대상 폴더",
        settingScanDirHint   = "사진을 검색할 폴더",
        settingSelectFolder       = "폴더 선택",
        settingSimilarityThreshold = "분류 유사도",
        settingSimilarityHint      = "유사도 기준값 (50 ~ 99%)",
        mainTitle            = "나를 찾아다오",
        mainAddList          = "목록 추가",
        mainListNameHint     = "목록 이름 입력",
        mainFindMe           = "나를 찾아줘",
        mainNoLists          = "목록이 없습니다. + 버튼을 눌러 추가하세요.",
        mainScanDirLabel     = "찾을 사진 폴더",
        mainFaceNotSet       = "얼굴 미설정",
        mainFaceSet          = "얼굴 설정됨",
        facePickerTitle      = "얼굴 선택",
        facePickerTapHint    = "찾을 얼굴을 터치하세요",
        facePickerNoFace     = "감지된 얼굴 없음",
        facePickerSelectPhoto = "사진 선택",
        facePickerDetecting  = "얼굴 감지 중...",
        facePickerNoSourceDir = "설정에서 소스 사진 폴더를 먼저 지정하세요.",
        facePickerEmptyDir   = "폴더에 사진이 없습니다.",
        scanInProgress       = "사진 검색 중...",
        scanComplete         = "검색 완료",
        scanNoResult         = "일치하는 사진이 없습니다",
        resultTitle          = "검색 결과",
        resultBack           = "메인으로 돌아가기",
        resultEmpty          = "검색된 사진이 없습니다",
        resultSelectAll      = "전체 선택",
        resultDeselectAll    = "선택 해제",
        resultDeleteSelected = "삭제",
        resultShareBluetooth = "블루투스 공유",
        resultSelectedCount  = "개 선택됨",
        resultDeleteConfirm  = "선택한 사진을 목록에서 삭제하시겠습니까?\n(실제 파일은 삭제되지 않습니다)",
        resultLongPressHint  = "사진을 길게 눌러 선택하세요",
        scanHasExistingTitle     = "이미 분류된 사진이 있습니다",
        scanHasExistingMessage   = "분류된 목록이 있습니다. 삭제하고 처음부터 분류하시겠습니까?",
        scanRestartDelete        = "예 (전체 재분류)",
        scanRestartSkip          = "아니오 (미분류만)",
        faceAddSample            = "사진 추가",
        faceSampleCount          = "샘플",
        unclassifiedListName     = "미분류",
        resultReclassify         = "재분류",
        resultReclassifyTitle    = "재분류할 목록 선택",
        resultReclassifyHint     = "이동할 목록을 선택하세요 (현재 분류는 삭제됩니다)",
        resultReclassifySave     = "저장",
        permissionRequired   = "권한이 필요합니다",
        permissionGoSettings = "설정으로 이동",
        permissionRationale  = "사진 접근 권한이 필요합니다. 설정에서 권한을 허용해 주세요.",

        updateReadyMsg       = "새 버전 다운로드가 완료되었습니다. 지금 재시작해서 업데이트를 적용할까요?",
        updateRestart        = "재시작",
        updateLater          = "나중에",

        exitConfirmMessage   = "앱을 종료하시겠습니까?",

        mainScanStop = "■ 중지",
        mainFoundCount = "발견: %1\$d장",
        mainSampleRegistered = "📸 샘플 %1\$d개 등록됨",
        resultPhotoCount = "검색된 사진: %1\$d장",
        scanStoppedStatus = "중지됨 — %1\$d장 저장됨",
        scanUnknownError = "알 수 없는 오류",
        notifChannelName = "사진 스캔",
        notifChannelDesc = "얼굴 인식 사진 스캔 진행 알림",
        notifSearching = "사진 검색 중",
        notifCompleted = "검색 완료",
        notifStopped = "검색 중지됨",
        notifProgressText = "%1\$d / %2\$d 처리 중 (%3\$d%%)",
        notifPreparing = "준비 중...",
        notifStop = "중지",
        notifFoundText = "%1\$d장의 사진을 찾았습니다.",
        notifStoppedText = "%1\$d장까지 저장되었습니다.",
        resultReclassifyEmpty = "이동할 수 있는 목록이 없습니다.",
        resultItemSelected = "선택됨",
        resultItemNotSelected = "미선택",
        settingCurrentValue = "현재: %1\$d%%",
        settingSimilarityGuide = "• 높을수록 엄격하게 분류 (99% = 거의 동일 인물만)\n\n• 낮을수록 넓게 분류 (50% = 유사한 인물 포함)",
        settingErrorNotNumber = "숫자를 입력하세요",
        settingErrorMin = "%1\$d 이상 입력하세요",
        settingErrorMax = "%1\$d 이하 입력하세요",
    ),

    // ── 영어 ──────────────────────────────────────────────────────────────────
    AppStrings(
        languageCode         = "en",
        appName              = "Looking For Me",
        saveAndStart         = "Save & Start",
        settings             = "Settings",
        closeApp             = "Exit App",
        cancel               = "Cancel",
        delete               = "Delete",
        settingTitle         = "App Settings",
        settingLanguage      = "Language",
        settingSourceDir     = "Source Photo Folder",
        settingSourceDirHint = "Folder containing photos for face selection",
        settingScanDir       = "Scan Target Folder",
        settingScanDirHint   = "Folder to search for photos",
        settingSelectFolder       = "Select Folder",
        settingSimilarityThreshold = "Similarity Threshold",
        settingSimilarityHint      = "Match threshold (50 ~ 99%)",
        mainTitle            = "Looking For Me",
        mainAddList          = "Add List",
        mainListNameHint     = "Enter list name",
        mainFindMe           = "Find Me",
        mainNoLists          = "No lists yet. Tap + to add one.",
        mainScanDirLabel     = "Photo Scan Folder",
        mainFaceNotSet       = "Face not set",
        mainFaceSet          = "Face set",
        facePickerTitle      = "Select Face",
        facePickerTapHint    = "Tap on the face to search",
        facePickerNoFace     = "No face detected",
        facePickerSelectPhoto = "Select Photo",
        facePickerDetecting  = "Detecting faces...",
        facePickerNoSourceDir = "Set a source photo folder in Settings first.",
        facePickerEmptyDir   = "No photos in this folder.",
        scanInProgress       = "Searching photos...",
        scanComplete         = "Search complete",
        scanNoResult         = "No matching photos found",
        resultTitle          = "Search Results",
        resultBack           = "Back to Main",
        resultEmpty          = "No photos found",
        resultSelectAll      = "Select All",
        resultDeselectAll    = "Deselect All",
        resultDeleteSelected = "Delete",
        resultShareBluetooth = "Share via Bluetooth",
        resultSelectedCount  = " selected",
        resultDeleteConfirm  = "Remove selected photos from the list?\n(The actual files will not be deleted)",
        resultLongPressHint  = "Long press a photo to select",
        scanHasExistingTitle     = "Already classified photos exist",
        scanHasExistingMessage   = "Some photos are already classified. Delete and reclassify all?",
        scanRestartDelete        = "Yes (Full Reclassify)",
        scanRestartSkip          = "No (Skip classified)",
        faceAddSample            = "Add Photo",
        faceSampleCount          = "samples",
        unclassifiedListName     = "Unclassified",
        resultReclassify         = "Reclassify",
        resultReclassifyTitle    = "Select target list",
        resultReclassifyHint     = "Select list to move (current classification will be removed)",
        resultReclassifySave     = "Save",
        permissionRequired   = "Permission Required",
        permissionGoSettings = "Go to Settings",
        permissionRationale  = "Photo access permission is required. Please allow it in Settings.",

        updateReadyMsg       = "A new version has been downloaded. Restart now to apply the update?",
        updateRestart        = "Restart",
        updateLater          = "Later",

        exitConfirmMessage   = "Do you want to exit the app?",

        mainScanStop = "■ Stop",
        mainFoundCount = "Found: %1\$d",
        mainSampleRegistered = "📸 %1\$d samples registered",
        resultPhotoCount = "Photos found: %1\$d",
        scanStoppedStatus = "Stopped — %1\$d saved",
        scanUnknownError = "Unknown error",
        notifChannelName = "Photo scan",
        notifChannelDesc = "Face recognition photo scan progress",
        notifSearching = "Searching photos",
        notifCompleted = "Search complete",
        notifStopped = "Search stopped",
        notifProgressText = "%1\$d / %2\$d processed (%3\$d%%)",
        notifPreparing = "Preparing...",
        notifStop = "Stop",
        notifFoundText = "Found %1\$d photo(s).",
        notifStoppedText = "%1\$d saved so far.",
        resultReclassifyEmpty = "No lists available to move to.",
        resultItemSelected = "Selected",
        resultItemNotSelected = "Not selected",
        settingCurrentValue = "Current: %1\$d%%",
        settingSimilarityGuide = "• Higher = stricter (99% = nearly identical person only)\n\n• Lower = broader (50% = similar people included)",
        settingErrorNotNumber = "Enter a number",
        settingErrorMin = "Enter %1\$d or more",
        settingErrorMax = "Enter %1\$d or less",
    ),

    // ── 일본어 ────────────────────────────────────────────────────────────────
    AppStrings(
        languageCode         = "ja",
        appName              = "私を探して",
        saveAndStart         = "保存して開始",
        settings             = "設定",
        closeApp             = "アプリを終了",
        cancel               = "キャンセル",
        delete               = "削除",
        settingTitle         = "アプリ設定",
        settingLanguage      = "言語選択",
        settingSourceDir     = "ソース写真フォルダ",
        settingSourceDirHint = "顔を選ぶ写真があるフォルダ",
        settingScanDir       = "スキャン対象フォルダ",
        settingScanDirHint   = "写真を検索するフォルダ",
        settingSelectFolder       = "フォルダを選択",
        settingSimilarityThreshold = "分類の類似度",
        settingSimilarityHint      = "類似度の基準値 (50 ~ 99%)",
        mainTitle            = "私を探して",
        mainAddList          = "リストを追加",
        mainListNameHint     = "リスト名を入力",
        mainFindMe           = "私を探して",
        mainNoLists          = "リストがありません。+ ボタンで追加してください。",
        mainScanDirLabel     = "写真スキャンフォルダ",
        mainFaceNotSet       = "顔未設定",
        mainFaceSet          = "顔設定済み",
        facePickerTitle      = "顔を選択",
        facePickerTapHint    = "検索したい顔をタップしてください",
        facePickerNoFace     = "顔が検出されませんでした",
        facePickerSelectPhoto = "写真を選択",
        facePickerDetecting  = "顔を検出中...",
        facePickerNoSourceDir = "設定でソース写真フォルダを指定してください.",
        facePickerEmptyDir   = "フォルダに写真がありません.",
        scanInProgress       = "写真を検索中...",
        scanComplete         = "検索完了",
        scanNoResult         = "一致する写真が見つかりません",
        resultTitle          = "検索結果",
        resultBack           = "メイン画面に戻る",
        resultEmpty          = "写真が見つかりませんでした",
        resultSelectAll      = "全て選択",
        resultDeselectAll    = "選択解除",
        resultDeleteSelected = "削除",
        resultShareBluetooth = "Bluetooth共有",
        resultSelectedCount  = "件選択中",
        resultDeleteConfirm  = "選択した写真をリストから削除しますか?\n(実際のファイルは削除されません)",
        resultLongPressHint  = "写真を長押しして選択してください",
        scanHasExistingTitle     = "分類済みの写真があります",
        scanHasExistingMessage   = "既に分類済みの写真があります。削除して再分類しますか?",
        scanRestartDelete        = "はい（全て再分類）",
        scanRestartSkip          = "いいえ（未分類のみ）",
        faceAddSample            = "写真追加",
        faceSampleCount          = "サンプル",
        unclassifiedListName     = "未分類",
        resultReclassify         = "再分類",
        resultReclassifyTitle    = "再分類するリストを選択",
        resultReclassifyHint     = "移動先のリストを選択してください",
        resultReclassifySave     = "保存",
        permissionRequired   = "権限が必要です",
        permissionGoSettings = "設定へ移動",
        permissionRationale  = "写真へのアクセス権限が必要です。設定で許可してください。",

        updateReadyMsg       = "新しいバージョンのダウンロードが完了しました。今すぐ再起動して更新を適用しますか？",
        updateRestart        = "再起動",
        updateLater          = "あとで",

        exitConfirmMessage   = "アプリを終了しますか？",

        mainScanStop = "■ 停止",
        mainFoundCount = "見つかった: %1\$d枚",
        mainSampleRegistered = "📸 サンプル%1\$d件登録済み",
        resultPhotoCount = "見つかった写真: %1\$d枚",
        scanStoppedStatus = "停止しました — %1\$d枚保存",
        scanUnknownError = "不明なエラー",
        notifChannelName = "写真スキャン",
        notifChannelDesc = "顔認識による写真スキャンの進行状況",
        notifSearching = "写真を検索中",
        notifCompleted = "検索完了",
        notifStopped = "検索を中止しました",
        notifProgressText = "%1\$d / %2\$d 件処理中 (%3\$d%%)",
        notifPreparing = "準備中...",
        notifStop = "停止",
        notifFoundText = "%1\$d枚の写真が見つかりました。",
        notifStoppedText = "%1\$d枚まで保存されました。",
        resultReclassifyEmpty = "移動できるリストがありません。",
        resultItemSelected = "選択済み",
        resultItemNotSelected = "未選択",
        settingCurrentValue = "現在: %1\$d%%",
        settingSimilarityGuide = "• 高いほど厳密に分類 (99% = ほぼ同一人物のみ)\n\n• 低いほど広く分類 (50% = 似た人物も含む)",
        settingErrorNotNumber = "数字を入力してください",
        settingErrorMin = "%1\$d以上を入力してください",
        settingErrorMax = "%1\$d以下を入力してください",
    ),

    // ── 중국어 ────────────────────────────────────────────────────────────────
    AppStrings(
        languageCode         = "zh",
        appName              = "找到我",
        saveAndStart         = "保存并开始",
        settings             = "设置",
        closeApp             = "退出应用",
        cancel               = "取消",
        delete               = "删除",
        settingTitle         = "应用设置",
        settingLanguage      = "选择语言",
        settingSourceDir     = "源照片文件夹",
        settingSourceDirHint = "包含用于选择人脸的照片的文件夹",
        settingScanDir       = "扫描目标文件夹",
        settingScanDirHint   = "要搜索照片的文件夹",
        settingSelectFolder       = "选择文件夹",
        settingSimilarityThreshold = "分类相似度",
        settingSimilarityHint      = "相似度基准值 (50 ~ 99%)",
        mainTitle            = "找到我",
        mainAddList          = "添加列表",
        mainListNameHint     = "输入列表名称",
        mainFindMe           = "找到我",
        mainNoLists          = "没有列表。请点击 + 添加。",
        mainScanDirLabel     = "照片扫描文件夹",
        mainFaceNotSet       = "未设置人脸",
        mainFaceSet          = "已设置人脸",
        facePickerTitle      = "选择人脸",
        facePickerTapHint    = "请点击要搜索的人脸",
        facePickerNoFace     = "未检测到人脸",
        facePickerSelectPhoto = "选择照片",
        facePickerDetecting  = "正在检测人脸...",
        facePickerNoSourceDir = "请先在设置中指定源照片文件夹.",
        facePickerEmptyDir   = "文件夹中没有照片.",
        scanInProgress       = "正在搜索照片...",
        scanComplete         = "搜索完成",
        scanNoResult         = "未找到匹配的照片",
        resultTitle          = "搜索结果",
        resultBack           = "返回主界面",
        resultEmpty          = "未找到照片",
        resultSelectAll      = "全选",
        resultDeselectAll    = "取消选择",
        resultDeleteSelected = "删除",
        resultShareBluetooth = "蓝牙共享",
        resultSelectedCount  = "项已选",
        resultDeleteConfirm  = "要从列表中删除选中的照片吗?\n(实际文件不会被删除)",
        resultLongPressHint  = "长按照片进行选择",
        scanHasExistingTitle     = "已有分类的照片",
        scanHasExistingMessage   = "已有分类照片。是否删除并重新分类?",
        scanRestartDelete        = "是（全部重新分类）",
        scanRestartSkip          = "否（仅未分类）",
        faceAddSample            = "添加照片",
        faceSampleCount          = "样本",
        unclassifiedListName     = "未分类",
        resultReclassify         = "重新分类",
        resultReclassifyTitle    = "选择目标列表",
        resultReclassifyHint     = "选择要移动到的列表（当前分类将被删除）",
        resultReclassifySave     = "保存",
        permissionRequired   = "需要权限",
        permissionGoSettings = "前往设置",
        permissionRationale  = "需要访问照片的权限。请在设置中允许。",

        updateReadyMsg       = "新版本已下载完成。现在重启以应用更新吗？",
        updateRestart        = "重启",
        updateLater          = "稍后",

        exitConfirmMessage   = "要退出应用吗？",

        mainScanStop = "■ 停止",
        mainFoundCount = "已找到: %1\$d张",
        mainSampleRegistered = "📸 已登记%1\$d个样本",
        resultPhotoCount = "找到照片: %1\$d张",
        scanStoppedStatus = "已停止 — 已保存%1\$d张",
        scanUnknownError = "未知错误",
        notifChannelName = "照片扫描",
        notifChannelDesc = "人脸识别照片扫描进度通知",
        notifSearching = "正在搜索照片",
        notifCompleted = "搜索完成",
        notifStopped = "搜索已停止",
        notifProgressText = "已处理 %1\$d / %2\$d (%3\$d%%)",
        notifPreparing = "准备中...",
        notifStop = "停止",
        notifFoundText = "找到了%1\$d张照片。",
        notifStoppedText = "已保存%1\$d张。",
        resultReclassifyEmpty = "没有可移动到的列表。",
        resultItemSelected = "已选择",
        resultItemNotSelected = "未选择",
        settingCurrentValue = "当前: %1\$d%%",
        settingSimilarityGuide = "• 越高越严格 (99% = 仅几乎相同的人)\n\n• 越低越宽松 (50% = 包含相似的人)",
        settingErrorNotNumber = "请输入数字",
        settingErrorMin = "请输入不小于%1\$d的值",
        settingErrorMax = "请输入不大于%1\$d的值",
    ),
)