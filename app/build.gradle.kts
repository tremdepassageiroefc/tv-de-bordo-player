plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.tvbordo.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.tvbordo.player"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Izy Play (Amlogic): Android TV normalmente 32 bits; 64 bits incluído por garantia
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Assinado com a chave de debug: suficiente para instalar nas boxes (sideload).
            // Para distribuição formal, crie uma keystore própria (ver README).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Mesmo motor do VLC usado nos testes de bancada
    implementation("org.videolan.android:libvlc-all:3.7.6")
}
