package com.example.sleeprec.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Session::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // DB はプロセスに 1 つだけ作る(Activity と Service で共有する)
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "sleeprec.db",
                ).build().also { instance = it }
            }
    }
}
