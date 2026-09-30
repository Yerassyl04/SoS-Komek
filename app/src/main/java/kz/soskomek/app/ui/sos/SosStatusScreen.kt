@file:OptIn(ExperimentalMaterial3Api::class)

package kz.soskomek.app.ui.sos

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.soskomek.app.R
import kz.soskomek.app.sos.Intents
import kz.soskomek.app.sos.SosMessageBuilder
import kz.soskomek.app.sos.SosViewModel
import kz.soskomek.app.sos.StepState
import kz.soskomek.app.ui.common.NoticeCard
import kz.soskomek.app.ui.common.formatTime
import kz.soskomek.app.ui.labelRes
import kz.soskomek.app.ui.serviceNameRes
import kz.soskomek.app.ui.theme.SafeGreen
import kz.soskomek.app.ui.theme.SosRed
import kotlin.math.roundToInt

@Composable
fun SosStatusScreen(vm: SosViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    var showSafeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.status_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val reason = s.reason
        if (!s.active || reason == null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.VerifiedUser, null, tint = SafeGreen, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.status_none), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SosRed, contentColor = Color.White),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(if (s.sending) R.string.status_sending else R.string.status_sent),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            stringResource(reason.labelRes) + " · " + formatTime(s.startedAt),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // 1. Location
            val coords = if (s.latitude != null && s.longitude != null) {
                SosMessageBuilder.coords(s.latitude!!, s.longitude!!) +
                    (s.accuracyM?.let { " (±${it.roundToInt()} м)" } ?: "")
            } else null
            StepRow(
                state = s.locationStep,
                title = stringResource(
                    when (s.locationStep) {
                        StepState.DONE -> R.string.step_location_done
                        StepState.FAILED -> R.string.step_location_failed
                        StepState.SKIPPED -> R.string.step_location_skipped
                        else -> R.string.step_location_progress
                    }
                ),
                subtitle = when (s.locationStep) {
                    StepState.DONE -> coords
                    StepState.FAILED -> stringResource(R.string.step_location_failed_hint)
                    StepState.SKIPPED -> stringResource(R.string.step_location_skipped_hint)
                    else -> null
                },
            )

            // 2. Trusted contacts
            StepRow(
                state = s.contactsStep,
                title = stringResource(
                    when (s.contactsStep) {
                        StepState.DONE -> R.string.step_contacts_done
                        StepState.FAILED -> R.string.step_contacts_failed
                        StepState.IN_PROGRESS -> R.string.step_contacts_progress
                        else -> R.string.step_contacts_pending
                    }
                ),
                subtitle = when {
                    s.noContacts -> stringResource(R.string.step_contacts_none)
                    s.viaSmsApp -> stringResource(R.string.step_contacts_sms_app)
                    s.contactsStep == StepState.DONE || s.contactsStep == StepState.FAILED -> buildString {
                        append(stringResource(R.string.step_contacts_count, s.contactsNotified, s.contactsTotal))
                        if (s.failedNames.isNotEmpty()) {
                            append("\n")
                            append(stringResource(R.string.step_contacts_failed_names, s.failedNames.joinToString(", ")))
                        }
                    }
                    else -> null
                },
            )
            if (!s.sending && (s.failedContactIds.isNotEmpty() || s.contactsStep == StepState.FAILED)) {
                OutlinedButton(onClick = vm::retryContacts, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.retry_send))
                }
            }

            // 3. Emergency call – always confirmed by the user
            StepRow(
                state = if (s.callStep == StepState.DONE) StepState.DONE else StepState.PENDING,
                title = stringResource(R.string.step_call_title),
                subtitle = if (s.calledNumber != null) stringResource(R.string.step_call_done, s.calledNumber!!)
                else stringResource(R.string.step_call_pending, reason.emergencyNumber),
            )

            if (s.savedOffline) {
                NoticeCard(stringResource(R.string.status_offline), icon = Icons.Filled.CloudOff)
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.call(reason.emergencyNumber) },
                colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
            ) {
                Icon(Icons.Filled.Call, null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.call_service, reason.emergencyNumber, stringResource(serviceNameRes(reason.emergencyNumber))),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("102", "103", "112").filter { it != reason.emergencyNumber }.forEach { number ->
                    OutlinedButton(
                        onClick = { vm.call(number) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Text("$number · ${stringResource(serviceNameRes(number))}", textAlign = TextAlign.Center)
                    }
                }
            }
            if (s.latitude != null && s.longitude != null) {
                OutlinedButton(
                    onClick = { Intents.openMap(context, s.latitude!!, s.longitude!!) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Map, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.open_on_map))
                }
            }
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(
                onClick = { showSafeDialog = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = SafeGreen.copy(alpha = 0.15f), contentColor = SafeGreen),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Filled.VerifiedUser, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.im_safe), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSafeDialog) {
        AlertDialog(
            onDismissRequest = { showSafeDialog = false },
            icon = { Icon(Icons.Filled.VerifiedUser, null, tint = SafeGreen) },
            title = { Text(stringResource(R.string.safe_title)) },
            text = { Text(stringResource(R.string.safe_text)) },
            confirmButton = {
                TextButton(onClick = { showSafeDialog = false; vm.markSafe(notifyContacts = true) }) {
                    Text(stringResource(R.string.safe_notify))
                }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showSafeDialog = false; vm.markSafe(notifyContacts = false) }) {
                        Text(stringResource(R.string.safe_silent))
                    }
                    TextButton(onClick = { showSafeDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun StepRow(state: StepState, title: String, subtitle: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                StepState.IN_PROGRESS -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                StepState.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = SafeGreen, modifier = Modifier.size(28.dp))
                StepState.FAILED -> Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                StepState.SKIPPED -> Icon(Icons.Filled.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
                StepState.PENDING -> Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
