package com.tarik.mailcleanup.domain.model

sealed interface DomainError {
    data object Generic : DomainError
    data object Auth : DomainError
    data object RateLimit : DomainError
    data object Network : DomainError
    data object Server : DomainError
    data object NoUnsubscribeMethod : DomainError
    data object KeepFailed : DomainError
}
