// ルートでは「使うプラグインとそのバージョン」を宣言するだけ(apply false)。
// 実際の適用は app/build.gradle.kts 側で行う。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
