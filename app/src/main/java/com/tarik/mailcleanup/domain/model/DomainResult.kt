package com.tarik.mailcleanup.domain.model

/**
 * Use-case/repository dönüşlerinde başarı-hata akışını tek tipte toplar.
 */
sealed interface DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>
    data class Error(val error: DomainError) : DomainResult<Nothing>
}
