import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
}

// Place Firebase config at app/google-services.json, then rebuild.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn(
        "Missing google-services.json — Firebase Auth will not be configured until you add " +
            "DrPlant/app/google-services.json"
    )
}

android {
    namespace = "com.nckh.drplant"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.nckh.drplant"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Uncompressed JNI libs + 16 KB zip alignment (AGP 8.5.1+ / 9.x)
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// Force 16 KB-aligned native libs from CameraX (overrides older transitive copies)
configurations.configureEach {
    resolutionStrategy {
        val cameraX = "1.6.2"
        force(
            "androidx.camera:camera-core:$cameraX",
            "androidx.camera:camera-camera2:$cameraX",
            "androidx.camera:camera-lifecycle:$cameraX",
            "androidx.camera:camera-view:$cameraX",
        )
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // CameraX — 1.4.2+ ships 16 KB-aligned libimage_processing_util_jni.so
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // ONNX Runtime — newer builds include 16 KB ELF alignment for JNI .so
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")

    // Firebase Authentication
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-auth")
}