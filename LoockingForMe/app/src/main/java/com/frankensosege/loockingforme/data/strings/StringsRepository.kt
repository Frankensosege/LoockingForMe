package com.frankensosege.loockingforme.data.strings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 다국어 문자열 리포지토리
 * - DB 초기화(시드) 및 언어별 AppStrings 제공
 */
class StringsRepository(private val dao: AppStringsDao) {

    /**
     * 선택된 언어 코드에 해당하는 AppStrings를 Flow로 제공
     * 없으면 기본값(한국어) 반환
     */
    fun getStrings(languageCode: String): Flow<AppStrings> =
        dao.getStringsByLanguage(languageCode)
            .map { it ?: AppStrings.defaultStrings }

    /**
     * DB가 비어있으면 초기 데이터 삽입 (앱 최초 실행 시 1회)
     */
    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            dao.insertAll(*initialStrings)
        }
    }

    /**
     * 특정 언어 문자열을 즉시(suspend) 반환
     */
    suspend fun getStringsOnce(languageCode: String): AppStrings =
        dao.getStringsByLanguageOnce(languageCode) ?: AppStrings.defaultStrings
}
