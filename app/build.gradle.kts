import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.android.secrets)
    kotlin("kapt")
}

// 릴리즈 서명 정보. keystore.properties 가 없으면 서명 없이 빌드됩니다.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

android {
    namespace = "com.youngs.picview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.youngs.picview"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val path = keystoreProperties.getProperty("storeFile")
                storeFile = File(path).let { if (it.isAbsolute) it else rootProject.file(path) }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 직접 배포용 APK 에서 에뮬레이터 전용 ABI 를 뺍니다(99MB → 약 45MB).
            // AAB 로 제출하면 구글이 알아서 더 잘게 쪼갭니다.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // 최신 SDK 및 툴체인은 17 이상 권장
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    /*
     * 네이버 지도 SDK 의 libnavermap.so 가 ABI 하나당 19~26MB 입니다.
     * 4개 ABI 를 다 넣으면 APK 가 99MB 가 되므로 분리해서 내보냅니다.
     *
     * - Play Store 제출은 AAB(:bundleRelease). 구글이 기기 ABI 에 맞는 것만 내려줘
     *   실제 다운로드는 30MB 내외가 됩니다.
     * - 직접 배포용 APK(:assembleRelease)는 아래 abiFilters 로 실기기용 두 개만 담습니다.
     *   x86/x86_64 는 에뮬레이터 전용이라 실사용자에게 필요 없습니다.
     *   (에뮬레이터 테스트는 debug 빌드로 하며, debug 에는 이 필터가 걸리지 않습니다)
     */
    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        language {
            // 한국어 전용 앱이라 언어 분리는 끕니다(분리해도 이득이 없고 QA 만 복잡해짐)
            enableSplit = false
        }
    }

    // Room 스키마를 파일로 남겨 마이그레이션 시 diff 를 볼 수 있게 합니다.
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }
}

dependencies {
    // 안드로이드 기본 UI 및 아키텍처 스펙
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // CameraX (스마트 프레임 가이드용)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Network & Coroutine (Retrofit2 + OkHttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)

    // 네이버 맵 & 글라이드
    implementation(libs.naver.map.sdk)
    implementation(libs.glide)

    // 네이버 지도의 FusedLocationSource 가 요구합니다
    implementation(libs.play.services.location)

    // 테스트 라이브러리
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 뷰 모델 및 라이프사이클 스코프 지원
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)

    // Room : 저장한 코스·방문 기록. 관광 데이터 자체는 캐싱하지 않습니다(공모전 규정)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.tedpermission.normal)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.swiperefreshlayout)

    // 💡 권장: 이미지 로딩 Glide 컴파일러 (애노테이션 처리용)
    kapt("com.github.bumptech.glide:compiler:4.16.0")
}
secrets {
    propertiesFileName = "local.properties"
    // local.properties 에 없는 키는 여기서 채웁니다.
    // 키가 없는 환경(다른 PC·CI)에서도 빌드가 통과하고, 해당 기능만 폴백합니다.
    defaultPropertiesFileName = "local.defaults.properties"
}