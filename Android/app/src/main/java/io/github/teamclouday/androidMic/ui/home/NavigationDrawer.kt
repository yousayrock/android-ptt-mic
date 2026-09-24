package io.github.teamclouday.androidMic.ui.home

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import io.github.teamclouday.androidMic.Mode
import io.github.teamclouday.androidMic.R
import io.github.teamclouday.androidMic.ui.MainViewModel
import io.github.teamclouday.androidMic.ui.home.dialog.DialogProcessingMode
import io.github.teamclouday.androidMic.ui.home.dialog.DialogIpPort
import io.github.teamclouday.androidMic.ui.home.dialog.DialogMode
import io.github.teamclouday.androidMic.ui.home.dialog.DialogTheme
import kotlinx.coroutines.launch


@Composable
fun DrawerBody(vm: MainViewModel) {

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        // setting title
        Box(
            modifier = Modifier
                .padding(vertical = 64.dp)
                .padding(start = 25.dp)
        ) {
            Text(
                text = stringResource(id = R.string.drawerHeader),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Connection
        SettingsItemsSubtitle(R.string.drawer_subtitle_connection)

        val dialogModeExpanded = rememberSaveable {
            mutableStateOf(false)
        }

        val mode = vm.prefs.mode.getAsState()

        DialogMode(vm = vm, expanded = dialogModeExpanded)
        SettingsItem(
            title = stringResource(id = R.string.drawerMode),
            subTitle = mode.value.toString(),
            contentDescription = "set mode",
            icon = Icons.Rounded.Settings,
            onClick = { dialogModeExpanded.value = true },
        )

        if (mode.value != Mode.USB) {
            val dialogIpPortExpanded = rememberSaveable {
                mutableStateOf(false)
            }
            DialogIpPort(
                vm = vm,
                expanded = dialogIpPortExpanded,
                portOnly = mode.value == Mode.ADB
            )
            SettingsItem(
                title = stringResource(if (mode.value == Mode.ADB) R.string.dialog_port else R.string.drawerIpPort),
                subTitle = (if (mode.value != Mode.ADB) (vm.prefs.ip.getAsState().value + ":") else "") + vm.prefs.port.getAsState().value,
                contentDescription = "set ip and port",
                icon = Icons.Rounded.Wifi,
                onClick = { dialogIpPortExpanded.value = true },
            )
        }


        // Audio
        SettingsItemsSubtitle(R.string.drawer_subtitle_audio)

        SettingsItem(
            title = stringResource(id = R.string.ptt_audio_profile),
            subTitle = stringResource(id = R.string.ptt_audio_profile_detail),
            contentDescription = "fixed V1 PTT audio format",
        )

        val dialogProcessingModeExpanded = rememberSaveable { mutableStateOf(false) }
        DialogProcessingMode(vm = vm, expanded = dialogProcessingModeExpanded)
        SettingsItem(
            title = stringResource(id = R.string.processing_mode),
            subTitle = vm.prefs.processingMode.getAsState().value.name,
            contentDescription = "set audio processing mode",
            onClick = { dialogProcessingModeExpanded.value = true },
        )

        val releaseCueEnabled = vm.prefs.releaseCueEnabled.getAsState().value
        SettingsItem(
            title = "PTT release cue",
            subTitle = if (releaseCueEnabled) "Enabled" else "Disabled",
            contentDescription = "toggle the short two-tone PTT release cue",
            onClick = { vm.setReleaseCueEnabled(!releaseCueEnabled) },
        )

        // Other
        SettingsItemsSubtitle(R.string.drawer_subtitle_other)

        val dialogThemesExpanded = rememberSaveable {
            mutableStateOf(false)
        }
        DialogTheme(vm = vm, expanded = dialogThemesExpanded)
        SettingsItem(
            title = stringResource(id = R.string.drawerTheme),
            subTitle = vm.prefs.theme.getAsState().value.toString(),
            contentDescription = "set theme",
            icon = Icons.Rounded.DarkMode,
            onClick = { dialogThemesExpanded.value = true },
        )

        val clipboard = LocalClipboard.current

        val appVersion = remember {
            vm.uiHelper.getAppVersion()
        }
        SettingsItem(
            title = stringResource(id = R.string.drawerVersion),
            subTitle = appVersion,
            contentDescription = "app version",
            icon = Icons.Rounded.Verified,
            onClick = {
                vm.viewModelScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("version", appVersion))
                    )
                }
            }
        )
    }
}


@Composable
private fun SettingsItemsSubtitle(
    subtitle: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 25.dp, bottom = 10.dp)
    ) {
        Text(
            text = stringResource(id = subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground)
}


@Composable
private fun SettingsItem(
    title: String,
    subTitle: String,
    contentDescription: String,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = subTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground)
}
