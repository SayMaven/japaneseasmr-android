package com.saymaven.downloader.japaneseasmr.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted key-value storage backed by Android Keystore (AES-256-GCM).
 * Used for sensitive credentials such as user-provided LLM API keys.
 */
object SecurePreferences {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "japaneseasmr_llm_aes_key"
    private const val PREFS_NAME = "secure_prefs"
    private const val PREF_KEY_LLM_API_KEY = "enc_llm_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val lock = Any()
    private val _llmApiKeyFlow = MutableStateFlow<String?>(null)

    fun llmApiKeyFlow(context: Context): Flow<String> {
        if (_llmApiKeyFlow.value == null) {
            _llmApiKeyFlow.value = getLlmApiKey(context)
        }
        return kotlinx.coroutines.flow.flow {
            _llmApiKeyFlow.asStateFlow().collect { value ->
                emit(value ?: getLlmApiKey(context))
            }
        }
    }

    fun getLlmApiKey(context: Context): String {
        synchronized(lock) {
            val prefs = getPrefs(context)
            val encoded = prefs.getString(PREF_KEY_LLM_API_KEY, null)
            if (encoded.isNullOrEmpty()) {
                _llmApiKeyFlow.value = ""
                return ""
            }
            return try {
                val decrypted = decrypt(encoded)
                _llmApiKeyFlow.value = decrypted
                decrypted
            } catch (e: Exception) {
                AppLogger.w("SecurePreferences", "Failed to decrypt stored API key: ${e.message}")
                ""
            }
        }
    }

    fun setLlmApiKey(context: Context, apiKey: String) {
        synchronized(lock) {
            val prefs = getPrefs(context)
            if (apiKey.isBlank()) {
                prefs.edit().remove(PREF_KEY_LLM_API_KEY).apply()
                _llmApiKeyFlow.value = ""
                return
            }
            try {
                val encrypted = encrypt(apiKey)
                prefs.edit().putString(PREF_KEY_LLM_API_KEY, encrypted).apply()
                _llmApiKeyFlow.value = apiKey
            } catch (e: Exception) {
                AppLogger.e("SecurePreferences", "Failed to encrypt API key", e)
            }
        }
    }

    /**
     * One-time migration helper: if a plaintext key exists and encrypted storage is empty,
     * encrypts and stores it, returning true so the caller can erase the plaintext copy.
     */
    fun migratePlaintextKeyIfNeeded(context: Context, plaintextKey: String?): Boolean {
        if (plaintextKey.isNullOrBlank()) return false
        synchronized(lock) {
            val existing = getLlmApiKey(context)
            if (existing.isNotBlank()) {
                return true
            }
            setLlmApiKey(context, plaintextKey)
            return true
        }
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH) { "Invalid encrypted payload" }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, StandardCharsets.UTF_8)
    }
}
