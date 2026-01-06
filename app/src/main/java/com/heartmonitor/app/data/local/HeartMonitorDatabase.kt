package com.heartmonitor.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HeartRecordingEntity::class,
        UserProfileEntity::class,
        DoctorEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HeartMonitorDatabase : RoomDatabase() {
    abstract fun heartRecordingDao(): HeartRecordingDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun doctorDao(): DoctorDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns for doctor visit information
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN doctorVisitDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN doctorNote TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN diagnosis TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN recommendations TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add audio file fields and remove signalData (too large for database)
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN pcmFilePath TEXT")
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN wavFilePath TEXT")
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN audioSampleRate INTEGER NOT NULL DEFAULT 8000")
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN audioChannels INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN bpmSeries TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE heart_recordings ADD COLUMN sampleRateHz INTEGER NOT NULL DEFAULT 8000")

                // Note: Cannot drop signalData column in SQLite, but new entities won't use it
                // Old data will be ignored when loading from database
            }
        }
    }


}