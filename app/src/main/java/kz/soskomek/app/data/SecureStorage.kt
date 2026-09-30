package kz.soskomek.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts every file with AES-256-GCM. The key lives in the Android Keystore
 * (hardware-backed where available) and never leaves the device.
 */
class SecureStorage(private val context: Context) {

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val ivLength = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data, 1 + ivLength, data.size - 1 - ivLength)
    }

    @Synchronized
    fun writeBytes(name: String, plain: ByteArray) {
        val file = AtomicFile(File(context.filesDir, name))
        val out = file.startWrite()
        try {
            out.write(encrypt(plain))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    @Synchronized
    fun readBytes(name: String): ByteArray? {
        val raw = File(context.filesDir, name)
        if (!raw.exists()) return null
        return try {
            decrypt(AtomicFile(raw).readFully())
        } catch (e: Exception) {
            null
        }
    }

    fun writeText(name: String, text: String) = writeBytes(name, text.toByteArray(Charsets.UTF_8))

    fun readText(name: String): String? = readBytes(name)?.toString(Charsets.UTF_8)

    @Synchronized
    fun delete(name: String) {
        AtomicFile(File(context.filesDir, name)).delete()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sos_komek_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
