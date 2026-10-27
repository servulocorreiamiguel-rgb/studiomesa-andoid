package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAuthorizationErrorClassifierTest {
    @Test
    fun protectedEndpointUnauthorizedStatusRequiresReverification() {
        assertTrue(
            SessionAuthorizationErrorClassifier.requiresReverification(
                BackendHttpException(401, "/rest/v1/restaurant_orders", "Erro de ligação (401).")
            )
        )
    }

    @Test
    fun protectedEndpointForbiddenStatusRequiresReverification() {
        assertTrue(
            SessionAuthorizationErrorClassifier.requiresReverification(
                BackendHttpException(403, "/rest/v1/restaurant_orders", "Acesso recusado.")
            )
        )
    }

    @Test
    fun loginFailureStaysOnAuthenticationForm() {
        assertFalse(
            SessionAuthorizationErrorClassifier.requiresReverification(
                BackendHttpException(401, "/auth/v1/token?grant_type=password", "Invalid login credentials")
            )
        )
    }

    @Test
    fun rejectedRefreshTokenRequiresSessionReverificationEvenOnHttp400() {
        assertTrue(
            SessionAuthorizationErrorClassifier.requiresReverification(
                BackendHttpException(
                    400,
                    "/auth/v1/token?grant_type=refresh_token",
                    "Invalid Refresh Token: Already Used"
                )
            )
        )
    }

    @Test
    fun signupAndRecoveryErrorsStayOnTheirForms() {
        assertFalse(
            SessionAuthorizationErrorClassifier.requiresReverification(
                BackendHttpException(403, "/auth/v1/signup", "Email signup is disabled")
            )
        )
        assertFalse(
            SessionAuthorizationErrorClassifier.requiresReverification(
                BackendHttpException(401, "/auth/v1/recover", "Invalid recovery request")
            )
        )
    }

    @Test
    fun publicProviderDiscoveryErrorsStayOnOAuthSetupFlow() {
        assertFalse(
            SessionAuthorizationErrorClassifier.requiresReverification(
                BackendHttpException(403, "/auth/v1/settings", "OAuth provider is not enabled")
            )
        )
    }

    @Test
    fun databaseAuthorizationMessagesRequireReverificationEvenWhenPostgrestUsesBadRequest() {
        assertTrue(
            SessionAuthorizationErrorClassifier.requiresReverification(
                BackendHttpException(400, "/rest/v1/rpc/list_restaurant_team", "Sem autorização para gerir esta equipa.")
            )
        )
    }

    @Test
    fun ordinaryValidationAndNetworkErrorsDoNotRequireReverification() {
        assertFalse(
            SessionAuthorizationErrorClassifier.requiresReverification(
                IllegalArgumentException("O preço 1401,03 não é válido.")
            )
        )
        assertFalse(
            SessionAuthorizationErrorClassifier.requiresReverification(
                IllegalStateException("Não foi possível ligar ao servidor. Verifica a internet.")
            )
        )
    }
}
