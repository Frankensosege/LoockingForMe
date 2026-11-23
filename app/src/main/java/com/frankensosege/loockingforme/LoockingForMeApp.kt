package com.frankensosege.loockingforme

import android.app.Application
import com.frankensosege.loockingforme.data.db.AppDatabase
import com.frankensosege.loockingforme.data.strings.StringsDatabase
import com.frankensosege.loockingforme.data.strings.StringsRepository
import com.frankensosege.loockingforme.utils.FaceAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 클래스
 * - 전역 DB 인스턴스 초기화
 * - FaceAnalyzer 싱글턴 관리 (TFLite 인터프리터 중복 초기화 방지)
 * - 다국어 문자열 DB 시드 (설정 화면을 거치지 않아도 문자열이 준비되도록 앱 시작 시 1회)
 */
class LoockingForMeApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 앱 전역 Room DB (목록, 얼굴 특징 등) */
    val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    /** 다국어 문자열 Room DB */
    val stringsDatabase: StringsDatabase by lazy {
        StringsDatabase.getInstance(this)
    }

    /**
     * FaceAnalyzer 싱글턴
     * TFLite Interpreter를 앱 수명과 동일하게 유지
     * 매번 새로 생성하면 모델 로딩 비용(~500ms) 반복 발생
     */
    val faceAnalyzer: FaceAnalyzer by lazy {
        FaceAnalyzer(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appScope.launch {
            StringsRepository(stringsDatabase.stringsDao()).seedIfEmpty()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // TFLite 인터프리터 해제
        faceAnalyzer.close()
    }

    companion object {
        lateinit var instance: LoockingForMeApp
            private set
    }
}