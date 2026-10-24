package pt.studiomesa.app

import java.util.Locale

/** Classifies errors that must hide cached restaurant/customer data until access is verified. */
internal object SessionAuthorizationErrorClassifier {
    private val authenticationFormEndpoints = listOf(
        "/auth/v1/token?grant_type=password",
        "/auth/v1/token?grant_type=pkce",
        "/auth/v1/signup",
        "/auth/v1/recover"
    )

    private val authorizationMessages = listOf(
        "não foi possível confirmar as permissões da conta",
        "não foi possível confirmar o acesso aos dados da conta",
        "unauthorized",
        "not authorized",
        "permission denied",
        "invalid jwt",
        "jwt expired",
        "forbidden",
        "não autorizado",
        "sem autorização",
        "não pertence a"
    )

    fun requiresReverification(error: Throwable): Boolean {
        val backendError = error as? BackendHttpException
        val endpoint = backendError?.endpointPath.orEmpty()

        // Failed login, signup, PKCE exchange and password recovery should be
        // reported in their own forms rather than redirecting to session recovery.
        if (authenticationFormEndpoints.any(endpoint::startsWith)) return false

        // An invalid/revoked refresh token can be reported as HTTP 400 (invalid_grant),
        // not only 401/403. The cached tenant data must still be hidden in that case.
        if (
            backendError != null &&
            endpoint.startsWith("/auth/v1/token?grant_type=refresh_token") &&
            backendError.statusCode in 400..499
        ) return true

        if (backendError?.statusCode == 401 || backendError?.statusCode == 403) return true

        val message = error.message.orEmpty().lowercase(Locale.ROOT)
        return authorizationMessages.any(message::contains)
    }
}
