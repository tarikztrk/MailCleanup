package com.tarik.mailcleanup.core.text

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : StringProvider {
    override fun get(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
