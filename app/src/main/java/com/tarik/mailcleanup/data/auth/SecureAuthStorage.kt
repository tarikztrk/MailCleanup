package com.tarik.mailcleanup.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tarik.mailcleanup.domain.model.MailAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureAuthStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveMailAccount(account: MailAccount) {
        prefs.edit()
            .putString(KEY_ACCOUNT_NAME, account.accountName)
            .putString(KEY_ACCOUNT_EMAIL, account.email)
            .apply()
    }

    fun loadMailAccount(): MailAccount? {
        val accountName = prefs.getString(KEY_ACCOUNT_NAME, null) ?: return null
        return MailAccount(
            accountName = accountName,
            email = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "mail_cleanup_auth_secure"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_ACCOUNT_EMAIL = "account_email"
    }
}
