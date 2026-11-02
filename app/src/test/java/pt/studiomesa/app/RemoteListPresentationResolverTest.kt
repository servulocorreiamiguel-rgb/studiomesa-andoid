package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteListPresentationResolverTest {
    @Test
    fun distinguishesLoadingFromConfirmedEmpty() {
        assertEquals(
            RemoteListPresentation.LOADING,
            RemoteListPresentationResolver.resolve(
                hasLoadedData = false,
                isLoading = true,
                hasError = false,
                isEmpty = true
            )
        )
        assertEquals(
            RemoteListPresentation.EMPTY,
            RemoteListPresentationResolver.resolve(
                hasLoadedData = true,
                isLoading = false,
                hasError = false,
                isEmpty = true
            )
        )
    }

    @Test
    fun showsRetryAfterAnInitialLoadFailureInsteadOfAnEmptyState() {
        assertEquals(
            RemoteListPresentation.RETRY,
            RemoteListPresentationResolver.resolve(
                hasLoadedData = false,
                isLoading = false,
                hasError = true,
                isEmpty = true
            )
        )
    }

    @Test
    fun preservesPreviouslyLoadedContentWhenRefreshFails() {
        assertEquals(
            RemoteListPresentation.STALE_CONTENT,
            RemoteListPresentationResolver.resolve(
                hasLoadedData = true,
                isLoading = false,
                hasError = true,
                isEmpty = false
            )
        )
        assertEquals(
            RemoteListPresentation.STALE_CONTENT,
            RemoteListPresentationResolver.resolve(
                hasLoadedData = true,
                isLoading = false,
                hasError = true,
                isEmpty = true
            )
        )
    }
}
