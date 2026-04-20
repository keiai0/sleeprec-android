package io.github.keiai0.sleeprec

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** 統計の画像(PNG)を、端末の共有ストレージへ保存する / 他のアプリへ共有する(FR-5.4)。 */
object ImageExport {
    const val SAVE_FOLDER = "SleepRec" // 「ピクチャ」フォルダの下
    private const val SHARE_DIR = "share" // 共有の受け渡し用の一時ファイル(キャッシュ内)

    /** ファイル名(例: SleepRec_stats_week_20260914.png)。ASCII だけにする。 */
    fun fileName(period: StatsPeriod): String =
        "SleepRec_stats_%s_%s.png".format(period.kind.name.lowercase(), period.start.toString().replace("-", ""))

    /** 「ピクチャ/SleepRec」に保存する。MediaStore を使うので、ストレージの権限は要らない。成功したら Uri。 */
    fun saveToPictures(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SAVE_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("openOutputStream failed")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /** 他のアプリへ画像を渡す。一時ファイルをキャッシュに作る(前回の分は消す)。 */
    fun share(context: Context, bitmap: Bitmap, displayName: String) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, displayName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }
}
