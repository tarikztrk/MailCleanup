package com.tarik.mailcleanup.ui.mapper

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.domain.model.MailAccount

fun GoogleSignInAccount.toMailAccountOrNull(): MailAccount? {
    val accountName = account?.name ?: email
    return accountName?.let {
        MailAccount(
            accountName = it,
            email = email
        )
    }
}
