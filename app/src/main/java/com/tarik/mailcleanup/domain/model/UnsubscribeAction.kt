package com.tarik.mailcleanup.domain.model

/**
 * List-Unsubscribe header'ından türeyen aksiyon türleri.
 */
sealed class UnsubscribeAction {
    data class MailTo(val recipient: String, val subject: String?) : UnsubscribeAction()
    data class Http(val url: String) : UnsubscribeAction()
    object NotFound : UnsubscribeAction()
}
