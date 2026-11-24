package com.example.trackerfinder

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional password that hides the app (locations, history, login export) from someone holding the phone.
 * Only a salted PBKDF2 hash is stored. It does not encrypt the data on disk.
 */
object AppLock {
    private const val PREFS = "applock"
    private const val ITER = 120_000
    /** Lock again after the app has been in the background this long. */
    const val RELOCK_AFTER_MS = 60_000L

    private var unlocked = false
    private var backgroundedAt = 0L

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = p(ctx).contains("hash")
    fun hint(ctx: Context): String? = p(ctx).getString("hint", null)?.takeIf { it.isNotBlank() }

    fun needsUnlock(ctx: Context) = isEnabled(ctx) && !unlocked

    fun set(ctx: Context, password: String, hint: String?) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(ctx).edit()
            .putString("salt", b64(salt))
            .putString("hash", b64(hash(password, salt)))
            .putString("hint", hint?.trim())
            .apply()
        unlocked = true
    }

    fun disable(ctx: Context) {
        p(ctx).edit().clear().apply()
        unlocked = false
    }

    fun check(ctx: Context, password: String): Boolean {
        val salt = p(ctx).getString("salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val want = p(ctx).getString("hash", null) ?: return false
        val ok = java.security.MessageDigest.isEqual(b64(hash(password, salt)).toByteArray(), want.toByteArray())
        if (ok) unlocked = true
        return ok
    }

    fun onBackground() { backgroundedAt = SystemClock.elapsedRealtime() }

    /** Called when the app returns; relocks if it was away long enough. */
    fun onForeground() {
        if (backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) unlocked = false
        backgroundedAt = 0
    }

    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITER, 256)).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}
