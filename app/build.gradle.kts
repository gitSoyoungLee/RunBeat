plugins {
    id("com.android.application")
}

android {
    namespace = "com.runbeat.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.runbeat.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 지도(OpenStreetMap) - API 키 없이 사용 가능
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
