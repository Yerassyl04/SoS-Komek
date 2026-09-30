package kz.soskomek.app.sos

import android.app.Application
import android.location.Location
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.soskomek.app.R
import kz.soskomek.app.container
import kz.soskomek.app.data.SosEvent
import kz.soskomek.app.data.SosReason
import kz.soskomek.app.data.SyncStatus
import kz.soskomek.app.data.TrustedContact
import java.util.UUID

enum class StepState { PENDING, IN_PROGRESS, DONE, FAILED, SKIPPED }

data class SosUiState(
    val active: Boolean = false,
    val sending: Boolean = false,
    val showReasonDialog: Boolean = false,
    val showRepeatWarning: Boolean = false,
    val eventId: String? = null,
    val reason: SosReason? = null,
    val startedAt: Long = 0L,
    val shareLocation: Boolean = false,
    val locationStep: StepState = StepState.PENDING,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
    val contactsStep: StepState = StepState.PENDING,
    val contactsTotal: Int = 0,
    val contactsNotified: Int = 0,
    val failedContactIds: List<String> = emptyList(),
    val failedNames: List<String> = emptyList(),
    val noContacts: Boolean = false,
    val viaSmsApp: Boolean = false,
    val callStep: StepState = StepState.PENDING,
    val calledNumber: String? = null,
    val savedOffline: Boolean = false,
)

sealed interface SosEffect {
    data object OpenStatus : SosEffect
    data object Finished : SosEffect
    data class Call(val number: String) : SosEffect
    data class OpenSmsApp(val phones: List<String>, val body: String) : SosEffect
    data class Message(@StringRes val res: Int) : SosEffect
}

/**
 * SOS flow: hold 3 s → choose reason (or cancel) → location → SMS to trusted contacts →
 * user separately confirms the call to 102 / 103.
 */
class SosViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val _state = MutableStateFlow(SosUiState())
    val state: StateFlow<SosUiState> = _state.asStateFlow()

    private val _effects = Channel<SosEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var job: Job? = null
    private var lastLocation: Location? = null

    fun onHoldCompleted() {
        if (job?.isActive == true) {
            _effects.trySend(SosEffect.OpenStatus)
            return
        }
        val sinceLast = System.currentTimeMillis() - c.settings.state.value.lastSosAt
        when {
            // Protection against repeated accidental activation
            sinceLast < DUPLICATE_WINDOW_MS && _state.value.active -> {
                _effects.trySend(SosEffect.Message(R.string.sos_already_sent))
                _effects.trySend(SosEffect.OpenStatus)
            }
            sinceLast < REPEAT_CONFIRM_WINDOW_MS -> _state.update { it.copy(showRepeatWarning = true) }
            else -> _state.update { it.copy(showReasonDialog = true) }
        }
    }

    fun confirmRepeat() = _state.update { it.copy(showRepeatWarning = false, showReasonDialog = true) }

    fun dismissDialogs() = _state.update { it.copy(showReasonDialog = false, showRepeatWarning = false) }

    fun startSos(reason: SosReason, shareLocation: Boolean) {
        if (job?.isActive == true) return
        val now = System.currentTimeMillis()
        c.settings.update { it.copy(lastSosAt = now) }
        val recipients = c.profile.profile.value.sosRecipients
        val id = UUID.randomUUID().toString()
        lastLocation = null
        _state.value = SosUiState(
            active = true,
            sending = true,
            eventId = id,
            reason = reason,
            startedAt = now,
            shareLocation = shareLocation,
            locationStep = if (shareLocation) StepState.IN_PROGRESS else StepState.SKIPPED,
            contactsTotal = recipients.size,
        )
        c.sosHistory.add(SosEvent(id = id, timestamp = now, reason = reason, locationShared = shareLocation, contactsTotal = recipients.size))
        _effects.trySend(SosEffect.OpenStatus)

        job = viewModelScope.launch {
            if (shareLocation) {
                val location = c.location.currentLocation()
                lastLocation = location
                _state.update {
                    if (location != null) it.copy(
                        locationStep = StepState.DONE,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                    ) else it.copy(locationStep = StepState.FAILED)
                }
            }
            notifyContacts(recipients, retry = false)
            sync()
            _state.update { it.copy(sending = false) }
            val s = _state.value
            Notifications.showSosSent(getApplication(), reason, s.contactsNotified, s.contactsTotal)
        }
    }

    fun retryContacts() {
        if (job?.isActive == true) return
        val ids = _state.value.failedContactIds.toSet()
        val recipients = c.profile.profile.value.sosRecipients.filter { it.id in ids }
            .ifEmpty { c.profile.profile.value.sosRecipients }
        if (_state.value.noContacts) _state.update { it.copy(contactsTotal = recipients.size, noContacts = false) }
        job = viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            notifyContacts(recipients, retry = true)
            _state.update { it.copy(sending = false) }
        }
    }

    private suspend fun notifyContacts(recipients: List<TrustedContact>, retry: Boolean) {
        val reason = _state.value.reason ?: return
        if (recipients.isEmpty()) {
            _state.update { it.copy(contactsStep = StepState.FAILED, noContacts = true) }
            persist()
            return
        }
        _state.update { it.copy(contactsStep = StepState.IN_PROGRESS) }
        val ctx = LocaleHelper.localized(getApplication())
        val profile = c.profile.profile.value
        val settings = c.settings.state.value
        val s = _state.value

        fun message(locationAllowed: Boolean) = SosMessageBuilder.build(
            ctx, profile, reason,
            location = if (locationAllowed) lastLocation else null,
            locationRequested = s.shareLocation && locationAllowed,
            time = s.startedAt,
            includeMedical = settings.includeMedical,
        )

        if (c.sms.hasPermission()) {
            var ok = 0
            val failed = mutableListOf<TrustedContact>()
            for (r in recipients) {
                if (c.sms.send(r.phone, message(r.shareLocation))) ok++ else failed += r
            }
            _state.update {
                val notified = (if (retry) it.contactsNotified else 0) + ok
                it.copy(
                    contactsNotified = notified,
                    contactsStep = if (notified > 0) StepState.DONE else StepState.FAILED,
                    failedContactIds = failed.map { f -> f.id },
                    failedNames = failed.map { f -> f.name },
                    viaSmsApp = false,
                )
            }
        } else {
            // No SMS permission: open the SMS app with the text ready, user presses "Send".
            val text = message(recipients.all { it.shareLocation })
            _effects.send(SosEffect.OpenSmsApp(recipients.map { it.phone }, text))
            _state.update {
                it.copy(contactsStep = StepState.DONE, viaSmsApp = true, contactsNotified = recipients.size,
                    failedContactIds = emptyList(), failedNames = emptyList())
            }
        }
        persist()
    }

    private suspend fun sync() {
        val id = _state.value.eventId ?: return
        val event = c.sosHistory.find(id) ?: return
        val online = Network.isOnline(getApplication())
        val status = when {
            !online -> SyncStatus.PENDING
            !c.api.isConfigured -> SyncStatus.LOCAL_ONLY
            c.api.sendSosEvent(event) -> SyncStatus.SYNCED
            else -> SyncStatus.PENDING
        }
        c.sosHistory.update(id) { it.copy(syncStatus = status) }
        if (status == SyncStatus.PENDING) SyncWorker.enqueue(getApplication())
        _state.update { it.copy(savedOffline = !online) }
    }

    fun call(number: String) {
        _effects.trySend(SosEffect.Call(number))
        if (_state.value.active) {
            _state.update { it.copy(callStep = StepState.DONE, calledNumber = number) }
            persist()
        }
    }

    fun markSafe(notifyContacts: Boolean) {
        viewModelScope.launch {
            if (notifyContacts) {
                val profile = c.profile.profile.value
                val text = SosMessageBuilder.safe(LocaleHelper.localized(getApplication()), profile)
                val recipients = profile.sosRecipients
                if (recipients.isNotEmpty()) {
                    if (c.sms.hasPermission()) recipients.forEach { c.sms.send(it.phone, text) }
                    else _effects.send(SosEffect.OpenSmsApp(recipients.map { it.phone }, text))
                }
            }
            _state.value.eventId?.let { id -> c.sosHistory.update(id) { it.copy(resolvedAt = System.currentTimeMillis()) } }
            job?.cancel()
            _state.value = SosUiState()
            _effects.send(SosEffect.Finished)
        }
    }

    private fun persist() {
        val s = _state.value
        val id = s.eventId ?: return
        c.sosHistory.update(id) {
            it.copy(
                latitude = s.latitude,
                longitude = s.longitude,
                accuracyM = s.accuracyM,
                contactsTotal = s.contactsTotal,
                contactsNotified = s.contactsNotified,
                viaSmsApp = s.viaSmsApp,
                calledNumber = s.calledNumber,
            )
        }
    }

    private companion object {
        const val DUPLICATE_WINDOW_MS = 30_000L
        const val REPEAT_CONFIRM_WINDOW_MS = 5 * 60_000L
    }
}
