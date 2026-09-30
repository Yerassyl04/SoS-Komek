package kz.soskomek.app.data

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kz.soskomek.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import java.util.UUID
import kotlin.math.ceil

sealed interface SubmitResult {
    data class Published(val personalDataRemoved: Boolean) : SubmitResult
    data object SentToModeration : SubmitResult
    data class Rejected(@StringRes val reason: Int) : SubmitResult
    data class RateLimited(val minutes: Int) : SubmitResult
}

enum class ComplaintReason(val code: String) { FALSE_INFO("false"), DANGEROUS("dangerous"), PERSONAL_DATA("personal_data"), OTHER("other") }

class IncidentRepository(private val storage: SecureStorage, private val api: RemoteApi) {

    private val _items = MutableStateFlow<List<Incident>>(emptyList())
    val items: StateFlow<List<Incident>> = _items.asStateFlow()

    private var reportTimes = mutableListOf<Long>()
    private var recentTexts = mutableListOf<String>()
    private var pendingComplaints = mutableListOf<JSONObject>()

    init {
        val text = storage.readText(FILE)
        _items.value = text?.let { t ->
            runCatching {
                val arr = JSONArray(t)
                (0 until arr.length()).map { Incident.fromJson(arr.getJSONObject(it)) }
            }.getOrNull()
        } ?: DemoData.generate()
        storage.readText(META)?.let { t ->
            runCatching {
                val o = JSONObject(t)
                o.optJSONArray("reportTimes")?.let { a -> reportTimes = (0 until a.length()).map { a.getLong(it) }.toMutableList() }
                o.optJSONArray("recentTexts")?.let { a -> recentTexts = (0 until a.length()).map { a.getString(it) }.toMutableList() }
                o.optJSONArray("pendingComplaints")?.let { a -> pendingComplaints = (0 until a.length()).map { a.getJSONObject(it) }.toMutableList() }
            }
        }
        if (text == null) persist()
    }

    @Synchronized
    private fun persist() {
        storage.writeText(FILE, JSONArray().apply { _items.value.forEach { put(it.toJson()) } }.toString())
        storage.writeText(
            META,
            JSONObject()
                .put("reportTimes", JSONArray(reportTimes))
                .put("recentTexts", JSONArray(recentTexts))
                .put("pendingComplaints", JSONArray().apply { pendingComplaints.forEach { put(it) } })
                .toString(),
        )
    }

    /**
     * Anti-spam: at most [MAX_REPORTS_PER_DAY] reports per day, one per [MIN_INTERVAL_MS],
     * no duplicates. Text is scrubbed of personal data and exact location is approximated.
     */
    @Synchronized
    fun submit(category: IncidentCategory, latitude: Double, longitude: Double, occurredAt: Long, rawDescription: String): SubmitResult {
        val now = System.currentTimeMillis()
        reportTimes.removeAll { now - it > DAY_MS }
        if (reportTimes.size >= MAX_REPORTS_PER_DAY) {
            return SubmitResult.RateLimited(minutesUntil(reportTimes.min() + DAY_MS, now))
        }
        reportTimes.maxOrNull()?.let { last ->
            if (now - last < MIN_INTERVAL_MS) return SubmitResult.RateLimited(minutesUntil(last + MIN_INTERVAL_MS, now))
        }
        val outcome = Moderation.check(rawDescription)
        outcome.rejectReason?.let { return SubmitResult.Rejected(it) }
        val fingerprint = outcome.cleanText.lowercase().filter { it.isLetterOrDigit() }
        if (fingerprint in recentTexts) return SubmitResult.Rejected(R.string.mod_duplicate)

        val (lat, lon) = LocationFuzzer.approximate(latitude, longitude)
        val incident = Incident(
            id = UUID.randomUUID().toString(),
            category = category,
            latitude = lat,
            longitude = lon,
            occurredAt = occurredAt,
            description = outcome.cleanText,
            createdAt = now,
            status = if (outcome.needsReview) IncidentStatus.PENDING else IncidentStatus.PUBLISHED,
            isOwn = true,
            synced = false,
        )
        _items.value = listOf(incident) + _items.value
        reportTimes.add(now)
        recentTexts = (listOf(fingerprint) + recentTexts).take(20).toMutableList()
        persist()
        return if (outcome.needsReview) SubmitResult.SentToModeration
        else SubmitResult.Published(outcome.personalDataRemoved)
    }

    /** Hides the publication for this user right away and queues the complaint for moderators. */
    @Synchronized
    fun complain(id: String, reason: ComplaintReason) {
        _items.value = _items.value.map { if (it.id == id) it.copy(complainedByMe = true) else it }
        pendingComplaints.add(JSONObject().put("incidentId", id).put("reason", reason.code).put("createdAt", System.currentTimeMillis()))
        persist()
    }

    fun hasPendingSync(): Boolean = _items.value.any { it.isOwn && !it.synced } || pendingComplaints.isNotEmpty()

    /** Returns false when the server is reachable but refused, so the worker retries. */
    suspend fun syncPending(): Boolean {
        if (!api.isConfigured) return true
        var ok = true
        _items.value.filter { it.isOwn && !it.synced }.forEach { incident ->
            if (api.post("/v1/incidents", incident.toServerJson())) {
                synchronized(this) {
                    _items.value = _items.value.map { if (it.id == incident.id) it.copy(synced = true) else it }
                    persist()
                }
            } else ok = false
        }
        pendingComplaints.toList().forEach { complaint ->
            if (api.post("/v1/incidents/${complaint.getString("incidentId")}/complaints", complaint)) {
                synchronized(this) {
                    pendingComplaints.remove(complaint)
                    persist()
                }
            } else ok = false
        }
        return ok
    }

    /** Pulls moderated publications around a point from the server (if configured). */
    suspend fun refreshFromServer(latitude: Double, longitude: Double) {
        if (!api.isConfigured) return
        val arr = api.getArray("/v1/incidents?lat=$latitude&lon=$longitude&radiusKm=50&days=31") ?: return
        val remote = (0 until arr.length()).mapNotNull { i ->
            runCatching { Incident.fromJson(arr.getJSONObject(i)).copy(isOwn = false, isDemo = false, synced = true) }.getOrNull()
        }
        synchronized(this) {
            val complained = _items.value.filter { it.complainedByMe }.map { it.id }.toSet()
            val local = _items.value.filter { it.isOwn || it.isDemo }
            val localIds = local.map { it.id }.toSet()
            _items.value = local + remote.filter { it.id !in localIds }.map { it.copy(complainedByMe = it.id in complained) }
            persist()
        }
    }

    @Synchronized
    fun clearPersonal() {
        _items.value = _items.value.filterNot { it.isOwn }
        reportTimes.clear()
        recentTexts.clear()
        pendingComplaints.clear()
        persist()
    }

    private fun minutesUntil(time: Long, now: Long) = ceil((time - now) / 60_000.0).toInt().coerceAtLeast(1)

    private companion object {
        const val FILE = "incidents.bin"
        const val META = "incidents_meta.bin"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val MIN_INTERVAL_MS = 10 * 60 * 1000L
        const val MAX_REPORTS_PER_DAY = 3
    }
}

/** Sample publications so the map is not empty before a server is connected. Clearly labelled in the UI. */
object DemoData {
    private val cities = listOf(
        43.2380 to 76.8890, // Almaty
        51.1280 to 71.4300, // Astana
        42.3150 to 69.5900, // Shymkent
    )

    fun generate(): List<Incident> {
        val random = Random(2026)
        val now = System.currentTimeMillis()
        val hour = 60 * 60 * 1000L
        return cities.flatMapIndexed { cityIndex, (lat, lon) ->
            (0 until 8).map { i ->
                val category = IncidentCategory.entries[(i + cityIndex) % IncidentCategory.entries.size]
                val ageHours = when (i % 4) {
                    0 -> random.nextInt(10) + 1L
                    1 -> random.nextInt(24 * 6) + 24L
                    else -> random.nextInt(24 * 25) + 24L * 7
                }
                val (fLat, fLon) = LocationFuzzer.approximate(
                    lat + (random.nextDouble() - 0.5) * 0.08,
                    lon + (random.nextDouble() - 0.5) * 0.1,
                )
                Incident(
                    id = "demo-$cityIndex-$i",
                    category = category,
                    latitude = fLat,
                    longitude = fLon,
                    occurredAt = now - ageHours * hour,
                    description = "",
                    createdAt = now - ageHours * hour,
                    isDemo = true,
                )
            }
        }
    }
}
