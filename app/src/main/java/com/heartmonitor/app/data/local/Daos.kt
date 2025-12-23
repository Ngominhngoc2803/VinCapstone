package com.heartmonitor.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface HeartRecordingDao {
    @Query("SELECT * FROM heart_recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<HeartRecordingEntity>>

    @Query("SELECT * FROM heart_recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): HeartRecordingEntity?

    @Query("SELECT * FROM heart_recordings WHERE timestamp >= :startDate ORDER BY timestamp DESC")
    fun getRecordingsAfterDate(startDate: LocalDateTime): Flow<List<HeartRecordingEntity>>

    @Query("SELECT * FROM heart_recordings WHERE healthStatus = 'ISSUES_DETECTED' AND timestamp >= :startDate ORDER BY timestamp DESC")
    fun getWarningsAfterDate(startDate: LocalDateTime): Flow<List<HeartRecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: HeartRecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: HeartRecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: HeartRecordingEntity)

    @Query("DELETE FROM heart_recordings")
    suspend fun deleteAllRecordings()
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateUserProfile(profile: UserProfileEntity)
}

@Dao
interface DoctorDao {
    @Query("SELECT * FROM doctors")
    fun getAllDoctors(): Flow<List<DoctorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoctor(doctor: DoctorEntity)

    @Delete
    suspend fun deleteDoctor(doctor: DoctorEntity)
}
