@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package kz.soskomek.app.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kz.soskomek.app.R
import kz.soskomek.app.container
import kz.soskomek.app.data.PhoneUtils
import kz.soskomek.app.data.PhoneVerifier
import kz.soskomek.app.data.Relation
import kz.soskomek.app.data.SosEvent
import kz.soskomek.app.data.TrustedContact
import kz.soskomek.app.data.UserProfile
import kz.soskomek.app.sos.Intents
import kz.soskomek.app.sos.LocaleHelper
import kz.soskomek.app.sos.SosMessageBuilder
import kz.soskomek.app.ui.common.NoticeCard
import kz.soskomek.app.ui.common.SectionTitle
import kz.soskomek.app.ui.common.formatDateTime
import kz.soskomek.app.ui.labelRes

// ------------------------------------------------------------------ Trusted contact editor

private class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

@Composable
fun ContactEditScreen(index: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val profile = remember { c.profile.profile.value }
    val existing = profile.contacts.getOrNull(index)

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var relation by rememberSaveable { mutableStateOf(existing?.relation ?: Relation.RELATIVE) }
    var receiveSos by rememberSaveable { mutableStateOf(existing?.receiveSos ?: true) }
    var shareLocation by rememberSaveable { mutableStateOf(existing?.shareLocation ?: true) }
    var invite by rememberSaveable { mutableStateOf(existing == null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Picking one number grants temporary access to that row only – no READ_CONTACTS permission needed
    val pickContact = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { name = it }
                    cursor.getString(1)?.let { phone = it }
                }
            }
        }
    }

    fun save() {
        val normalized = PhoneUtils.normalize(phone)
        error = when {
            name.isBlank() -> R.string.contact_err_name
            normalized == null -> R.string.phone_invalid
            normalized == profile.phone -> R.string.contact_err_self
            profile.contacts.any { it.phone == normalized && it.id != existing?.id } -> R.string.contact_err_duplicate
            else -> null
        }
        if (error != null || normalized == null) return
        val contact = TrustedContact(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            phone = normalized,
            relation = relation,
            receiveSos = receiveSos,
            shareLocation = receiveSos && shareLocation,
        )
        c.profile.update { p ->
            val list = if (existing != null) p.contacts.map { if (it.id == existing.id) contact else it }
            else (p.contacts + contact).take(UserProfile.MAX_CONTACTS)
            p.copy(contacts = list)
        }
        if (invite) {
            val text = SosMessageBuilder.invite(LocaleHelper.localized(context), c.profile.profile.value)
            if (c.sms.hasPermission()) scope.launch { c.sms.send(normalized, text) }
            else Intents.composeSms(context, listOf(normalized), text)
        }
        Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.contact_new else R.string.contact_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete))
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { runCatching { pickContact.launch(Unit) } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Contacts, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.contact_pick))
            }
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.contact_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.phone)) },
                placeholder = { Text("+7 7XX XXX XX XX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(stringResource(R.string.contact_relation))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Relation.entries.forEach { r ->
                    FilterChip(selected = relation == r, onClick = { relation = r }, label = { Text(stringResource(r.labelRes)) })
                }
            }
            SectionTitle(stringResource(R.string.contact_permissions))
            SwitchRow(stringResource(R.string.contact_receive_sos), stringResource(R.string.contact_receive_sos_hint), receiveSos) { receiveSos = it }
            if (receiveSos) {
                SwitchRow(stringResource(R.string.contact_share_location), stringResource(R.string.contact_share_location_hint), shareLocation) { shareLocation = it }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { invite = !invite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = invite, onCheckedChange = { invite = it })
                Text(stringResource(R.string.contact_invite), style = MaterialTheme.typography.bodyMedium)
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(56.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.contact_delete_title)) },
            text = { Text(existing.name) },
            confirmButton = {
                TextButton(onClick = {
                    c.profile.update { p -> p.copy(contacts = p.contacts.filterNot { it.id == existing.id }) }
                    confirmDelete = false
                    onBack()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ------------------------------------------------------------------ Phone verification

@Composable
fun VerifyPhoneScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val profile by c.profile.profile.collectAsStateWithLifecycle()
    val phone = profile.phone

    var code by rememberSaveable { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(c.verifier.hasActiveCode(phone)) }
    var sending by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(c.verifier.resendSecondsLeft()) }
    var sendCounter by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sendCounter) {
        secondsLeft = c.verifier.resendSecondsLeft()
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft = c.verifier.resendSecondsLeft()
        }
    }

    fun sendCode() {
        sending = true
        val generated = c.verifier.issue(phone)
        val text = LocaleHelper.localized(context).getString(R.string.verify_sms_text, generated)
        scope.launch {
            val ok = c.sms.send(phone, text)
            sending = false
            if (ok) {
                codeSent = true
                message = null
            } else {
                message = context.getString(R.string.verify_send_failed)
            }
            sendCounter++
        }
    }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sendCode() else message = context.getString(R.string.verify_need_sms)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.verify_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(PhoneUtils.format(phone), style = MaterialTheme.typography.headlineSmall)
            NoticeCard(stringResource(R.string.verify_info), icon = Icons.Filled.Sms)
            Button(
                onClick = {
                    if (c.sms.hasPermission()) sendCode() else smsPermission.launch(Manifest.permission.SEND_SMS)
                },
                enabled = !sending && secondsLeft == 0 && phone.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    when {
                        sending -> stringResource(R.string.sending)
                        secondsLeft > 0 -> stringResource(R.string.verify_resend_in, secondsLeft)
                        codeSent -> stringResource(R.string.verify_resend)
                        else -> stringResource(R.string.verify_send)
                    }
                )
            }
            if (codeSent) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> if (v.length <= 6) code = v.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.verify_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                )
                Button(
                    onClick = {
                        when (c.verifier.verify(phone, code)) {
                            PhoneVerifier.Result.OK -> {
                                c.profile.update { it.copy(phoneVerified = true) }
                                Toast.makeText(context, R.string.phone_verified, Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                            PhoneVerifier.Result.WRONG -> message = context.getString(R.string.verify_wrong, c.verifier.attemptsLeft)
                            PhoneVerifier.Result.EXPIRED -> message = context.getString(R.string.verify_expired)
                            PhoneVerifier.Result.LOCKED -> { message = context.getString(R.string.verify_locked); codeSent = false; code = "" }
                            PhoneVerifier.Result.NO_CODE -> { message = context.getString(R.string.verify_expired); codeSent = false }
                        }
                    },
                    enabled = code.length == 6,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.verify_confirm)) }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

// ------------------------------------------------------------------ SOS history

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val events by c.sosHistory.events.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<SosEvent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (events.isNotEmpty()) IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, stringResource(R.string.history_clear))
                    }
                },
            )
        },
    ) { padding ->
        if (events.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.History, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.history_empty), textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.history_encrypted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(events, key = { it.id }) { e ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(e.reason.labelRes), style = MaterialTheme.typography.titleMedium)
                            Text(formatDateTime(e.timestamp), style = MaterialTheme.typography.bodyMedium)
                            val loc = if (e.latitude != null && e.longitude != null) SosMessageBuilder.coords(e.latitude, e.longitude)
                            else stringResource(if (e.locationShared) R.string.history_loc_unavailable else R.string.history_loc_not_shared)
                            Text(stringResource(R.string.history_location, loc), style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (e.viaSmsApp) stringResource(R.string.history_via_sms_app)
                                else stringResource(R.string.step_contacts_count, e.contactsNotified, e.contactsTotal),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            e.calledNumber?.let { Text(stringResource(R.string.step_call_done, it), style = MaterialTheme.typography.bodySmall) }
                            if (e.resolvedAt != null) {
                                Text(stringResource(R.string.history_resolved, formatDateTime(e.resolvedAt)), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                stringResource(e.syncStatus.labelRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { toDelete = e }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_text)) },
            confirmButton = {
                TextButton(onClick = { c.sosHistory.clear(); confirmClear = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.history_delete_one)) },
            text = { Text(formatDateTime(e.timestamp)) },
            confirmButton = {
                TextButton(onClick = { c.sosHistory.delete(e.id); toDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
