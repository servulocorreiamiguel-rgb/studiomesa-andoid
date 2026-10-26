package pt.studiomesa.app

/**
 * HTTP error from Supabase REST/Auth. Keeping the status and endpoint separate
 * from the display message avoids guessing authorization state from digits in text.
 */
internal class BackendHttpException(
    val statusCode: Int,
    val endpointPath: String,
    message: String
) : IllegalStateException(message)
