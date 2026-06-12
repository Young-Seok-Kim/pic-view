plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.android.secrets)
    kotlin("kapt")
}

android {
    namespace = "com.youngs.picview"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.youngs.picview"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)

    // 네이버 맵 & 글라이드
    implementation(libs.naver.map.sdk)
    implementation(libs.glide)

    // 🔵 [구글 & 파이어베이스 스택]
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx) // 서버 DB 및 캐싱
    implementation(libs.firebase.auth.ktx)      // 파이어베이스 유저 인증
    implementation(libs.play.services.auth)     // 구글 원탭 소셜 로그인

    // 🟢 [카카오 플랫폼 스택]
    implementation(libs.kakao.sdk.user)          // 카카오 로그인
    implementation(libs.kakao.sdk.share)         // 카카오톡 링크 공유기능
//    implementation(libs.kakao.maps.sdk)          // 카카오 맵 스페어

    // 테스트 라이브러리
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 💡 권장: 뷰 모델 및 라이프사이클 스코프 지원 (필수!)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.fragment:fragment-ktx:1.8.2") // Fragment에서의 ViewModel 접근 및 뷰바인딩 편의성 증대

    implementation("io.github.ParkSangGwon:tedpermission-normal:3.3.0")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    implementation("com.google.guava:guava:31.1-android")

    implementation("androidx.core:core-splashscreen:1.0.1")

    // 💡 권장: 이미지 로딩 Glide 컴파일러 (애노테이션 처리용)
    kapt("com.github.bumptech.glide:compiler:4.16.0")
}
secrets {
    propertiesFileName = "local.properties"
}