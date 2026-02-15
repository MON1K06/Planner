package com.example.planner

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Task::class, Category::class], version = 4) // <--- ВЕРСИЯ 4
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "minimal_planner_db"
                )
                    .fallbackToDestructiveMigration() // Удалит старые данные при миграции
                    .build().also { INSTANCE = it }
            }
        }
    }
}