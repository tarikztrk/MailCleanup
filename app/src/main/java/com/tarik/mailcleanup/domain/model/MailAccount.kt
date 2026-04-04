package com.tarik.mailcleanup.domain.model

/**
 * Platformdan bağımsız hesap modeli.
 * GoogleSignInAccount gibi Android/Google tipleri domain'e taşınmaz.
 */
data class MailAccount(
    val accountName: String,
    val email: String?
)
