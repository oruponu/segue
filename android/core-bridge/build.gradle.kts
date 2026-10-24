plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.oruponu.segue.core"
    compileSdk {
        version = release(36)
    }
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            // libonnxruntime.so is provided by core/scripts/build_deps.sh
            jniLibs.srcDir("../../core/deps/android/jniLibs")
        }
        getByName("androidTest") {
            assets.srcDir("../../core/testdata")
            assets.srcDir("../../models")
        }
    }

    packaging {
        jniLibs {
            // The same .so may also be packaged through the CMake IMPORTED target
            pickFirsts += "**/libonnxruntime.so"
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
