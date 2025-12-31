package com.heartmonitor.app.di

import android.content.Context
import androidx.room.Room
import com.heartmonitor.app.bluetooth.BleManager
import com.heartmonitor.app.data.local.*
import com.heartmonitor.app.data.remote.OpenAIService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HeartMonitorDatabase {
        return Room.databaseBuilder(
            context,
            HeartMonitorDatabase::class.java,
            "heart_monitor_db"
        )
            .addMigrations(HeartMonitorDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideHeartRecordingDao(database: HeartMonitorDatabase): HeartRecordingDao {
        return database.heartRecordingDao()
    }

    @Provides
    @Singleton
    fun provideUserProfileDao(database: HeartMonitorDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    @Singleton
    fun provideDoctorDao(database: HeartMonitorDatabase): DoctorDao {
        return database.doctorDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIService(okHttpClient: OkHttpClient): OpenAIService {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIService::class.java)
    }

    @Provides
    @Singleton
    fun provideBleManager(@ApplicationContext context: Context): BleManager {
        return BleManager(context)
    }
}