package io.github.keiai0.sleeprec.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Session::class, LoudnessSample::class, AudioEvent::class, ApneaCandidate::class, SessionTag::class, SessionPause::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun loudnessDao(): LoudnessDao
    abstract fun audioEventDao(): AudioEventDao
    abstract fun apneaCandidateDao(): ApneaCandidateDao
    abstract fun sessionTagDao(): SessionTagDao
    abstract fun sessionPauseDao(): SessionPauseDao

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

        // v4 → v5: 無呼吸の候補のテーブルを追加
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `apnea_candidates` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `silenceMs` INTEGER NOT NULL, `maxDb` REAL NOT NULL, `clipPath` TEXT, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apnea_candidates_sessionId` ON `apnea_candidates` (`sessionId`)")
            }
        }

        // v5 → v6: 睡眠前のメモ(sessions.memo)と、タグのテーブルを追加
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `memo` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_tags` (" +
                        "`sessionId` INTEGER NOT NULL, `tag` TEXT NOT NULL, PRIMARY KEY(`sessionId`, `tag`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_tags_sessionId` ON `session_tags` (`sessionId`)")
            }
        }

        // v6 → v7: 一時停止の記録
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_pauses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `reason` TEXT NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_pauses_sessionId` ON `session_pauses` (`sessionId`)")
            }
        }

        // v7 → v8: 起床時の気分(sessions.mood)
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `mood` INTEGER")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        // DB はプロセスに 1 つだけ作る(Activity と Service で共有する)
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "sleeprec.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { instance = it }
            }
    }
}
