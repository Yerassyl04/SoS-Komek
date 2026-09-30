package kz.soskomek.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kz.soskomek.app.BuildConfig
import kz.soskomek.app.sos.LocationProvider
import kz.soskomek.app.sos.SmsSender
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val storage = SecureStorage(app)
    val api = RemoteApi(BuildConfig.API_BASE_URL)
    val settings = SettingsRepository(app)
    val profile = ProfileRepository(app, storage)
    val sosHistory = SosRepository(storage)
    val incidents = IncidentRepository(storage, api)
    val location = LocationProvider(app)
    val sms = SmsSender(app)
    val verifier = PhoneVerifier()

    fun hasPendingSync(): Boolean =
        sosHistory.events.value.any { it.syncStatus == SyncStatus.PENDING } || incidents.hasPendingSync()

    /** "Delete all my data": profile, photo, medical info, contacts, SOS history, own reports. */
    fun wipeAll() {
        profile.clear()
        sosHistory.clear()
        incidents.clearPersonal()
        settings.reset()
    }
}

// ---------------------------------------------------------------- Settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val termsAccepted: Boolean = false,
    /** Explicit consent to attach location to SOS messages. */
    val locationConsent: Boolean = false,
    val includeMedical: Boolean = false,
    val showDemo: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lastSosAt: Long = 0L,
)

/** Non-sensitive preferences only. Personal data lives in [SecureStorage]. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        onboardingDone = prefs.getBoolean("onboarding_done", false),
        termsAccepted = prefs.getBoolean("terms_accepted", false),
        locationConsent = prefs.getBoolean("location_consent", false),
        includeMedical = prefs.getBoolean("include_medical", false),
        showDemo = prefs.getBoolean("show_demo", true),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
        lastSosAt = prefs.getLong("last_sos_at", 0L),
    )

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val old = _state.value
        val new = transform(old)
        prefs.edit()
            .putBoolean("onboarding_done", new.onboardingDone)
            .putBoolean("terms_accepted", new.termsAccepted)
            .putBoolean("location_consent", new.locationConsent)
            .putBoolean("include_medical", new.includeMedical)
            .putBoolean("show_demo", new.showDemo)
            .putString("theme", new.theme.name)
            .putLong("last_sos_at", new.lastSosAt)
            .apply()
        _state.value = new
        if (old.theme != new.theme) applyTheme()
    }

    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (_state.value.theme) {
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    fun reset() {
        prefs.edit().clear().apply()
        _state.value = AppSettings()
        applyTheme()
    }
}

// ---------------------------------------------------------------- Profile

class ProfileRepository(private val context: Context, private val storage: SecureStorage) {
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _photoVersion = MutableStateFlow(0)
    val photoVersion: StateFlow<Int> = _photoVersion.asStateFlow()

    private fun load(): UserProfile = storage.readText(FILE)
        ?.let { runCatching { UserProfile.fromJson(JSONObject(it)) }.getOrNull() }
        ?: UserProfile()

    @Synchronized
    fun update(transform: (UserProfile) -> UserProfile) {
        val new = transform(_profile.value)
        storage.writeText(FILE, new.toJson().toString())
        _profile.value = new
    }

    /** Downscales the picked picture and stores it encrypted. Call off the main thread. */
    fun savePhoto(uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot decode image")
        val scale = PHOTO_SIZE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        storage.writeBytes(PHOTO_FILE, bytes)
        update { it.copy(hasPhoto = true) }
        _photoVersion.value++
    }.isSuccess

    fun loadPhoto(): Bitmap? = storage.readBytes(PHOTO_FILE)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    fun deletePhoto() {
        storage.delete(PHOTO_FILE)
        update { it.copy(hasPhoto = false) }
        _photoVersion.value++
    }

    fun clear() {
        storage.delete(FILE)
        storage.delete(PHOTO_FILE)
        _profile.value = UserProfile()
        _photoVersion.value++
    }

    private companion object {
        const val FILE = "profile.bin"
        const val PHOTO_FILE = "photo.bin"
        const val PHOTO_SIZE = 512
    }
}

// ---------------------------------------------------------------- SOS history

class SosRepository(private val storage: SecureStorage) {
    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<SosEvent>> = _events.asStateFlow()

    private fun load(): List<SosEvent> = storage.readText(FILE)?.let { text ->
        runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).map { SosEvent.fromJson(arr.getJSONObject(it)) }
        }.getOrNull()
    } ?: emptyList()

    private fun persist(list: List<SosEvent>) {
        storage.writeText(FILE, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        _events.value = list
    }

    @Synchronized
    fun add(event: SosEvent) = persist(listOf(event) + _events.value)

    @Synchronized
    fun update(id: String, transform: (SosEvent) -> SosEvent) =
        persist(_events.value.map { if (it.id == id) transform(it) else it })

    fun find(id: String): SosEvent? = _events.value.firstOrNull { it.id == id }

    @Synchronized
    fun delete(id: String) = persist(_events.value.filterNot { it.id == id })

    @Synchronized
    fun clear() {
        storage.delete(FILE)
        _events.value = emptyList()
    }

    private companion object {
        const val FILE = "sos_history.bin"
    }
}
