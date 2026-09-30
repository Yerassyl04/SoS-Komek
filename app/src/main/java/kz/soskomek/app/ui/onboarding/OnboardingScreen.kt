@file:OptIn(ExperimentalMaterial3Api::class)

package kz.soskomek.app.ui.onboarding

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import kz.soskomek.app.R
import kz.soskomek.app.container
import kz.soskomek.app.ui.common.NoticeCard
import kz.soskomek.app.ui.common.PermissionList
import kz.soskomek.app.ui.profile.LanguageSelector
import kz.soskomek.app.ui.theme.SosRed

@Composable
fun OnboardingScreen() {
    val c = LocalContext.current.container
    var step by rememberSaveable { mutableIntStateOf(0) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var locationConsent by rememberSaveable { mutableStateOf(true) }
    var openDoc by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = step > 0) { step-- }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().fillMaxSize()) {
            LinearProgressIndicator(
                progress = { (step + 1) / 3f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    0 -> {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier.size(96.dp).background(SosRed, CircleShape).align(Alignment.CenterHorizontally),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.onb_welcome),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                        LanguageSelector()
                        NoticeCard(
                            stringResource(R.string.disclaimer_full),
                            icon = Icons.Filled.Warning,
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(stringResource(R.string.onb_features), style = MaterialTheme.typography.bodyMedium)
                    }
                    1 -> {
                        Text(stringResource(R.string.onb_privacy_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.onb_privacy_summary), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { openDoc = "privacy" }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(stringResource(R.string.privacy_policy), textAlign = TextAlign.Center)
                        }
                        OutlinedButton(onClick = { openDoc = "terms" }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(stringResource(R.string.terms), textAlign = TextAlign.Center)
                        }
                        CheckRow(accepted, stringResource(R.string.onb_accept)) { accepted = it }
                        CheckRow(locationConsent, stringResource(R.string.onb_location_consent)) { locationConsent = it }
                        Text(
                            stringResource(R.string.onb_location_consent_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.onb_permissions_intro), style = MaterialTheme.typography.bodyLarge)
                        PermissionList()
                        Text(
                            stringResource(R.string.onb_permissions_later),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(stringResource(R.string.back))
                    }
                }
                Button(
                    onClick = {
                        when (step) {
                            0 -> step = 1
                            1 -> {
                                c.settings.update { it.copy(termsAccepted = true, locationConsent = locationConsent) }
                                step = 2
                            }
                            else -> c.settings.update { it.copy(onboardingDone = true) }
                        }
                    },
                    enabled = step != 1 || accepted,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(if (step == 2) R.string.onb_finish else R.string.next)) }
            }
        }
    }

    openDoc?.let { doc ->
        Dialog(onDismissRequest = { openDoc = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            LegalScreen(doc = doc, onBack = { openDoc = null }, closeIcon = true)
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LegalScreen(doc: String, onBack: () -> Unit, closeIcon: Boolean = false) {
    val (title, body) = if (doc == "terms") R.string.terms to R.string.terms_text else R.string.privacy_policy to R.string.privacy_text
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(if (closeIcon) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            if (closeIcon) {
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.onb_permissions_intro), style = MaterialTheme.typography.bodyLarge)
            PermissionList()
        }
    }
}
