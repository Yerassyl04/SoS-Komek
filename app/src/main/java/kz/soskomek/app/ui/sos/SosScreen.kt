@file:OptIn(ExperimentalMaterial3Api::class)

package kz.soskomek.app.ui.sos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kz.soskomek.app.R
import kz.soskomek.app.container
import kz.soskomek.app.data.SosReason
import kz.soskomek.app.sos.Intents
import kz.soskomek.app.sos.SosViewModel
import kz.soskomek.app.ui.common.AppPermission
import kz.soskomek.app.ui.common.NoticeCard
import kz.soskomek.app.ui.common.WarningCard
import kz.soskomek.app.ui.hintRes
import kz.soskomek.app.ui.labelRes
import kz.soskomek.app.ui.serviceNameRes
import kz.soskomek.app.ui.theme.SosRed
import kz.soskomek.app.ui.theme.SosRedDark
import kotlin.math.ceil

private const val HOLD_MS = 3000

@Composable
fun SosScreen(
    vm: SosViewModel,
    onOpenStatus: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val state by vm.state.collectAsStateWithLifecycle()
    val profile by c.profile.profile.collectAsStateWithLifecycle()
    val settings by c.settings.state.collectAsStateWithLifecycle()

    var permissionsOk by remember { mutableStateOf(AppPermission.sosCriticalGranted(context)) }
    LifecycleResumeEffect(Unit) {
        permissionsOk = AppPermission.sosCriticalGranted(context)
        onPauseOrDispose { }
    }

    // Missing permissions are requested right before sending; SOS goes out whatever the answer.
    var pending by remember { mutableStateOf<Pair<SosReason, Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.let { (reason, share) -> vm.startSos(reason, share) }
        pending = null
        permissionsOk = AppPermission.sosCriticalGranted(context)
    }

    fun send(reason: SosReason, share: Boolean) {
        fun missing(p: String) = ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED
        val needed = buildList {
            if (missing(Manifest.permission.SEND_SMS)) add(Manifest.permission.SEND_SMS)
            if (share && missing(Manifest.permission.ACCESS_FINE_LOCATION) && missing(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (needed.isEmpty()) vm.startSos(reason, share)
        else {
            pending = reason to share
            launcher.launch(needed.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Outlined.History, stringResource(R.string.history_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.active) {
                NoticeCard(
                    text = stringResource(R.string.sos_active_banner),
                    icon = Icons.Filled.NotificationsActive,
                    container = SosRed,
                    content = Color.White,
                    actionLabel = stringResource(R.string.open),
                    onAction = onOpenStatus,
                )
            }
            if (profile.sosRecipients.isEmpty()) {
                WarningCard(stringResource(R.string.warn_no_contacts), stringResource(R.string.add), onOpenProfile)
            }
            if (!permissionsOk) {
                WarningCard(stringResource(R.string.warn_permissions), stringResource(R.string.configure), onOpenPermissions)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sos_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            SosHoldButton(enabled = !state.sending, onTriggered = vm::onHoldCompleted)
            Text(
                stringResource(R.string.sos_hold_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.quick_call_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.quick_call_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCall("102", Icons.Filled.LocalPolice, Modifier.weight(1f))
                QuickCall("103", Icons.Filled.LocalHospital, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCall("112", Icons.Filled.Call, Modifier.weight(1f))
                QuickCall("111", Icons.Filled.FamilyRestroom, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            NoticeCard(stringResource(R.string.disclaimer_short))
            Spacer(Modifier.height(12.dp))
        }
    }

    if (state.showReasonDialog) {
        ReasonDialog(
            defaultShareLocation = settings.locationConsent,
            onSelect = { reason, share ->
                vm.dismissDialogs()
                send(reason, share)
            },
            onCancel = vm::dismissDialogs,
        )
    }
    if (state.showRepeatWarning) {
        AlertDialog(
            onDismissRequest = vm::dismissDialogs,
            icon = { Icon(Icons.Filled.Warning, null) },
            title = { Text(stringResource(R.string.repeat_title)) },
            text = { Text(stringResource(R.string.repeat_text)) },
            confirmButton = { TextButton(onClick = vm::confirmRepeat) { Text(stringResource(R.string.repeat_confirm)) } },
            dismissButton = { TextButton(onClick = vm::dismissDialogs) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Big SOS button: must be held for 3 seconds, with a circular countdown. Releasing earlier cancels. */
@Composable
fun SosHoldButton(enabled: Boolean, onTriggered: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val trigger by rememberUpdatedState(onTriggered)
    var holding by remember { mutableStateOf(false) }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val a11yLabel = stringResource(R.string.sos_a11y)

    Box(
        modifier = Modifier
            .size(260.dp)
            .semantics {
                role = Role.Button
                contentDescription = a11yLabel
                // Screen-reader users double-tap instead of holding; the reason dialog still confirms.
                onClick { if (enabled) trigger(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    holding = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        holding = false
                        trigger()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    holding = false
                    if (job.isActive) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            drawCircle(color = trackColor, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            drawArc(
                color = SosRed,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            )
        }
        Box(
            Modifier
                .size(208.dp)
                .shadow(if (holding) 2.dp else 12.dp, CircleShape)
                .clip(CircleShape)
                .background(if (enabled) (if (holding) SosRedDark else SosRed) else Color.Gray),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SOS", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black)
                val label = when {
                    !enabled -> stringResource(R.string.sos_sending)
                    holding || progress.value > 0f -> {
                        val seconds = ceil((1f - progress.value) * HOLD_MS / 1000f).toInt().coerceAtLeast(1)
                        stringResource(R.string.sos_hold_countdown, seconds)
                    }
                    else -> stringResource(R.string.sos_hold_short)
                }
                Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun QuickCall(number: String, icon: ImageVector, modifier: Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { Intents.call(context, number, direct = false) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(number, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(serviceNameRes(number)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
fun ReasonDialog(defaultShareLocation: Boolean, onSelect: (SosReason, Boolean) -> Unit, onCancel: () -> Unit) {
    var shareLocation by remember { mutableStateOf(defaultShareLocation) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.reason_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.reason_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReasonButton(SosReason.HARASSMENT, Icons.Filled.PersonOff, Color(0xFFC2185B)) { onSelect(it, shareLocation) }
                ReasonButton(SosReason.VIOLENCE, Icons.Filled.PanTool, SosRed) { onSelect(it, shareLocation) }
                ReasonButton(SosReason.MEDICAL, Icons.Filled.LocalHospital, Color(0xFF1565C0)) { onSelect(it, shareLocation) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { shareLocation = !shareLocation }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = shareLocation, onCheckedChange = { shareLocation = it })
                    Text(stringResource(R.string.reason_share_location), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun ReasonButton(reason: SosReason, icon: ImageVector, color: Color, onClick: (SosReason) -> Unit) {
    Surface(
        onClick = { onClick(reason) },
        shape = RoundedCornerShape(18.dp),
        color = color,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(reason.labelRes), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(reason.hintRes), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}
