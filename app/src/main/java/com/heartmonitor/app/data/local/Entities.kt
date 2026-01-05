package com.heartmonitor.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity(tableName = "heart_recordings")
@TypeConverters(Converters::class)
data class HeartRecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timestamp: LocalDateTime,
    val duration: Long,
    val signalData: List<Float>,
    val healthStatus: String,
    val verificationStatus: String,
    val doctorName: String? = null,
    val hospitalName: String? = null,
    val averageBpm: Float,
    val maxBpm: Int,
    val aiAnalysisJson: String? = null,
    // New fields for doctor visit
    val doctorVisitDate: LocalDate? = null,
    val doctorNote: String? = null,
    val diagnosis: String? = null,
    val recommendations: String? = null,

    val pcmFilePath: String? = null,
    val wavFilePath: String? = null,
    val audioSampleRate: Int = 8000,
    val audioChannels: Int = 1

)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null
)

@Entity(tableName = "doctors")
data class DoctorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String,
    val phone: String,
    val email: String,
    val avatarUrl: String? = null
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? {
        return value?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? {
        return date?.toEpochSecond(ZoneOffset.UTC)
    }

    @TypeConverter
    fun fromLocalDate(value: Long?): LocalDate? {
        return value?.let { LocalDate.ofEpochDay(it) }
    }

    @TypeConverter
    fun localDateToLong(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }

    @TypeConverter
    fun fromFloatList(value: List<Float>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFloatList(value: String): List<Float> {
        val type = object : TypeToken<List<Float>>() {}.type
        return gson.fromJson(value, type)
    }
}