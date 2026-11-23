package com.frankensosege.loockingforme.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Storage Access Framework(SAF) 및 권한 처리 유틸리티
 * - Persistable URI 권한 유지
 * - 미디어 접근 권한 확인 (Android 9~13+ 대응)
 */
object StorageHelper {

    /**
     * SAF로 선택한 URI에 Persistable 권한 부여
     * 앱 재시작 후에도 URI 접근 권한 유지
     */
    fun takePersistablePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // 이미 권한이 있거나 부여 불가 — 무시
        }
    }

    /**
     * Android 버전별 미디어 읽기 권한 문자열 반환
     * - Android 13(API 33)+: READ_MEDIA_IMAGES
     * - Android 9~12(API 28~32): READ_EXTERNAL_STORAGE
     */
    fun getRequiredMediaPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /**
     * 앱 상세 설정 화면으로 이동하는 인텐트 반환 (권한 거부 후 안내용)
     */
    fun buildAppSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * URI를 사람이 읽기 쉬운 경로 문자열로 변환 (표시용)
     * SAF URI의 마지막 경로 세그먼트를 디코딩하여 반환
     */
    fun uriToDisplayPath(uri: Uri): String {
        val path = uri.lastPathSegment ?: return uri.toString()
        // "primary:DCIM/Camera" → "DCIM/Camera" 형태로 정리
        return if (path.contains(":")) path.substringAfter(":") else path
    }
}