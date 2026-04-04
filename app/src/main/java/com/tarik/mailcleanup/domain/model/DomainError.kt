package com.tarik.mailcleanup.domain.model

sealed interface DomainError {
    data object Generic : DomainError
    data object NoUnsubscribeMethod : DomainError
    data object KeepFailed : DomainError
}
