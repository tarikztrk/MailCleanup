package com.tarik.mailcleanup.domain.model

/**
 * Abonelik listesinde gösterilen gönderici bazlı model.
 * messageIds unsubscribe/clean aksiyonlarında kullanılmak üzere tutulur.
 */
data class Subscription(
    val senderName: String,
    val senderEmail: String,
    val messageIds: MutableList<String> = mutableListOf(),
    val emailCount: Int = 0
)
