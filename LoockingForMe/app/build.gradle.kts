plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // kotlin.plugin.compose는 Kotlin 2.0.0+에서만 사용 가능
    // Kotlin 1.9.22에서는 composeOptions.kotlinCompilerExtensionVersion으로 처리
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.frankensosege.loockingforme"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.frankensosege.loockingforme"
        minSdk = 28   // Android Q (10) 이상
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            // 실제 광고 단위 ID
            buildConfigField("String", "AD_INTERSTITIAL_ID",    "\"ca-app-pub-2733222333675520/5993113499\"")
            buildConfigField("String", "AD_BANNER_MAIN_ID",     "\"ca-app-pub-2733222333675520/7414453486\"")
            buildConfigField("String", "AD_BANNER_RESULT_ID",   "\"ca-app-pub-2733222333675520/3475208470\"")
            buildConfigField("String", "AD_BANNER_SETTING_ID",  "\"ca-app-pub-2733222333675520/9388386843\"")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // 구글 공식 테스트 광고 ID (배너 3곳은 같은 테스트 ID 공용)
            buildConfigField("String", "AD_INTERSTITIAL_ID",    "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "AD_BANNER_MAIN_ID",     "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "AD_BANNER_RESULT_ID",   "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "AD_BANNER_SETTING_ID",  "\"ca-app-pub-3940256099942544/6300978111\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    // TFLite 모델 파일 압축 비활성화 — 런타임에 직접 MappedByteBuffer로 읽어야 함

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        disable += "GradleDependency"
        abortOnError = false
        checkDependencies = true
    }

    androidResources { noCompress += "tflite" }
}

// Room schema export 경로 설정
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // ExifInterface — 이미지 EXIF 회전 보정
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Activity
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DocumentFile (SAF 디렉토리 접근)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Coil — 이미지 로딩
    implementation(libs.coil.compose)

    // ML Kit Face Detection (얼굴 감지 + 랜드마크)
    implementation(libs.mlkit.face.detection)

    // TFLite — FaceNet 임베딩 추론
    // GPU delegate plugin은 버전 충돌 문제로 제외, NNAPI + CPU 폴백 사용
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Slack Compose Lint
    lintChecks(libs.slack.compose.lint)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Play 인앱 업데이트
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    //AdMob 광고 SDK
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

