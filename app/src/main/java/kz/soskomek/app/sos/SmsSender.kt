package kz.soskomek.app.sos

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Sends SMS directly through the SIM card; works without internet. */
class SmsSender(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    private fun manager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    /**
     * Returns true when the operator accepted every part of the message.
     * If no result arrives within 45 s the message was already handed to the system, so it is treated as sent.
     */
    suspend fun send(phone: String, text: String): Boolean {
        if (!hasPermission()) return false
        val sms = runCatching { manager() }.getOrNull() ?: return false
        val parts = sms.divideMessage(text)
        val action = "${context.packageName}.SMS_SENT.${UUID.randomUUID()}"

        val result = withTimeoutOrNull(45_000) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                var remaining = parts.size
                var allOk = true
                lateinit var receiver: BroadcastReceiver

                fun finish(ok: Boolean) {
                    if (done.compareAndSet(false, true)) {
                        runCatching { context.unregisterReceiver(receiver) }
                        if (cont.isActive) cont.resume(ok)
                    }
                }

                receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        if (resultCode != Activity.RESULT_OK) allOk = false
                        remaining--
                        if (remaining <= 0) finish(allOk)
                    }
                }
                ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)

                val sentIntents = ArrayList<PendingIntent>()
                parts.indices.forEach { index ->
                    sentIntents += PendingIntent.getBroadcast(
                        context,
                        index,
                        Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                    )
                }
                try {
                    if (parts.size == 1) sms.sendTextMessage(phone, null, text, sentIntents[0], null)
                    else sms.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
                } catch (e: Exception) {
                    finish(false)
                }
                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) runCatching { context.unregisterReceiver(receiver) }
                }
            }
        }
        return result ?: true
    }
}
