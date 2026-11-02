package pt.studiomesa.app

/** Presentation states for remote lists where an empty response differs from a failed load. */
enum class RemoteListPresentation {
    LOADING,
    RETRY,
    EMPTY,
    CONTENT,
    STALE_CONTENT
}

object RemoteListPresentationResolver {
    fun resolve(
        hasLoadedData: Boolean,
        isLoading: Boolean,
        hasError: Boolean,
        isEmpty: Boolean
    ): RemoteListPresentation = when {
        hasLoadedData && hasError -> RemoteListPresentation.STALE_CONTENT
        hasLoadedData && isEmpty -> RemoteListPresentation.EMPTY
        hasLoadedData -> RemoteListPresentation.CONTENT
        isLoading -> RemoteListPresentation.LOADING
        hasError -> RemoteListPresentation.RETRY
        else -> RemoteListPresentation.LOADING
    }
}
