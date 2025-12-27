package com.heartmonitor.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        HeartRecordingEntity::class,
        UserProfileEntity::class,
        DoctorEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HeartMonitorDatabase : RoomDatabase() {
    abstract fun heartRecordingDao(): HeartRecordingDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun doctorDao(): DoctorDao
}
