package io.github.keiai0.sleeprec

import android.content.Context
import io.github.keiai0.sleeprec.data.SessionStore
import java.io.File

/** キャッシュの削除と、全データの削除(FR-8.6)。録音はすべて端末の中にあるので、ここで消せば何も残らない(NFR-3)。 */
object DataCleaner {
    /** キャッシュ(画像の共有用の一時ファイルなど)の大きさ(バイト)。 */
    fun cacheSize(context: Context): Long =
        context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun recordingsDir(context: Context) = File(context.getExternalFilesDir(null), "recordings")

    /** 保存されている全録音(WAV)の大きさ(バイト)。 */
    fun fullRecordingsSize(context: Context): Long =
        recordingsDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** 保存されている全録音(WAV)だけを削除する。検出したクリップ、音量、スコアなどは残る。 */
    fun deleteFullRecordings(context: Context) {
        recordingsDir(context).deleteRecursively()
    }

    fun clearCache(context: Context) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /**
     * すべての記録(音声ファイル、DB の行)、設定、プロフィール、キャッシュを削除する。
     * 計測中は呼ばない(呼び出し側で止める)。
     */
    suspend fun deleteAll(context: Context, store: SessionStore) {
        store.deleteAllSessions()
        // DB に行がない、取り残されたファイルも消す
        val base = context.getExternalFilesDir(null)
        File(base, "recordings").deleteRecursively()
        File(base, "clips").deleteRecursively()
        clearCache(context)
        AppSettings.reset(context)
        ScreenModeState.set(context, ScreenMode.AUTO_LOCK)
    }
}
