package com.tarik.mailcleanup.domain.model

sealed class UnsubscribeAction {
    data class MailTo(val recipient: String, val subject: String?) : UnsubscribeAction()
    data class Http(val url: String) : UnsubscribeAction()
    object NotFound : UnsubscribeAction()
}
