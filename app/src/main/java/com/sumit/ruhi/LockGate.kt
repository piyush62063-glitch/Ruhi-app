package com.sumit.ruhi

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import java.security.MessageDigest

/**
 * Locked phone ka bola hua password (default: sumit).
 * Sahi password ke baad 2 minute tak Ruhi kaam karti hai. 3 galat try par 1 minute lock.
 * Password sirf hash ke roop mein save hota hai, aur Hindi/English dono tarah bolne par match hota hai.
 */
object LockGate {
    private const val DEFAULT_PASS = "sumit"
    private const val WINDOW_MS = 2 * 60 * 1000L
    private const val LOCKOUT_MS = 60 * 1000L

    @Volatile private var authUntil = 0L
    @Volatile private var lockUntil = 0L
    @Volatile private var wrong = 0

    fun isLocked(c: Context): Boolean = try {
        (c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
    } catch (e: Exception) { false }

    /** Phone locked hai, gate ON hai aur abhi password nahi suna gaya. */
    fun needsAuth(c: Context): Boolean =
        Store.gate(c) && isLocked(c) && SystemClock.elapsedRealtime() >= authUntil

    fun grant() {
        authUntil = SystemClock.elapsedRealtime() + WINDOW_MS
        wrong = 0
    }

    fun revoke() { authUntil = 0L }

    /** Galat try ginta hai, teesri galti par 1 minute ka lock lagata hai. Ab tak ki galtiyan lautata hai. */
    fun fail(): Int {
        wrong++
        if (wrong >= 3) {
            lockUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS
            wrong = 0
            return 3
        }
        return wrong
    }

    fun lockLeft(): Int {
        val d = lockUntil - SystemClock.elapsedRealtime()
        return if (d > 0) ((d + 999) / 1000).toInt() else 0
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Awaaz se aaye shabd ka dhaancha: Hindi ya English, spelling ka fark chal jata hai. */
    fun passKey(s: String): String = Actions.skel(s).replace("nb", "mb").replace("np", "mp")

    private fun curHash(c: Context): String {
        val h = Store.passHash(c)
        return if (h.isBlank()) sha(passKey(DEFAULT_PASS)) else h
    }

    private fun curN(c: Context): Int = if (Store.passHash(c).isBlank()) 1 else Store.passN(c)

    /** Naya password (1 se 3 shabd). Bahut chhota ho to false. */
    fun setPass(c: Context, p: String): Boolean {
        val words = p.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 3) return false
        val k = passKey(words.joinToString(" "))
        if (k.length < 3) return false
        Store.setPass(c, sha(k), words.size)
        return true
    }

    /**
     * Bole hue text mein password mile to uske alawa bacha hua text lautata hai
     * (sirf password ho to khali string), warna null.
     */
    fun consume(c: Context, text: String): String? {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val n = curN(c)
        val h = curHash(c)
        if (words.size < n) return null
        for (i in 0..(words.size - n)) {
            val w = words.subList(i, i + n).joinToString(" ")
            if (sha(passKey(w)) == h) {
                val rest = ArrayList<String>()
                for (j in words.indices) if (j < i || j >= i + n) rest.add(words[j])
                return rest.joinToString(" ")
            }
        }
        return null
    }
}
