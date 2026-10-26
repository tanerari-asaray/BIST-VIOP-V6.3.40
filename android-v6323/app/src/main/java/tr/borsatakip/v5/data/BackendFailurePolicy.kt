package tr.borsatakip.v5.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Provider authentication and application-to-gateway authentication are different failures. */
object BackendFailurePolicy {
    fun networkCode(error: IOException): ProviderFailureCode = when (error) {
        is SocketTimeoutException -> ProviderFailureCode.NETWORK_TIMEOUT
        is UnknownHostException -> ProviderFailureCode.DNS_ERROR
        is SSLException -> ProviderFailureCode.TLS_ERROR
        else -> ProviderFailureCode.NETWORK_ERROR
    }

    fun code(error: BackendHttpException): ProviderFailureCode =
        (error.cause as? IOException)?.let(::networkCode) ?: code(error.evidence)

    fun code(e: BackendHttpEvidence): ProviderFailureCode = when {
        e.failureStage == "PROVIDER_AUTH" -> ProviderFailureCode.SERVER_ERROR
        e.providerCode in setOf("LICENSED_BIST_PROVIDER_NOT_CONNECTED", "LICENSED_VIOP_PROVIDER_NOT_CONNECTED",
            "FINANCIAL_STATEMENTS_SOURCE_NOT_CONFIGURED") -> ProviderFailureCode.DATA_SOURCE_NOT_CONNECTED
        e.httpStatus == 401 || e.httpStatus == 403 -> ProviderFailureCode.AUTH_ERROR
        e.httpStatus == 404 || e.httpStatus == 422 -> ProviderFailureCode.EMPTY_DATA
        e.httpStatus == 429 -> ProviderFailureCode.RATE_LIMIT
        e.httpStatus != null && e.httpStatus >= 500 -> ProviderFailureCode.SERVER_ERROR
        e.httpStatus != null && e.httpStatus in 200..299 -> ProviderFailureCode.EMPTY_DATA
        else -> ProviderFailureCode.SERVER_ERROR
    }
}
