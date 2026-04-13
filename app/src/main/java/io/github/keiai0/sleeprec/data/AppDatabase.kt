package io.github.keiai0.sleeprec.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Session::class, LoudnessSample::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun loudnessDao(): LoudnessDao

    companion object {
        // v1 → v2: 1秒ごとの音量テーブルを追加(既存の sessions はそのまま残す)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loudness_samples` (" +
                        "`sessionId` INTEGER NOT NULL, `second` INTEGER NOT NULL, " +
                        "`avgDb` REAL NOT NULL, `maxDb` REAL NOT NULL, " +
                        "PRIMARY KEY(`sessionId`, `second`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loudness_samples_sessionId` ON `loudness_samples` (`sessionId`)")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        // DB はプロセスに 1 つだけ作る(Activity と Service で共有する)
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "sleeprec.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
