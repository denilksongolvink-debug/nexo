package dev.nexo.app

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** Gerencia segredos do backend: gera, armazena e escreve runtime.env para o proot */
class SecretStore(private val context: Context) {

    private val prefs = NexoApplication.getInstance().encryptedPrefs
    private val secureRandom = SecureRandom()

    // Chaves no EncryptedSharedPreferences
    private const val KEY_API_KEY = "nexus_api_key"
    private const val KEY_DASH_TOKEN = "nexus_dash_token"
    private const val KEY_EDGE_SECRET = "nexus_edge_secret"
    private const val KEY_EDGE_NONCE = "nexus_edge_nonce"
    private const val KEY_EDGE_PORT = "nexus_edge_port"

    /** Gera segredos na primeira execução */
    suspend fun ensureSecretsGenerated() = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            if (!prefs.contains(KEY_API_KEY)) putString(KEY_API_KEY, generateSecret(32))
            if (!prefs.contains(KEY_DASH_TOKEN)) putString(KEY_DASH_TOKEN, generateSecret(32))
            if (!prefs.contains(KEY_EDGE_SECRET)) putString(KEY_EDGE_SECRET, generateSecret(32))
            if (!prefs.contains(KEY_EDGE_NONCE)) putString(KEY_EDGE_NONCE, generateNonce())
            if (!prefs.contains(KEY_EDGE_PORT)) putInt(KEY_EDGE_PORT, 8700)
        }.apply()
    }

    /** Gera novo nonce a cada boot */
    suspend fun rotateNonce() = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_EDGE_NONCE, generateNonce()).apply()
    }

    /** Escreve runtime.env no armazenamento interno (files/home/.nexo/runtime.env) */
    suspend fun writeRuntimeEnv() = withContext(Dispatchers.IO) {
        val runtimeDir = context.getDir("nexo", Context.MODE_PRIVATE)
        val envFile = java.io.File(runtimeDir, "runtime.env")

        val apiKey = prefs.getString(KEY_API_KEY, "")!!
        val dashToken = prefs.getString(KEY_DASH_TOKEN, "")!!
        val edgeSecret = prefs.getString(KEY_EDGE_SECRET, "")!!
        val nonce = prefs.getString(KEY_EDGE_NONCE, "")!!
        val edgePort = prefs.getInt(KEY_EDGE_PORT, 8700)

        val content = """
            NEXO_API_KEY=$apiKey
            NEXO_DASH_TOKEN=$dashToken
            NEXO_EDGE_SECRET=$edgeSecret
            NEXO_EDGE_NONCE=$nonce
            NEXO_EDGE_PORT=$edgePort
        """.trimIndent()

        envFile.writeText(content)
        // chmod 600
        envFile.setReadable(false, false)
        envFile.setReadable(true, true)
        envFile.setWritable(false, false)
        envFile.setWritable(true, true)
    }

    /** Retorna o nonce atual */
    fun getNonce(): String = prefs.getString(KEY_EDGE_NONCE, "")!!

    /** Retorna a porta do edge */
    fun getEdgePort(): Int = prefs.getInt(KEY_EDGE_PORT, 8700)

    private fun generateSecret(bytes: Int): String {
        val arr = ByteArray(bytes)
        secureRandom.nextBytes(arr)
        return Base64.encodeToString(arr, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun generateNonce(): String {
        val arr = ByteArray(16)
        secureRandom.nextBytes(arr)
        return Base64.encodeToString(arr, Base64.URL_SAFE or Base64.NO_WRAP)
    }
}