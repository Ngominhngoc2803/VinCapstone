package com.heartmonitor.app.domain.model

import java.time.LocalDateTime

data class ChatMessage(
    val id: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
