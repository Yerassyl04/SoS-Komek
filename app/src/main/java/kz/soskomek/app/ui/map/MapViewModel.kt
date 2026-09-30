package kz.soskomek.app.ui.map

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.soskomek.app.container
import kz.soskomek.app.data.ComplaintReason
import kz.soskomek.app.data.Incident
import kz.soskomek.app.data.IncidentCategory
import kz.soskomek.app.data.IncidentStatus
import java.util.Calendar

enum class Period(val days: Int) { TODAY(0), WEEK(7), MONTH(31) }

data class MapFilter(
    val categories: Set<IncidentCategory> = IncidentCategory.entries.toSet(),
    /** null = any distance */
    val distanceKm: Int? = 5,
    val period: Period = Period.WEEK,
)

data class MapUi(
    val incidents: List<Incident> = emptyList(),
    val filter: MapFilter = MapFilter(),
    val selected: Incident? = null,
    val demoShown: Boolean = false,
    val hasUserLocation: Boolean = false,
)

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val filter = MutableStateFlow(MapFilter())
    private val userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    private val mapCenter = MutableStateFlow(DEFAULT_CENTER)
    private val selectedId = MutableStateFlow<String?>(null)

    private val _centerRequests = MutableSharedFlow<Pair<Double, Double>>(extraBufferCapacity = 1)
    val centerRequests: SharedFlow<Pair<Double, Double>> = _centerRequests

    private val reference = combine(userLocation, mapCenter) { user, center -> user ?: center }

    val ui: StateFlow<MapUi> = combine(c.incidents.items, filter, reference, c.settings.state, selectedId) { items, f, ref, settings, selId ->
        val since = sinceMillis(f.period)
        val visible = items.filter { inc ->
            val statusOk = inc.status == IncidentStatus.PUBLISHED || (inc.isOwn && inc.status == IncidentStatus.PENDING)
            statusOk && !inc.complainedByMe &&
                (settings.showDemo || !inc.isDemo) &&
                inc.category in f.categories &&
                inc.occurredAt >= since &&
                (f.distanceKm == null || distanceM(ref, inc) <= f.distanceKm * 1000)
        }
        MapUi(
            incidents = visible,
            filter = f,
            selected = visible.firstOrNull { it.id == selId },
            demoShown = visible.any { it.isDemo },
            hasUserLocation = userLocation.value != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUi())

    val initialCenter: Pair<Double, Double> get() = userLocation.value ?: mapCenter.value

    fun toggleCategory(category: IncidentCategory) = filter.update {
        val set = if (category in it.categories) it.categories - category else it.categories + category
        it.copy(categories = set)
    }

    fun setDistance(km: Int?) = filter.update { it.copy(distanceKm = km) }
    fun setPeriod(period: Period) = filter.update { it.copy(period = period) }
    fun select(id: String?) { selectedId.value = id }
    fun setMapCenter(lat: Double, lon: Double) { mapCenter.value = lat to lon }

    fun locate(animate: Boolean = true) {
        viewModelScope.launch {
            val location = c.location.lastKnown() ?: c.location.currentLocation() ?: return@launch
            userLocation.value = location.latitude to location.longitude
            if (animate) _centerRequests.tryEmit(location.latitude to location.longitude)
            c.incidents.refreshFromServer(location.latitude, location.longitude)
        }
    }

    fun complain(id: String, reason: ComplaintReason) {
        c.incidents.complain(id, reason)
        selectedId.value = null
        kz.soskomek.app.sos.SyncWorker.enqueue(getApplication())
    }

    private fun distanceM(ref: Pair<Double, Double>, inc: Incident): Float {
        val out = FloatArray(1)
        Location.distanceBetween(ref.first, ref.second, inc.latitude, inc.longitude, out)
        return out[0]
    }

    private fun sinceMillis(period: Period): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -period.days)
        return cal.timeInMillis
    }

    companion object {
        val DEFAULT_CENTER = 43.2380 to 76.8890 // Almaty
    }
}
