package com.tarik.mailcleanup.core.text

import androidx.annotation.StringRes

/**
 * ViewModel tarafının Android Context'e dokunmadan metin alabilmesi için soyutlama.
 */
interface StringProvider {
    fun get(@StringRes resId: Int, vararg args: Any): String
}
