package com.jev.overseas.assistant

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.jev.overseas.core.engine.Assistant
import com.jev.overseas.core.net.ModelConfig
import com.jev.overseas.core.net.OpenRouterGateway
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.session.SelectionMemory
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Everything the app stores: the encrypted key, the
 * two model ids, spelling, the scene picked per chat
 * (under salted hashes), the bubble position and the "Analyse when opened"
 * switch. Never any chat content or contact details.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val vault = KeyVault(prefs)

    // ------------------------------------------------------------------ key

    val hasKey: Boolean get() = vault.load() != null

    fun saveKey(key: String) = vault.save(key.trim())

    fun removeKey() = vault.clear()

    /** "sk-or-…" plus the last four characters. The full key is never shown again. */
    val maskedKey: String?
        get() = vault.load()?.let { k -> k.take(KEY_PREFIX_SHOWN) + "…" + k.takeLast(4) }

    // ------------------------------------------------------------------ models and spelling

    var jevModel: String
        get() = prefs.getString(JEV_MODEL, null) ?: ModelConfig.DEFAULT_JEV_MODEL
        set(value) = prefs.edit().putString(JEV_MODEL, value.trim().ifEmpty { ModelConfig.DEFAULT_JEV_MODEL }).apply()

    var draftModel: String
        get() = prefs.getString(DRAFT_MODEL, null) ?: ModelConfig.DEFAULT_DRAFT_MODEL
        set(value) = prefs.edit().putString(DRAFT_MODEL, value.trim().ifEmpty { ModelConfig.DEFAULT_DRAFT_MODEL }).apply()

    fun resetModels() = prefs.edit().remove(JEV_MODEL).remove(DRAFT_MODEL).apply()

    /** "AUTO", "US" or "UK". */
    var spelling: String
        get() = prefs.getString(SPELLING, null) ?: "AUTO"
        set(value) = prefs.edit().putString(SPELLING, value).apply()

    /** Local diagnostic log for bug reports (on while the app is in development). */
    var keepDiagnostics: Boolean
        get() = prefs.getBoolean(DIAGNOSTICS, true)
        set(value) = prefs.edit().putBoolean(DIAGNOSTICS, value).apply()

    var analyseWhenOpened: Boolean
        get() = prefs.getBoolean(AUTO_ANALYSE, true)
        set(value) = prefs.edit().putBoolean(AUTO_ANALYSE, value).apply()

    // ------------------------------------------------------------------ selection and bubble

    /**
     * The scene and relationship picked for each chat. The key is the reader's
     * chat id hashed again with a random per-install salt, so the stored keys say
     * nothing about who the chats are with. The oldest entries go past [MAX_CHATS].
     */
    val chatMemory: SelectionMemory = object : SelectionMemory {
        override fun get(chatId: String): Selection? = synchronized(this@Settings) {
            val entry = chats()[chatKey(chatId)] ?: return null
            val scene = SceneCatalog.scene(entry.optString("scene")) ?: return null
            Selection(scene, SceneCatalog.relationship(scene, entry.optString("relationship").ifEmpty { null }), entry.optBoolean("conflict"))
        }

        override fun put(chatId: String, selection: Selection) = synchronized(this@Settings) {
            val all = chats()
            all.remove(chatKey(chatId))
            all[chatKey(chatId)] = JSONObject()
                .put("scene", selection.scene.id)
                .put("relationship", selection.relationship?.id ?: "")
                .put("conflict", selection.conflict)
            while (all.size > MAX_CHATS) all.remove(all.keys.first())
            val json = JSONObject()
            for ((k, v) in all) json.put(k, v)
            prefs.edit().putString(CHATS, json.toString()).apply()
        }
    }

    val rememberedChats: Int get() = synchronized(this) { chats().size }

    /** Also clears the single global selection kept by versions before per-chat memory. */
    fun forgetChats() = synchronized(this) { prefs.edit().remove(CHATS).remove(SCENE).remove(RELATIONSHIP).remove(CONFLICT).apply() }

    /** Oldest first: JSONObject keeps insertion order on Android. */
    private fun chats(): LinkedHashMap<String, JSONObject> {
        val out = LinkedHashMap<String, JSONObject>()
        val json = runCatching { JSONObject(prefs.getString(CHATS, null) ?: return out) }.getOrNull() ?: return out
        for (k in json.keys()) json.optJSONObject(k)?.let { out[k] = it }
        return out
    }

    private fun chatKey(chatId: String): String {
        val salt = prefs.getString(CHAT_SALT, null) ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
            .let { Base64.encodeToString(it, Base64.NO_WRAP) }.also { prefs.edit().putString(CHAT_SALT, it).apply() }
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$chatId".toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    /** Bubble position: which edge, and the vertical position as a fraction of the screen height. */
    var bubbleOnLeft: Boolean
        get() = prefs.getBoolean(BUBBLE_LEFT, false)
        set(value) = prefs.edit().putBoolean(BUBBLE_LEFT, value).apply()

    var bubbleY: Float
        get() = prefs.getFloat(BUBBLE_Y, 0.42f)
        set(value) = prefs.edit().putFloat(BUBBLE_Y, value).apply()

    // ------------------------------------------------------------------ wiring

    fun modelConfig(): ModelConfig? = vault.load()?.let { ModelConfig(apiKey = it, jevModel = jevModel, draftModel = draftModel) }

    /** A fresh engine with the current key and settings, or null without a key (S12). */
    fun assistant(): Assistant? = modelConfig()?.let { config ->
        Assistant(OpenRouterGateway(config), defaultSpelling = "US", fixedSpelling = spelling.takeIf { it != "AUTO" })
    }

    companion object {
        private const val FILE = "jev_settings"
        private const val JEV_MODEL = "jev_model"
        private const val DRAFT_MODEL = "draft_model"
        private const val SPELLING = "spelling"
        private const val AUTO_ANALYSE = "analyse_when_opened"
        private const val DIAGNOSTICS = "keep_diagnostics"
        private const val SCENE = "scene"
        private const val RELATIONSHIP = "relationship"
        private const val CONFLICT = "conflict"
        private const val BUBBLE_LEFT = "bubble_left"
        private const val BUBBLE_Y = "bubble_y"
        private const val CHATS = "chat_selections"
        private const val CHAT_SALT = "chat_salt"
        private const val MAX_CHATS = 200
        private const val KEY_PREFIX_SHOWN = 6

        val SCENES = Scene.values().toList()
    }
}

/**
 * The OpenRouter key, encrypted with an AES-GCM key that lives in the Android
 * Keystore and never leaves it. Only the ciphertext is in SharedPreferences.
 */
private class KeyVault(private val prefs: SharedPreferences) {

    fun save(plain: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(DATA, Base64.encodeToString(sealed, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? {
        val iv = prefs.getString(IV, null) ?: return null
        val data = prefs.getString(DATA, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear() = prefs.edit().remove(IV).remove(DATA).apply()

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "jev_openrouter_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV = "key_iv"
        private const val DATA = "key_data"
    }
}
