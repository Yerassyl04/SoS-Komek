package kz.soskomek.app.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Fused location (Google Play services) with a fallback to the platform LocationManager,
 * so it also works on devices without Google services.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        runCatching { withTimeoutOrNull(12_000) { fusedCurrent() } }.getOrNull()?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { withTimeoutOrNull(8_000) { platformCurrent() } }.getOrNull()?.let { return it }
        }
        return lastKnown()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fusedCurrent(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(30_000)
            .build()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(request, cts.token)
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        cont.invokeOnCancellation { cts.cancel() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun platformCurrent(): Location? = suspendCancellableCoroutine { cont ->
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = when {
            granted(Manifest.permission.ACCESS_FINE_LOCATION) && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val signal = CancellationSignal()
        lm.getCurrentLocation(provider, signal, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
        cont.invokeOnCancellation { signal.cancel() }
    }

    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): Location? {
        if (!hasPermission()) return null
        val fused = runCatching {
            withTimeoutOrNull(3_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    LocationServices.getFusedLocationProviderClient(context).lastLocation
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
        }.getOrNull()
        if (fused != null) return fused
        val lm = context.getSystemService(LocationManager::class.java)
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
