package io.github.keiai0.sleeprec.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Session::class, LoudnessSample::class, AudioEvent::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun loudnessDao(): LoudnessDao
    abstract fun audioEventDao(): AudioEventDao

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

        // v2 → v3: 音声イベントのテーブルを追加
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`maxDb` REAL NOT NULL, `avgDb` REAL NOT NULL, `type` TEXT NOT NULL, `clipPath` TEXT, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_events_sessionId` ON `audio_events` (`sessionId`)")
            }
        }

        // v3 → v4: 種別のスコアと、ユーザーが直したかの印を追加
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `audio_events` ADD COLUMN `typeScore` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `audio_events` ADD COLUMN `typeCorrected` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        // DB はプロセスに 1 つだけ作る(Activity と Service で共有する)
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "sleeprec.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
