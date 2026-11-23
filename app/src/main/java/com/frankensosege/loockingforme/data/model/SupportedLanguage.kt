package com.frankensosege.loockingforme.data.model

/**
 * 지원 언어 목록 (가이드라인 확정: 4개만)
 */
enum class SupportedLanguage(
    val code: String,
    val displayName: String,
) {
    ENGLISH(code = "en", displayName = "English"),
    KOREAN(code = "ko", displayName = "한국어"),
    JAPANESE(code = "ja", displayName = "日本語"),
    CHINESE(code = "zh", displayName = "中文"),
    ;

    companion object {
        fun fromCode(code: String): SupportedLanguage =
            entries.firstOrNull { it.code == code } ?: KOREAN
    }
}
