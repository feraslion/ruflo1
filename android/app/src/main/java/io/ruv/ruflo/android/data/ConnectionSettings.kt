package io.ruv.ruflo.android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class ConnectionConfig(
    val baseUrl: String = "",
    val bearerToken: String = ""
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
        bearerToken = preferences.getString(KEY_BEARER_TOKEN, "").orEmpty()
    )

    fun save(config: ConnectionConfig) {
        preferences.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_BEARER_TOKEN, config.bearerToken.trim())
            .apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_BEARER_TOKEN = "bearer_token"
    }
}
