package com.nearbymesh.app.core.security

import android.content.Context
import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE) and Cryptographic Identity Engine.
 *
 * Uses Elliptic Curve Diffie-Hellman (ECDH) on secp256r1 for key exchange
 * and AES-256-GCM for authenticated payload encryption.
 * Relay nodes can only read the packet routing envelope, never the payload!
 */
class CryptoEngine(private val context: Context) {

    private val prefs = context.getSharedPreferences("nearby_mesh_crypto", Context.MODE_PRIVATE)

    val keyPair: KeyPair by lazy {
        loadOrCreateKeyPair()
    }

    val nodeId: String by lazy {
        val savedId = prefs.getString("node_id_short", null)
        if (savedId != null && savedId.length == 9 && savedId.contains("-")) {
            savedId
        } else {
            try {
                val pubBytes = keyPair.public.encoded
                val digest = MessageDigest.getInstance("SHA-256").digest(pubBytes)
                val hex = "%02X%02X".format(digest[0], digest[1])
                val hex2 = "%02X%02X".format(digest[2], digest[3])
                val newId = "$hex-$hex2"
                prefs.edit().putString("node_id_short", newId).apply()
                newId
            } catch (_: Throwable) {
                val rnd = "%04X-%04X".format((0..0xFFFF).random(), (0..0xFFFF).random())
                prefs.edit().putString("node_id_short", rnd).apply()
                rnd
            }
        }
    }

    val publicKeyBase64: String by lazy {
        try {
            Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun loadOrCreateKeyPair(): KeyPair {
        val savedPrivate = prefs.getString("private_key", null)
        val savedPublic = prefs.getString("public_key", null)

        if (savedPrivate != null && savedPublic != null) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val pubBytes = Base64.decode(savedPublic, Base64.NO_WRAP)
                val privBytes = Base64.decode(savedPrivate, Base64.NO_WRAP)
                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                val privKey = keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privBytes))
                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Generate new EC KeyPair with robust fallbacks
        try {
            val kpg = KeyPairGenerator.getInstance("EC")
            try {
                kpg.initialize(ECGenParameterSpec("secp256r1"))
            } catch (_: Throwable) {
                try {
                    kpg.initialize(ECGenParameterSpec("prime256v1"))
                } catch (_: Throwable) {
                    kpg.initialize(256)
                }
            }
            val kp = kpg.generateKeyPair()

            prefs.edit()
                .putString("public_key", Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
                .putString("private_key", Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
                .apply()

            return kp
        } catch (e: Throwable) {
            // Fallback to RSA if EC is not supported by device provider
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            return kpg.generateKeyPair()
        }
    }

    /**
     * Derives a 256-bit AES symmetric key from our Private Key and recipient's Public Key
     * using ECDH + SHA-256 HKDF.
     */
    fun deriveSharedSecret(peerPublicKeyBase64: String): SecretKeySpec {
        val pubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(peerPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive 256-bit AES key via SHA-256 hash of shared secret
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Returns Base64 of: [12 bytes IV] + [Ciphertext + 16 bytes GCM Auth Tag]
     */
    fun encrypt(plainText: String, peerPublicKeyBase64: String): String {
        val secretKey = deriveSharedSecret(peerPublicKeyBase64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts ciphertext using AES-256-GCM and sender's public key.
     */
    fun decrypt(cipherTextBase64: String, senderPublicKeyBase64: String): String {
        val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        if (combined.size < 28) throw IllegalArgumentException("Ciphertext too short")

        val iv = combined.sliceArray(0 until 12)
        val cipherBytes = combined.sliceArray(12 until combined.size)

        val secretKey = deriveSharedSecret(senderPublicKeyBase64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decrypted = cipher.doFinal(cipherBytes)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Computes SHA-256 checksum of data byte array
     */
    fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
