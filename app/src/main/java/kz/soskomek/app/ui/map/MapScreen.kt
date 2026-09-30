@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package kz.soskomek.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kz.soskomek.app.R
import kz.soskomek.app.data.ComplaintReason
import kz.soskomek.app.data.Incident
import kz.soskomek.app.data.IncidentCategory
import kz.soskomek.app.data.IncidentStatus
import kz.soskomek.app.data.LocationFuzzer
import kz.soskomek.app.ui.common.AppPermission
import kz.soskomek.app.ui.common.formatDateTime
import kz.soskomek.app.ui.labelRes
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(onReport: () -> Unit) {
    val context = LocalContext.current
    val vm: MapViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    var showFilters by rememberSaveable { mutableStateOf(true) }
    var complainAbout by remember { mutableStateOf<Incident?>(null) }

    val mapView = rememberMapView(context, vm.initialCenter, zoom = 12.5)
    val hasLocation = AppPermission.LOCATION.isGranted(context)

    LaunchedEffect(mapView) {
        mapView.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { vm.select(null); return false }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }))
        mapView.addMapListener(DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                vm.setMapCenter(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
                return false
            }
            override fun onZoom(event: ZoomEvent?): Boolean = false
        }, 400))
        if (hasLocation) {
            val overlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
            overlay.enableMyLocation()
            mapView.overlays.add(overlay)
            vm.locate()
        }
    }
    LaunchedEffect(Unit) {
        vm.centerRequests.collect { (lat, lon) -> mapView.controller.animateTo(GeoPoint(lat, lon), 12.5, 700L) }
    }
    LaunchedEffect(ui.incidents, ui.selected, dark) {
        renderIncidents(context, mapView, ui.incidents, ui.selected?.id, vm::select)
        mapView.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        mapView.invalidate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_title)) },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Filled.FilterList, stringResource(R.string.filters))
                    }
                },
            )
        },
        floatingActionButton = {
            if (ui.selected == null) {
                ExtendedFloatingActionButton(
                    onClick = onReport,
                    icon = { Icon(Icons.Filled.AddLocationAlt, null) },
                    text = { Text(stringResource(R.string.report_incident)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AnimatedVisibility(showFilters && ui.selected == null) {
                FilterPanel(
                    filter = ui.filter,
                    count = ui.incidents.size,
                    onToggleCategory = vm::toggleCategory,
                    onDistance = vm::setDistance,
                    onPeriod = vm::setPeriod,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ui.demoShown) {
                        AssistChip(
                            onClick = { Toast.makeText(context, R.string.demo_hint, Toast.LENGTH_LONG).show() },
                            label = { Text(stringResource(R.string.demo_badge)) },
                            leadingIcon = { Icon(Icons.Filled.Science, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                if (hasLocation) {
                    SmallFloatingActionButton(
                        onClick = { vm.locate() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    ) { Icon(Icons.Filled.MyLocation, stringResource(R.string.my_location)) }
                }
                ui.selected?.let { incident ->
                    IncidentCard(
                        incident = incident,
                        onClose = { vm.select(null) },
                        onComplain = { complainAbout = incident },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                }
            }
        }
    }

    complainAbout?.let { incident ->
        ComplaintDialog(
            onDismiss = { complainAbout = null },
            onSubmit = { reason ->
                vm.complain(incident.id, reason)
                complainAbout = null
                Toast.makeText(context, R.string.complaint_sent, Toast.LENGTH_LONG).show()
            },
        )
    }
}

@Composable
private fun FilterPanel(
    filter: MapFilter,
    count: Int,
    onToggleCategory: (IncidentCategory) -> Unit,
    onDistance: (Int?) -> Unit,
    onPeriod: (Period) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IncidentCategory.entries.forEach { category ->
                FilterChip(
                    selected = category in filter.categories,
                    onClick = { onToggleCategory(category) },
                    label = { Text(stringResource(category.labelRes)) },
                    leadingIcon = {
                        Box(Modifier.size(12.dp).background(Color(category.color), CircleShape))
                    },
                )
            }
        }
        val periods = Period.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            periods.forEachIndexed { i, period ->
                SegmentedButton(
                    selected = filter.period == period,
                    onClick = { onPeriod(period) },
                    shape = SegmentedButtonDefaults.itemShape(i, periods.size),
                ) {
                    Text(stringResource(when (period) {
                        Period.TODAY -> R.string.period_today
                        Period.WEEK -> R.string.period_week
                        Period.MONTH -> R.string.period_month
                    }))
                }
            }
        }
        val distances = listOf(1, 5, 20, null)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            distances.forEachIndexed { i, km ->
                SegmentedButton(
                    selected = filter.distanceKm == km,
                    onClick = { onDistance(km) },
                    shape = SegmentedButtonDefaults.itemShape(i, distances.size),
                ) {
                    Text(if (km == null) stringResource(R.string.distance_any) else stringResource(R.string.distance_km, km))
                }
            }
        }
        Text(
            stringResource(R.string.map_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IncidentCard(incident: Incident, onClose: () -> Unit, onComplain: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).background(Color(incident.category.color), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(incident.category.labelRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            Text(
                formatDateTime(incident.occurredAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (incident.isDemo) stringResource(R.string.demo_description) else incident.description,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.approx_place, LocationFuzzer.APPROX_RADIUS_M.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (incident.isOwn && incident.status == IncidentStatus.PENDING) {
                Text(stringResource(R.string.status_on_moderation), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            if (!incident.isOwn) {
                TextButton(onClick = onComplain) {
                    Icon(Icons.Filled.Flag, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.complain))
                }
            }
        }
    }
}

@Composable
private fun ComplaintDialog(onDismiss: () -> Unit, onSubmit: (ComplaintReason) -> Unit) {
    var reason by remember { mutableStateOf(ComplaintReason.FALSE_INFO) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Flag, null) },
        title = { Text(stringResource(R.string.complaint_title)) },
        text = {
            Column {
                Text(stringResource(R.string.complaint_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ComplaintReason.entries.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == r, onClick = { reason = r }, role = Role.RadioButton)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(r.labelRes))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(reason) }) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ osmdroid helpers

@Composable
fun rememberMapView(context: Context, center: Pair<Double, Double>, zoom: Double): MapView {
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            controller.setZoom(zoom)
            controller.setCenter(GeoPoint(center.first, center.second))
            // Let the map handle gestures inside scrollable screens
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    return mapView
}

private fun renderIncidents(
    context: Context,
    mapView: MapView,
    incidents: List<Incident>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    mapView.overlays.removeAll { it is Marker || it is Polygon }
    incidents.forEach { incident ->
        val color = incident.category.color.toInt()
        val center = GeoPoint(incident.latitude, incident.longitude)
        val selected = incident.id == selectedId
        // A circle instead of a pin: the location is approximate on purpose
        val area = Polygon(mapView).apply {
            setPoints(Polygon.pointsAsCircle(center, LocationFuzzer.APPROX_RADIUS_M))
            fillPaint.color = ColorUtils.setAlphaComponent(color, if (selected) 90 else 45)
            outlinePaint.color = ColorUtils.setAlphaComponent(color, 160)
            outlinePaint.strokeWidth = 3f
            infoWindow = null
            setOnClickListener { _, _, _ -> onSelect(incident.id); true }
        }
        val marker = Marker(mapView).apply {
            position = center
            icon = dotDrawable(context, color, if (selected) 26 else 18)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
            setOnMarkerClickListener { _, _ -> onSelect(incident.id); true }
        }
        mapView.overlays.add(area)
        mapView.overlays.add(marker)
    }
}

private fun dotDrawable(context: Context, color: Int, sizeDp: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint)
    return BitmapDrawable(context.resources, bitmap)
}
