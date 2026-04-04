package com.tarik.mailcleanup.domain.model

data class Subscription(
    val senderName: String,
    val senderEmail: String,
    val messageIds: MutableList<String> = mutableListOf(),
    val emailCount: Int = 0
)
