package io.ruv.ruflo.android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OAuthSession(
    val accessToken: String,
    
    val expiresAtEpochMs: Long,
    val grantedScopes: Set<String>
)

class OidcAuthenticationManager(context: Context) : AutoCloseable {
    private val authorizationService = AuthorizationService(context.applicationContext)

    suspend fun createAuthorizationIntent(baseUrl: String, clientId: String): Intent {
        require(clientId.isNotBlank()) { "أدخل معرّف عميل OAuth المخصص لتطبيق Android." }
        val issuer = issuerUri(baseUrl)
        val serviceConfiguration = suspendCancellableCoroutine { continuation ->
            AuthorizationServiceConfiguration.fetchFromIssuer(issuer) { configuration, exception ->
                when {
                    configuration != null -> continuation.resume(configuration)
                    exception != null -> continuation.resumeWithException(exception)
                    else -> continuation.resumeWithException(
                        IllegalStateException("تعذر اكتشاف إعدادات OpenID Connect للبوابة.")
                    )
                }
            }
        }

        val request = AuthorizationRequest.Builder(
            serviceConfiguration,
            clientId.trim(),
            ResponseTypeValues.CODE,
            REDIRECT_URI
        )
            .setScope(REQUIRED_SCOPES.joinToString(" "))
            .setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier())
            .build()

        return authorizationService.getAuthorizationRequestIntent(request)
    }

    suspend fun completeAuthorization(responseIntent: Intent?): OAuthSession {
        val response = responseIntent?.let(AuthorizationResponse::fromIntent)
        val exception = responseIntent?.let(AuthorizationException::fromIntent)
        if (response == null) {
            throw exception ?: IllegalStateException("لم تكتمل المصادقة.")
        }

        val tokenResponse = suspendCancellableCoroutine { continuation ->
            authorizationService.performTokenRequest(response.createTokenExchangeRequest()) { token, tokenException ->
                when {
                    token != null -> continuation.resume(token)
                    tokenException != null -> continuation.resumeWithException(tokenException)
                    else -> continuation.resumeWithException(IllegalStateException("تعذر الحصول على رمز الوصول."))
                }
            }
        }

        val accessToken = tokenResponse.accessToken
            ?: throw SecurityException("لم تعد بوابة المصادقة رمز وصول صالحًا.")
        val grantedScopes = tokenResponse.scope
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: REQUIRED_SCOPES

        return OAuthSession(
            accessToken = accessToken,
            expiresAtEpochMs = tokenResponse.accessTokenExpirationTime ?: 0L,
            grantedScopes = grantedScopes
        )
    }

    override fun close() {
        authorizationService.dispose()
    }

    private fun issuerUri(baseUrl: String): Uri {
        val normalized = baseUrl.trim().removeSuffix("/")
        val uri = Uri.parse(normalized)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo.isNullOrBlank()) {
            "استخدم عنوان HTTPS للبوابة دون بيانات اعتماد مضمنة."
        }
        return uri
    }

    companion object {
        val REDIRECT_URI: Uri = Uri.parse("io.ruv.ruflo.android:/oauth2redirect")
        val REQUIRED_SCOPES: Set<String> = setOf("openid", "profile", "agents.read", "agents.control")
    }
}
