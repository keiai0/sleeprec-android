plugins {
    alias(libs.plugins.android.application)
    // AGP 9 は Kotlin を内蔵しているので、kotlin-android プラグインは不要
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // Room のアノテーション(@Entity など)からコードを生成する
}

android {
    namespace = "io.github.keiai0.sleeprec"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.keiai0.sleeprec"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.litert)
    implementation(libs.androidx.navigation.compose)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
