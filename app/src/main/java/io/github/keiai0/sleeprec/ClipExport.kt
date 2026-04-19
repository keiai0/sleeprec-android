package io.github.keiai0.sleeprec

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** クリップの音声を、端末の共有ストレージへ保存する / 他のアプリへ共有する(FR-4.8)。 */
object ClipExport {
    const val SAVE_FOLDER = "SleepRec" // 「音楽」フォルダの下

    /** 保存・共有するときのファイル名(例: SleepRec_20260919_152402_snoring.wav)。ASCII だけにして、どの端末・アプリでも扱えるようにする。 */
    fun fileName(startedAt: Long, label: String, zone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply { timeZone = zone }
        return "SleepRec_${fmt.format(Date(startedAt))}_$label.wav"
    }

    /**
     * 端末の「音楽/SleepRec」に保存する。MediaStore を使うので、ストレージの権限は要らない(Android 10 以降)。
     * 成功したら、保存先の Uri を返す。
     */
    fun saveToMusic(context: Context, path: String, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$SAVE_FOLDER")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } } ?: error("openOutputStream failed")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // 途中で失敗したときに、壊れたファイルを残さない
            null
        }
    }

    /** 他のアプリ(メッセージ、メール、ファイルなど)へ、音声を渡す。 */
    fun share(context: Context, path: String, displayName: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path), displayName)
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/x-wav")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }
}
