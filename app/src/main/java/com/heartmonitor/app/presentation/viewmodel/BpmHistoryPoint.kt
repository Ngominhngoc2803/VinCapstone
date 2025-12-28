package com.heartmonitor.app.presentation.viewmodel

import java.time.LocalDate

data class BpmHistoryPoint(
    val date: LocalDate,
    val bpm: Float
)
