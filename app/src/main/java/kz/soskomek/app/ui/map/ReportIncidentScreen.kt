@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package kz.soskomek.app.ui.map

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kz.soskomek.app.R
import kz.soskomek.app.container
import kz.soskomek.app.data.IncidentCategory
import kz.soskomek.app.data.Moderation
import kz.soskomek.app.data.PiiScrubber
import kz.soskomek.app.data.SubmitResult
import kz.soskomek.app.sos.SyncWorker
import kz.soskomek.app.ui.common.AppPermission
import kz.soskomek.app.ui.common.NoticeCard
import kz.soskomek.app.ui.common.SectionTitle
import kz.soskomek.app.ui.common.formatDate
import kz.soskomek.app.ui.labelRes
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun ReportIncidentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()

    var category by rememberSaveable { mutableStateOf<IncidentCategory?>(null) }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lon by rememberSaveable { mutableStateOf<Double?>(null) }
    val nowZoned = remember { LocalDateTime.now() }
    // DatePicker works with UTC midnight
    var dateUtc by rememberSaveable {
        mutableLongStateOf(nowZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    }
    var hour by rememberSaveable { mutableIntStateOf(nowZoned.hour) }
    var minute by rememberSaveable { mutableIntStateOf(nowZoned.minute) }
    var description by rememberSaveable { mutableStateOf("") }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SubmitResult?>(null) }

    val preview = remember(description) {
        if (description.isBlank()) "" else PiiScrubber.scrub(description.trim()).text
    }
    val occurredAt = run {
        val date = Instant.ofEpochMilli(dateUtc).atZone(ZoneOffset.UTC).toLocalDate()
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val canSubmit = category != null && lat != null && description.trim().length >= Moderation.MIN_LENGTH && confirmed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            NoticeCard(stringResource(R.string.report_anonymous), icon = Icons.Filled.Shield)

            SectionTitle(stringResource(R.string.report_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IncidentCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(stringResource(cat.labelRes), style = MaterialTheme.typography.bodyLarge) },
                        leadingIcon = { Box(Modifier.size(14.dp).background(Color(cat.color), CircleShape)) },
                        modifier = Modifier.height(44.dp),
                    )
                }
            }

            SectionTitle(stringResource(R.string.report_place))
            Text(
                stringResource(R.string.report_place_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            PlacePicker(lat, lon) { la, lo -> lat = la; lon = lo }
            if (AppPermission.LOCATION.isGranted(context)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val loc = c.location.lastKnown() ?: c.location.currentLocation()
                            if (loc != null) { lat = loc.latitude; lon = loc.longitude }
                            else Toast.makeText(context, R.string.step_location_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.MyLocation, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_use_location))
                }
            }

            SectionTitle(stringResource(R.string.report_when))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Filled.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDate(occurredAt))
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Filled.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text("%02d:%02d".format(hour, minute))
                }
            }

            SectionTitle(stringResource(R.string.report_description))
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= Moderation.MAX_LENGTH) description = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                placeholder = { Text(stringResource(R.string.report_description_hint)) },
                supportingText = { Text("${description.length} / ${Moderation.MAX_LENGTH}") },
            )
            if (preview.isNotBlank() && preview != description.trim().replace(Regex("\\s+"), " ")) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.report_preview), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(preview, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { confirmed = !confirmed },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Text(stringResource(R.string.report_confirm_truth), style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    when {
                        occurredAt > now + 5 * 60_000 ->
                            Toast.makeText(context, R.string.report_future, Toast.LENGTH_LONG).show()
                        else -> {
                            val r = c.incidents.submit(category!!, lat!!, lon!!, occurredAt, description)
                            if (r is SubmitResult.Published || r is SubmitResult.SentToModeration) SyncWorker.enqueue(context)
                            result = r
                        }
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(56.dp),
            ) { Text(stringResource(R.string.report_submit)) }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateUtc,
            selectableDates = object : SelectableDates {
                val todayUtc = LocalDateTime.now().toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= todayUtc && utcTimeMillis >= todayUtc - 30L * 24 * 3600 * 1000
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateUtc = it }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
    result?.let { r ->
        val (title, text) = when (r) {
            is SubmitResult.Published -> stringResource(R.string.report_published) to
                (stringResource(R.string.report_published_text) +
                    if (r.personalDataRemoved) "\n\n" + stringResource(R.string.report_pii_removed) else "")
            SubmitResult.SentToModeration -> stringResource(R.string.report_moderation) to stringResource(R.string.report_moderation_text)
            is SubmitResult.Rejected -> stringResource(R.string.report_rejected) to stringResource(r.reason)
            is SubmitResult.RateLimited -> stringResource(R.string.report_rejected) to stringResource(R.string.report_rate_limited, r.minutes)
        }
        val success = r is SubmitResult.Published || r is SubmitResult.SentToModeration
        AlertDialog(
            onDismissRequest = { result = null; if (success) onBack() },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { result = null; if (success) onBack() }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
private fun PlacePicker(lat: Double?, lon: Double?, onPick: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val start = if (lat != null && lon != null) lat to lon else MapViewModel.DEFAULT_CENTER
    val mapView = rememberMapView(context, start, zoom = 14.0)
    val marker = remember { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); infoWindow = null } }

    LaunchedEffect(mapView) {
        mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { onPick(it.latitude, it.longitude) }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }))
        if (lat == null) {
            context.container.location.lastKnown()?.let {
                mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            }
        }
    }
    LaunchedEffect(lat, lon) {
        if (lat != null && lon != null) {
            marker.position = GeoPoint(lat, lon)
            if (marker !in mapView.overlays) mapView.overlays.add(marker)
            mapView.controller.animateTo(GeoPoint(lat, lon))
            mapView.invalidate()
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (lat == null) {
            Text(
                stringResource(R.string.report_tap_map),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
