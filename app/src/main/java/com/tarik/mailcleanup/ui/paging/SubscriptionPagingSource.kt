package com.tarik.mailcleanup.ui.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.usecase.GetSubscriptionsUseCase
import java.util.Calendar

class SubscriptionPagingSource(
    private val account: GoogleSignInAccount,
    private val getSubscriptionsUseCase: GetSubscriptionsUseCase
) : PagingSource<Int, Subscription>() {

    override fun getRefreshKey(state: PagingState<Int, Subscription>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Subscription> {
        return try {
            val oneYearAgo = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
            var page = params.key ?: 0

            while (true) {
                val endDate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -30 * page)
                }
                val startDate = (endDate.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -30)
                }

                if (startDate.before(oneYearAgo)) {
                    return LoadResult.Page(
                        data = emptyList(),
                        prevKey = if (page == 0) null else page - 1,
                        nextKey = null
                    )
                }

                when (val result = getSubscriptionsUseCase(account, startDate, endDate)) {
                    is DomainResult.Success -> {
                        val uniqueByEmail = result.data
                            .groupBy { it.senderEmail }
                            .map { (_, items) -> items.maxByOrNull { it.emailCount } ?: items.first() }
                            .sortedByDescending { it.emailCount }

                        if (uniqueByEmail.isNotEmpty()) {
                            return LoadResult.Page(
                                data = uniqueByEmail,
                                prevKey = if (page == 0) null else page - 1,
                                nextKey = page + 1
                            )
                        }

                        // This window is empty; move to the next (older) window.
                        page += 1
                    }
                    is DomainResult.Error -> {
                        return LoadResult.Error(
                            IllegalStateException(result.error.toString())
                        )
                    }
                }
            }

            @Suppress("UNREACHABLE_CODE")
            LoadResult.Page(
                data = emptyList(),
                prevKey = null,
                nextKey = null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
