package io.github.keiai0.sleeprec

/** バックグラウンド動作の制限が強い、端末メーカーの系統。 */
enum class Vendor(val nameRes: Int, val guideRes: Int) {
    COLOROS(R.string.vendor_coloros, R.string.guide_coloros),   // OPPO / realme / OnePlus
    XIAOMI(R.string.vendor_xiaomi, R.string.guide_xiaomi),
    HUAWEI(R.string.vendor_huawei, R.string.guide_huawei),
    SAMSUNG(R.string.vendor_samsung, R.string.guide_samsung),
    VIVO(R.string.vendor_vivo, R.string.guide_vivo),
    GENERIC(R.string.vendor_generic, R.string.guide_generic),
}

/**
 * 権限アシスタント(FR-8.4)のための、メーカー別の情報(Android に依存しない純粋な関数)。
 * 夜間に計測が止められる原因の多くは、メーカー独自の省電力機能なので、機種ごとの設定の場所を案内する。
 */
object VendorGuide {
    fun vendorOf(manufacturer: String, brand: String): Vendor {
        val s = "${manufacturer.lowercase()} ${brand.lowercase()}"
        return when {
            listOf("oppo", "realme", "oneplus").any { it in s } -> Vendor.COLOROS
            listOf("xiaomi", "redmi", "poco").any { it in s } -> Vendor.XIAOMI
            listOf("huawei", "honor").any { it in s } -> Vendor.HUAWEI
            "samsung" in s -> Vendor.SAMSUNG
            listOf("vivo", "iqoo").any { it in s } -> Vendor.VIVO
            else -> Vendor.GENERIC
        }
    }

    class Target(val pkg: String, val cls: String)

    /**
     * そのメーカーの、自動起動・バックグラウンド動作の設定画面の候補(上から順に試す)。
     * 端末や OS のバージョンで存在しない・開けないことが多いので、開けなければ、アプリ情報の画面にする。
     * ColorOS は、専用の画面を直接開けないため、アプリ情報の画面から手順で案内する(実機で確認)。
     */
    fun targets(vendor: Vendor): List<Target> = when (vendor) {
        Vendor.XIAOMI -> listOf(Target("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        Vendor.HUAWEI -> listOf(
            Target("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Target("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        Vendor.SAMSUNG -> listOf(Target("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"))
        Vendor.VIVO -> listOf(Target("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
        Vendor.COLOROS, Vendor.GENERIC -> emptyList()
    }
}
