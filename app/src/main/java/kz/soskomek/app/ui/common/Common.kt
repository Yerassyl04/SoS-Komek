package kz.soskomek.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kz.soskomek.app.R
import kz.soskomek.app.sos.Intents
import kz.soskomek.app.sos.LocaleHelper
import kz.soskomek.app.ui.theme.SafeGreen
import java.text.SimpleDateFormat
import java.util.Date

enum class AppPermission(
    val permissions: Array<String>,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
) {
    LOCATION(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        R.string.perm_location_title, R.string.perm_location_desc, Icons.Filled.LocationOn,
    ),
    SMS(arrayOf(Manifest.permission.SEND_SMS), R.string.perm_sms_title, R.string.perm_sms_desc, Icons.Filled.Sms),
    CALLS(arrayOf(Manifest.permission.CALL_PHONE), R.string.perm_call_title, R.string.perm_call_desc, Icons.Filled.Phone),
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
        R.string.perm_notif_title, R.string.perm_notif_desc, Icons.Filled.NotificationsActive,
    );

    fun isGranted(context: Context): Boolean = permissions.isEmpty() ||
        permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    companion object {
        val applicable get() = entries.filter { it.permissions.isNotEmpty() }
        fun allGranted(context: Context) = applicable.all { it.isGranted(context) }
        fun sosCriticalGranted(context: Context) = LOCATION.isGranted(context) && SMS.isGranted(context)
    }
}

/** Every permission with a plain-language explanation of why it is needed. */
@Composable
fun PermissionList(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val requested = remember { mutableStateListOf<AppPermission>() }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val missing = remember(tick) { AppPermission.applicable.filterNot { it.isGranted(context) } }
        AppPermission.applicable.forEach { p ->
            val granted = remember(tick) { p.isGranted(context) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(p.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(p.title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(p.description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (granted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = SafeGreen, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.perm_granted), color = SafeGreen, style = MaterialTheme.typography.labelLarge)
                            }
                        } else if (p in requested) {
                            TextButton(onClick = { Intents.openAppSettings(context) }) {
                                Text(stringResource(R.string.perm_open_settings))
                            }
                        } else {
                            OutlinedButton(onClick = {
                                requested.add(p)
                                launcher.launch(p.permissions)
                            }) { Text(stringResource(R.string.perm_allow)) }
                        }
                    }
                }
            }
        }
        if (missing.isNotEmpty()) {
            Button(
                onClick = {
                    requested.addAll(missing)
                    launcher.launch(missing.flatMap { it.permissions.toList() }.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.perm_allow_all)) }
        }
    }
}

@Composable
fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = content) }
            }
        }
    }
}

@Composable
fun WarningCard(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) = NoticeCard(
    text = text,
    icon = Icons.Filled.Warning,
    container = MaterialTheme.colorScheme.errorContainer,
    content = MaterialTheme.colorScheme.onErrorContainer,
    actionLabel = actionLabel,
    onAction = onAction,
)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

fun formatDateTime(time: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", LocaleHelper.currentLocale()).format(Date(time))

fun formatDate(time: Long): String =
    SimpleDateFormat("d MMM yyyy", LocaleHelper.currentLocale()).format(Date(time))

fun formatTime(time: Long): String =
    SimpleDateFormat("HH:mm", LocaleHelper.currentLocale()).format(Date(time))
