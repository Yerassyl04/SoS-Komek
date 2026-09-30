package kz.soskomek.app.sos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kz.soskomek.app.MainActivity
import kz.soskomek.app.R
import kz.soskomek.app.container
import kz.soskomek.app.data.SosReason
import kz.soskomek.app.data.SyncStatus
import kz.soskomek.app.data.UserProfile
import kz.soskomek.app.ui.labelRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object Network {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

object LocaleHelper {
    const val RU = "ru"
    const val KK = "kk"

    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val lang = if (!locales.isEmpty) locales[0]?.language else Locale.getDefault().language
        return if (lang == KK) KK else RU
    }

    fun currentLocale(): Locale = Locale.forLanguageTag(currentTag())

    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tag))
    }

    /** Context whose resources use the in-app language (also for code outside an Activity). */
    fun localized(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(currentLocale())
        return context.createConfigurationContext(config)
    }
}

object Intents {
    /** Direct call when CALL_PHONE is granted, otherwise opens the dialer with the number filled in. */
    fun call(context: Context, number: String, direct: Boolean = true) {
        val uri = Uri.parse("tel:$number")
        val canCall = direct && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Fallback when SEND_SMS is not granted: the user presses "Send" in their SMS app. */
    fun composeSms(context: Context, phones: List<String>, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phones.joinToString(";")))
            .putExtra("sms_body", body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openMap(context: Context, lat: Double, lon: Double) {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(SosMessageBuilder.mapLink(lat, lon))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }

    fun openAppSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

object SosMessageBuilder {
    fun coords(lat: Double, lon: Double) = String.format(Locale.US, "%.5f, %.5f", lat, lon)

    fun mapLink(lat: Double, lon: Double) = String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", lat, lon)

    fun build(
        ctx: Context,
        profile: UserProfile,
        reason: SosReason,
        location: Location?,
        locationRequested: Boolean,
        time: Long,
        includeMedical: Boolean,
    ): String = buildString {
        val name = profile.fullName.ifBlank { ctx.getString(R.string.sms_unknown_user) }
        appendLine(ctx.getString(R.string.sms_header, name))
        appendLine(ctx.getString(R.string.sms_reason, ctx.getString(reason.labelRes)))
        appendLine(ctx.getString(R.string.sms_time, SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(time))))
        if (location != null) {
            val accuracy = if (location.hasAccuracy()) ctx.getString(R.string.sms_accuracy, location.accuracy.roundToInt()) else ""
            appendLine(ctx.getString(R.string.sms_coords, coords(location.latitude, location.longitude)) + accuracy)
            appendLine(mapLink(location.latitude, location.longitude))
        } else {
            appendLine(ctx.getString(if (locationRequested) R.string.sms_location_unavailable else R.string.sms_location_not_shared))
        }
        if (reason == SosReason.MEDICAL && includeMedical) {
            if (profile.bloodGroup.isNotBlank()) appendLine(ctx.getString(R.string.sms_blood, profile.bloodGroup))
            if (profile.medicalNotes.isNotBlank()) appendLine(ctx.getString(R.string.sms_medical, profile.medicalNotes.take(160)))
        }
        if (profile.phone.isNotBlank()) appendLine(ctx.getString(R.string.sms_phone, profile.phone))
        append(ctx.getString(R.string.sms_footer))
    }

    fun safe(ctx: Context, profile: UserProfile): String =
        ctx.getString(R.string.sms_safe, profile.fullName.ifBlank { ctx.getString(R.string.sms_unknown_user) })

    fun invite(ctx: Context, profile: UserProfile): String =
        ctx.getString(R.string.sms_invite, profile.fullName.ifBlank { ctx.getString(R.string.sms_unknown_user) })
}

object Notifications {
    private const val CHANNEL_SOS = "sos_alerts"
    private const val SOS_NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val ctx = LocaleHelper.localized(context)
        val channel = NotificationChannel(CHANNEL_SOS, ctx.getString(R.string.notif_channel_sos), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showSosSent(context: Context, reason: SosReason, notified: Int, total: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val ctx = LocaleHelper.localized(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val dial = PendingIntent.getActivity(
            context, 1,
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${reason.emergencyNumber}")),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_stat_sos)
            .setContentTitle(ctx.getString(R.string.notif_sos_title))
            .setContentText(ctx.getString(R.string.notif_sos_text, notified, total))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .addAction(0, ctx.getString(R.string.call_number, reason.emergencyNumber), dial)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(SOS_NOTIFICATION_ID, notification) }
    }
}

/** Sends SOS events, reports and complaints that were saved while offline. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        var ok = true
        c.sosHistory.events.value.filter { it.syncStatus == SyncStatus.PENDING }.forEach { event ->
            when {
                !c.api.isConfigured -> c.sosHistory.update(event.id) { it.copy(syncStatus = SyncStatus.LOCAL_ONLY) }
                c.api.sendSosEvent(event) -> c.sosHistory.update(event.id) { it.copy(syncStatus = SyncStatus.SYNCED) }
                else -> ok = false
            }
        }
        if (!c.incidents.syncPending()) ok = false
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sos_sync", ExistingWorkPolicy.KEEP, request)
        }
    }
}
