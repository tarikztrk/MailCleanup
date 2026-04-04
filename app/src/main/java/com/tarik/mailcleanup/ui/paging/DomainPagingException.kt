package com.tarik.mailcleanup.ui.paging

import com.tarik.mailcleanup.domain.model.DomainError

/**
 * Paging katmanında DomainError bilgisini kaybetmeden UI'a taşımak için sarıcı exception.
 */
class DomainPagingException(
    val domainError: DomainError
) : IllegalStateException(domainError.toString())
