plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kz.soskomek.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "kz.soskomek.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // HTTPS-адрес сервера синхронизации (SOS-события, карта инцидентов, жалобы).
        // Пусто = приложение работает автономно: SMS + локальное хранилище.
        buildConfigField("String", "API_BASE_URL", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Для удобной установки release-сборка подписывается debug-ключом.
            // Перед публикацией в Google Play замените на собственный ключ.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.play.services.location)
    implementation(libs.osmdroid)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
