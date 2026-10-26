plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.drplantadmin"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.drplantadmin"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    // CameraX
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

// Xử lý hướng ảnh
    implementation("androidx.exifinterface:exifinterface:1.3.7")

// Đọc dữ liệu chẩn đoán
    implementation("com.google.code.gson:gson:2.10.1")

// Chạy mô hình AI ONNX
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")
}