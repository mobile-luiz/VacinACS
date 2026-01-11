plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // 🚨 ADICIONADO: OBRIGATÓRIO para que a anotação @Parcelize funcione 🚨
    id("kotlin-parcelize")
    // Plugin KAPT para processamento de anotações (se necessário para Room/Dagger, etc.)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vacinas.vacina"
    compileSdk = 36
    // REMOVER A CHAMADA ESTRANHA release(36) e usar apenas 34 ou 36
    // Usando um valor estável, se 36 não estiver definido no projeto libs.
    // Se o seu `targetSdk` for 36, use 36 aqui também.

    defaultConfig {
        applicationId = "com.vacinas.vacina"
        minSdk = 24
        targetSdk = 34 // Manter compatibilidade
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)

    // 🔹 Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    kapt("androidx.room:room-compiler:2.6.1")

    // 🔹 Volley (HTTP)
    implementation("com.android.volley:volley:1.2.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // No bloco dependencies
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Internet Connectivity
    implementation("com.google.android.gms:play-services-basement:18.2.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}