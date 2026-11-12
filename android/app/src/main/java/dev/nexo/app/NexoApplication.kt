package dev.nexo.app

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class NexoApplication : Application() {

    companion object {
        private const val PREFS_NAME = "nexo_secrets"
        private const val MASTER_KEY_ALIAS = "nexo_master_key"
        
        @Volatile private var INSTANCE: NexoApplication? = null
        fun getInstance(): NexoApplication = INSTANCE!!
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }

    /** EncryptedSharedPreferences para segredos (API keys, tokens) */
    val encryptedPrefs by lazy {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}