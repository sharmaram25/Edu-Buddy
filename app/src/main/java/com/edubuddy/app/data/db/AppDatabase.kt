package com.edubuddy.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubjectEntity::class,
        ClassSlotEntity::class,
        EventEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        AttendanceEntity::class,
        FocusSessionEntity::class,
        HolidayEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjects(): SubjectDao
    abstract fun classSlots(): ClassSlotDao
    abstract fun events(): EventDao
    abstract fun tasks(): TaskDao
    abstract fun money(): MoneyDao
    abstract fun attendance(): AttendanceDao
    abstract fun focus(): FocusDao
    abstract fun holidays(): HolidayDao

    companion object {
        /** v2: holidays & breaks. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `holidays` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `startDay` INTEGER NOT NULL, `endDay` INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "edubuddy.db")
                // Schema changes always ship an explicit Migration; we never silently wipe user data.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
