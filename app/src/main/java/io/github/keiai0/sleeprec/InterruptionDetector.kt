package io.github.keiai0.sleeprec

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import io.github.keiai0.sleeprec.data.InterruptReason
import io.github.keiai0.sleeprec.data.SessionStore

/**
 * 「計測中(RECORDING)のまま、サービスが動いていない」セッションを中断と判定する。
 *
 * 仕組み: サービスは約 30 秒ごとに lastAliveAt を更新する。正常終了なら status が
 * RECORDING でなくなる。RECORDING のまま更新が止まっていれば、プロセスごと消えたと分かる
 * (OS による kill、強制停止、クラッシュ、電池切れなど)。
 */
object InterruptionDetector {
    // ハートビート間隔(30 秒)の 3 倍。この間に更新がなければ「止まった」とみなす
    private const val STALE_MS = 90_000L

    /**
     * @param force true なら更新時刻に関わらず中断とする。サービスの起動時に使う
     *              (サービスは 1 つだけなので、起動時点で残っている RECORDING は必ず前回の残骸)。
     */
    suspend fun markStale(context: Context, store: SessionStore, force: Boolean) {
        val now = System.currentTimeMillis()
        for (s in store.recordingSessions()) {
            if (!force && now - s.lastAliveAt < STALE_MS) continue
            // 生きていた最後の時刻を、終了時刻とする
            store.markInterrupted(s.id, s.lastAliveAt, findReason(context, s.lastAliveAt))
        }
    }

    private fun findReason(context: Context, lastAliveAt: Long): InterruptReason {
        // getHistoricalProcessExitReasons は Android 11 (API 30) 以降
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return InterruptReason.UNKNOWN

        val am = context.getSystemService(ActivityManager::class.java)
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 10) // 新しい順
        // 最後に生きていた時刻の前後 1 分以降に終了した記録を探す
        val hit = exits.firstOrNull { it.timestamp >= lastAliveAt - 60_000L }
            ?: return InterruptReason.NO_RECORD

        return when (hit.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> InterruptReason.OS_LOW_MEMORY
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_USER_STOPPED -> InterruptReason.USER_KILLED
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR -> InterruptReason.CRASH
            ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> InterruptReason.SYSTEM_KILLED
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> InterruptReason.PERMISSION_CHANGED
            else -> InterruptReason.OTHER
        }
    }
}
