package com.tarik.mailcleanup.ui.paging

import com.tarik.mailcleanup.domain.model.DomainError

class DomainPagingException(
    val domainError: DomainError
) : IllegalStateException(domainError.toString())
