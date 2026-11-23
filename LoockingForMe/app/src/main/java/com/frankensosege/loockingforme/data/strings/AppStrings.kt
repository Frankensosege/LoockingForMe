package com.frankensosege.loockingforme.data.strings

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 앱 내 모든 UI 문자열을 담는 Room Entity
 * - 언어 코드(languageCode)를 기본 키로 사용
 * - 새 문자열 추가 시 반드시 4개 언어(EN/KO/JA/ZH) 모두 추가할 것
 */
@Entity(tableName = "app_strings")
data class AppStrings(
    @PrimaryKey
    @ColumnInfo(name = "language_code")
    val languageCode: String,

    // ── 공통 ──────────────────────────────────────────────────────────────────
    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "save_and_start")
    val saveAndStart: String,

    @ColumnInfo(name = "settings")
    val settings: String,

    @ColumnInfo(name = "close_app")
    val closeApp: String,

    @ColumnInfo(name = "cancel")
    val cancel: String,

    @ColumnInfo(name = "delete")
    val delete: String,

    // ── 설정 화면 ─────────────────────────────────────────────────────────────
    @ColumnInfo(name = "setting_title")
    val settingTitle: String,

    @ColumnInfo(name = "setting_language")
    val settingLanguage: String,

    @ColumnInfo(name = "setting_source_dir")
    val settingSourceDir: String,

    @ColumnInfo(name = "setting_source_dir_hint")
    val settingSourceDirHint: String,

    @ColumnInfo(name = "setting_scan_dir")
    val settingScanDir: String,

    @ColumnInfo(name = "setting_scan_dir_hint")
    val settingScanDirHint: String,

    @ColumnInfo(name = "setting_select_folder")
    val settingSelectFolder: String,

    @ColumnInfo(name = "setting_similarity_threshold")
    val settingSimilarityThreshold: String,

    @ColumnInfo(name = "setting_similarity_hint")
    val settingSimilarityHint: String,

    // ── 메인 화면 ─────────────────────────────────────────────────────────────
    @ColumnInfo(name = "main_title")
    val mainTitle: String,

    @ColumnInfo(name = "main_add_list")
    val mainAddList: String,

    @ColumnInfo(name = "main_list_name_hint")
    val mainListNameHint: String,

    @ColumnInfo(name = "main_find_me")
    val mainFindMe: String,

    @ColumnInfo(name = "main_no_lists")
    val mainNoLists: String,

    @ColumnInfo(name = "main_scan_dir_label")
    val mainScanDirLabel: String,

    @ColumnInfo(name = "main_face_not_set")
    val mainFaceNotSet: String,

    @ColumnInfo(name = "main_face_set")
    val mainFaceSet: String,

    // ── 얼굴 선택 팝업 ────────────────────────────────────────────────────────
    @ColumnInfo(name = "face_picker_title")
    val facePickerTitle: String,

    @ColumnInfo(name = "face_picker_tap_hint")
    val facePickerTapHint: String,

    @ColumnInfo(name = "face_picker_no_face")
    val facePickerNoFace: String,

    @ColumnInfo(name = "face_picker_select_photo")
    val facePickerSelectPhoto: String,

    @ColumnInfo(name = "face_picker_detecting")
    val facePickerDetecting: String,

    @ColumnInfo(name = "face_picker_nosource_dir")
    val facePickerNoSourceDir: String,

    @ColumnInfo(name = "face_picker_empty_dir")
    val facePickerEmptyDir: String,

    // ── 스캔 진행 ─────────────────────────────────────────────────────────────
    @ColumnInfo(name = "scan_in_progress")
    val scanInProgress: String,

    @ColumnInfo(name = "scan_complete")
    val scanComplete: String,

    @ColumnInfo(name = "scan_no_result")
    val scanNoResult: String,

    // ── 결과 화면 ─────────────────────────────────────────────────────────────
    @ColumnInfo(name = "result_title")
    val resultTitle: String,

    @ColumnInfo(name = "result_back")
    val resultBack: String,

    @ColumnInfo(name = "result_empty")
    val resultEmpty: String,

    @ColumnInfo(name = "result_select_all")
    val resultSelectAll: String,

    @ColumnInfo(name = "result_deselect_all")
    val resultDeselectAll: String,

    @ColumnInfo(name = "result_delete_selected")
    val resultDeleteSelected: String,

    @ColumnInfo(name = "result_share_bluetooth")
    val resultShareBluetooth: String,

    @ColumnInfo(name = "result_selected_count")
    val resultSelectedCount: String,

    @ColumnInfo(name = "result_delete_confirm")
    val resultDeleteConfirm: String,

    @ColumnInfo(name = "result_long_press_hint")
    val resultLongPressHint: String,

    // ── 스캔 재시작 확인 ──────────────────────────────────────────────────────
    @ColumnInfo(name = "scan_has_existing_title")
    val scanHasExistingTitle: String,

    @ColumnInfo(name = "scan_has_existing_message")
    val scanHasExistingMessage: String,

    @ColumnInfo(name = "scan_restart_delete")
    val scanRestartDelete: String,

    @ColumnInfo(name = "scan_restart_skip")
    val scanRestartSkip: String,

    // ── 멀티샘플 얼굴 ─────────────────────────────────────────────────────────
    @ColumnInfo(name = "face_add_sample")
    val faceAddSample: String,

    @ColumnInfo(name = "face_sample_count")
    val faceSampleCount: String,

    // ── 미분류 / 재분류 ───────────────────────────────────────────────────────
    @ColumnInfo(name = "unclassified_list_name")
    val unclassifiedListName: String,

    @ColumnInfo(name = "result_reclassify")
    val resultReclassify: String,

    @ColumnInfo(name = "result_reclassify_title")
    val resultReclassifyTitle: String,

    @ColumnInfo(name = "result_reclassify_hint")
    val resultReclassifyHint: String,

    @ColumnInfo(name = "result_reclassify_save")
    val resultReclassifySave: String,

    // ── 권한 ──────────────────────────────────────────────────────────────────
    @ColumnInfo(name = "permission_required")
    val permissionRequired: String,

    @ColumnInfo(name = "permission_go_settings")
    val permissionGoSettings: String,

    @ColumnInfo(name = "permission_rationale")
    val permissionRationale: String,

    // ── In app update ──────────────────────────────────────────────────────────
    @ColumnInfo(name = "update_ready_msg")
    val updateReadyMsg: String,

    @ColumnInfo(name = "update_restart")
    val updateRestart: String,

    @ColumnInfo(name = "update_later")
    val updateLater: String,

    // ── 앱 종료 확인 ──────────────────────────────────────────────────────────
    @ColumnInfo(name = "exit_confirm_message")
    val exitConfirmMessage: String,

    // ── 스캔 상태 / 알림 / 결과·설정 화면 문구 (v9) ──────────────────────────
    @ColumnInfo(name = "main_scan_stop")
    val mainScanStop: String,

    @ColumnInfo(name = "main_found_count")
    val mainFoundCount: String,

    @ColumnInfo(name = "main_sample_registered")
    val mainSampleRegistered: String,

    @ColumnInfo(name = "result_photo_count")
    val resultPhotoCount: String,

    @ColumnInfo(name = "scan_stopped_status")
    val scanStoppedStatus: String,

    @ColumnInfo(name = "scan_unknown_error")
    val scanUnknownError: String,

    @ColumnInfo(name = "notif_channel_name")
    val notifChannelName: String,

    @ColumnInfo(name = "notif_channel_desc")
    val notifChannelDesc: String,

    @ColumnInfo(name = "notif_searching")
    val notifSearching: String,

    @ColumnInfo(name = "notif_completed")
    val notifCompleted: String,

    @ColumnInfo(name = "notif_stopped")
    val notifStopped: String,

    @ColumnInfo(name = "notif_progress_text")
    val notifProgressText: String,

    @ColumnInfo(name = "notif_preparing")
    val notifPreparing: String,

    @ColumnInfo(name = "notif_stop")
    val notifStop: String,

    @ColumnInfo(name = "notif_found_text")
    val notifFoundText: String,

    @ColumnInfo(name = "notif_stopped_text")
    val notifStoppedText: String,

    @ColumnInfo(name = "result_reclassify_empty")
    val resultReclassifyEmpty: String,

    @ColumnInfo(name = "result_item_selected")
    val resultItemSelected: String,

    @ColumnInfo(name = "result_item_not_selected")
    val resultItemNotSelected: String,

    @ColumnInfo(name = "setting_current_value")
    val settingCurrentValue: String,

    @ColumnInfo(name = "setting_similarity_guide")
    val settingSimilarityGuide: String,

    @ColumnInfo(name = "setting_error_not_number")
    val settingErrorNotNumber: String,

    @ColumnInfo(name = "setting_error_min")
    val settingErrorMin: String,

    @ColumnInfo(name = "setting_error_max")
    val settingErrorMax: String,
) {
    companion object {
        /** 앱 미시작 시 기본값 (한국어 기준) */
        val defaultStrings = AppStrings(
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
            facePickerNoSourceDir = "걸정에서 소스 사진 폴더를 먼저 지정하세요.",
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
            resultDeleteConfirm  = "선택한 사진을 목록에서 삭제하시겠습니까?\n\n(실제 파일은 삭제되지 않습니다)",
            resultLongPressHint       = "사진을 길게 눌러 선택하세요",
            scanHasExistingTitle     = "이미 분류된 사진이 있습니다",
            scanHasExistingMessage   = "분류된 목록이 있습니다. 삭제하고 처음부터 분류하시겠습니까?\n\n• 예 — 기존 분류 삭제 후 전체 재분류\n• 아니오 — 이미 분류된 사진은 건너뛰고 미분류 사진만 분류",
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
        )
    }
}