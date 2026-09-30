@file:OptIn(ExperimentalMaterial3Api::class)

package kz.soskomek.app.ui.profile

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.soskomek.app.R
import kz.soskomek.app.container
import kz.soskomek.app.data.PhoneUtils
import kz.soskomek.app.data.ThemeMode
import kz.soskomek.app.data.UserProfile
import kz.soskomek.app.sos.LocaleHelper
import kz.soskomek.app.ui.common.SectionTitle
import kz.soskomek.app.ui.labelRes
import kz.soskomek.app.ui.theme.SafeGreen

val BLOOD_GROUPS = listOf("", "O(I) Rh+", "O(I) Rh−", "A(II) Rh+", "A(II) Rh−", "B(III) Rh+", "B(III) Rh−", "AB(IV) Rh+", "AB(IV) Rh−")

@Composable
fun ProfileScreen(
    onEditContact: (Int) -> Unit,
    onVerifyPhone: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLegal: (String) -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val profile by c.profile.profile.collectAsStateWithLifecycle()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val photoVersion by c.profile.photoVersion.collectAsStateWithLifecycle()

    var firstName by rememberSaveable(profile.firstName) { mutableStateOf(profile.firstName) }
    var lastName by rememberSaveable(profile.lastName) { mutableStateOf(profile.lastName) }
    var phone by rememberSaveable(profile.phone) { mutableStateOf(profile.phone) }
    var bloodGroup by rememberSaveable(profile.bloodGroup) { mutableStateOf(profile.bloodGroup) }
    var medical by rememberSaveable(profile.medicalNotes) { mutableStateOf(profile.medicalNotes) }
    var phoneError by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    val photo by produceState<Bitmap?>(null, photoVersion) {
        value = withContext(Dispatchers.IO) { c.profile.loadPhoto() }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { c.profile.savePhoto(uri) }
            if (!ok) Toast.makeText(context, R.string.photo_error, Toast.LENGTH_SHORT).show()
        }
    }

    val changed = firstName != profile.firstName || lastName != profile.lastName || phone != profile.phone ||
        bloodGroup != profile.bloodGroup || medical != profile.medicalNotes

    /** Returns false if the phone number is invalid. */
    fun save(): Boolean {
        val normalized = if (phone.isBlank()) "" else PhoneUtils.normalize(phone)
        if (normalized == null) {
            phoneError = true
            return false
        }
        phoneError = false
        c.profile.update {
            it.copy(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                phone = normalized,
                phoneVerified = it.phoneVerified && normalized == it.phone,
                bloodGroup = bloodGroup,
                medicalNotes = medical.trim(),
            )
        }
        phone = normalized
        return true
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_title)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Photo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = photo
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), stringResource(R.string.photo), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(profile.fullName.ifBlank { stringResource(R.string.profile_no_name) }, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Filled.AddAPhoto, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (profile.hasPhoto) R.string.photo_change else R.string.photo_add))
                    }
                    if (profile.hasPhoto) {
                        TextButton(onClick = { c.profile.deletePhoto() }) { Text(stringResource(R.string.photo_remove)) }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.profile_encrypted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle(stringResource(R.string.profile_personal))
            OutlinedTextField(firstName, { firstName = it }, label = { Text(stringResource(R.string.first_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(lastName, { lastName = it }, label = { Text(stringResource(R.string.last_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; phoneError = false },
                label = { Text(stringResource(R.string.phone)) },
                placeholder = { Text("+7 7XX XXX XX XX") },
                singleLine = true,
                isError = phoneError,
                supportingText = {
                    when {
                        phoneError -> Text(stringResource(R.string.phone_invalid))
                        profile.phone.isNotBlank() && profile.phoneVerified && phone == profile.phone ->
                            Text(stringResource(R.string.phone_verified), color = SafeGreen)
                        else -> Text(stringResource(R.string.phone_not_verified))
                    }
                },
                trailingIcon = {
                    if (profile.phoneVerified && phone == profile.phone && phone.isNotBlank()) Icon(Icons.Filled.Verified, null, tint = SafeGreen)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!(profile.phoneVerified && phone == profile.phone) && phone.isNotBlank()) {
                OutlinedButton(
                    onClick = { if (save()) onVerifyPhone() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.verify_phone)) }
            }

            SectionTitle(stringResource(R.string.profile_medical))
            BloodGroupField(bloodGroup) { bloodGroup = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = medical,
                onValueChange = { if (it.length <= 300) medical = it },
                label = { Text(stringResource(R.string.medical_notes)) },
                placeholder = { Text(stringResource(R.string.medical_notes_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (save()) Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show() },
                enabled = changed,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.save)) }

            SectionTitle(stringResource(R.string.contacts_title, profile.contacts.size, UserProfile.MAX_CONTACTS))
            Text(stringResource(R.string.contacts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            profile.contacts.forEachIndexed { index, contact ->
                Card(
                    onClick = { onEditContact(index) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(contact.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${PhoneUtils.format(contact.phone)} · ${stringResource(contact.relation.labelRes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                when {
                                    !contact.receiveSos -> stringResource(R.string.contact_sos_off)
                                    contact.shareLocation -> stringResource(R.string.contact_sos_and_location)
                                    else -> stringResource(R.string.contact_sos_only)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (contact.receiveSos) SafeGreen else MaterialTheme.colorScheme.error,
                            )
                        }
                        Icon(Icons.Filled.Edit, stringResource(R.string.edit))
                    }
                }
            }
            if (profile.contacts.size < UserProfile.MAX_CONTACTS) {
                OutlinedButton(onClick = { onEditContact(-1) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.contact_add))
                }
            }

            SectionTitle(stringResource(R.string.settings_title))
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            LanguageSelector()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val themes = ThemeMode.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                themes.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.theme == mode,
                        onClick = { c.settings.update { it.copy(theme = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                    ) {
                        Text(stringResource(when (mode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.setting_location_consent), stringResource(R.string.setting_location_consent_hint), settings.locationConsent) { v ->
                c.settings.update { it.copy(locationConsent = v) }
            }
            SwitchRow(stringResource(R.string.setting_medical), stringResource(R.string.setting_medical_hint), settings.includeMedical) { v ->
                c.settings.update { it.copy(includeMedical = v) }
            }
            SwitchRow(stringResource(R.string.setting_demo), stringResource(R.string.setting_demo_hint), settings.showDemo) { v ->
                c.settings.update { it.copy(showDemo = v) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavRow(Icons.Filled.History, stringResource(R.string.history_title), onOpenHistory)
            NavRow(Icons.Filled.Security, stringResource(R.string.permissions_title), onOpenPermissions)
            NavRow(Icons.Filled.Policy, stringResource(R.string.privacy_policy), { onOpenLegal("privacy") })
            NavRow(Icons.Filled.Gavel, stringResource(R.string.terms), { onOpenLegal("terms") })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.wipe_all), color = MaterialTheme.colorScheme.error)
            }
            Text(
                stringResource(R.string.version_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            icon = { Icon(Icons.Filled.DeleteForever, null) },
            title = { Text(stringResource(R.string.wipe_title)) },
            text = { Text(stringResource(R.string.wipe_text)) },
            confirmButton = {
                TextButton(onClick = { confirmWipe = false; c.wipeAll() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val current = LocaleHelper.currentTag()
    val options = listOf(LocaleHelper.RU to "Русский", LocaleHelper.KK to "Қазақша")
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = { if (current != tag) LocaleHelper.setLanguage(tag) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun BloodGroupField(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val notSpecified = stringResource(R.string.not_specified)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.ifBlank { notSpecified },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.blood_group)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BLOOD_GROUPS.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.ifBlank { notSpecified }) },
                    onClick = { onChange(group); expanded = false },
                )
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
