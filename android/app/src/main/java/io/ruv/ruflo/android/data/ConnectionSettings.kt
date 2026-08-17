package io.ruv.ruflo.android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class ConnectionConfig(
    val baseUrl: String = "",
    val oauthClientId: String = "",
    val accessToken: String = "",
    val accessTokenExpiresAtEpochMs: Long = 0L,
    val grantedScopes: Set<String> = emptySet(),
    val subject: String = ""
)

class ConnectionSettings(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "ruflo_connection",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): ConnectionConfig = ConnectionConfig(
        baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
        oauthClientId = preferences.getString(KEY_OAUTH_CLIENT_ID, "").orEmpty(),
        accessToken = preferences.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
        accessTokenExpiresAtEpochMs = preferences.getLong(KEY_ACCESS_TOKEN_EXPIRY, 0L),
        grantedScopes = preferences.getStringSet(KEY_GRANTED_SCOPES, emptySet()).orEmpty(),
        subject = preferences.getString(KEY_SUBJECT, "").orEmpty()
    )

    fun save(config: ConnectionConfig) {
        preferences.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_OAUTH_CLIENT_ID, config.oauthClientId.trim())
            .putString(KEY_ACCESS_TOKEN, config.accessToken)
            .putLong(KEY_ACCESS_TOKEN_EXPIRY, config.accessTokenExpiresAtEpochMs)
            .putStringSet(KEY_GRANTED_SCOPES, config.grantedScopes)
            .putString(KEY_SUBJECT, config.subject)
            .apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_OAUTH_CLIENT_ID = "oauth_client_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ACCESS_TOKEN_EXPIRY = "access_token_expiry"
        const val KEY_GRANTED_SCOPES = "granted_scopes"
        const val KEY_SUBJECT = "subject"
    }
}
