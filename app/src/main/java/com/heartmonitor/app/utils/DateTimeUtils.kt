package com.heartmonitor.app.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateTimeUtils {
    
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val fullFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
    
    fun formatTime(dateTime: LocalDateTime): String {
        return dateTime.format(timeFormatter)
    }
    
    fun formatDate(dateTime: LocalDateTime): String {
        return dateTime.format(dateFormatter)
    }
    
    fun formatFull(dateTime: LocalDateTime): String {
        return dateTime.format(fullFormatter)
    }
    
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", 0, minutes, seconds)
    }
    
    fun getStartOfWeek(): LocalDateTime {
        val now = LocalDateTime.now()
        val dayOfWeek = now.dayOfWeek.value
        return now.minusDays((dayOfWeek - 1).toLong())
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
    }
    
    fun getDaysAgo(days: Int): LocalDateTime {
        return LocalDateTime.now().minusDays(days.toLong())
    }
    
    fun isToday(dateTime: LocalDateTime): Boolean {
        val today = LocalDateTime.now().toLocalDate()
        return dateTime.toLocalDate() == today
    }
    
    fun getDaysDifference(from: LocalDateTime, to: LocalDateTime = LocalDateTime.now()): Long {
        return ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate())
    }
}
