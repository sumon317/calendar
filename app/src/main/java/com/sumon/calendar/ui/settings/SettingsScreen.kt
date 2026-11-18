package com.sumon.calendar.ui.settings

import android.Manifest
import android.accounts.Account
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumon.calendar.BuildConfig
import com.sumon.calendar.R
import com.sumon.calendar.data.DeviceCalendar
import com.sumon.calendar.ui.month.CalendarViewModel
import com.sumon.calendar.ui.theme.ColorPalettePreset
import com.sumon.calendar.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CalendarViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.READ_CALENDAR] == true &&
            results[Manifest.permission.WRITE_CALENDAR] == true
        viewModel.onPermissionResult(granted)
    }

    val googleLoginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refresh()
        viewModel.syncWithGoogle()
    }

    val syncSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refresh()
        viewModel.syncWithGoogle()
    }

    val appSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.checkPermissions()
        viewModel.refresh()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    val exportIcsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val icsContent = viewModel.exportIcs()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(icsContent.toByteArray(Charsets.UTF_8))
                    }
                    exportStatusMessage = "Calendar exported successfully!"
                } catch (e: Exception) {
                    exportStatusMessage = "Export failed: ${e.localizedMessage}"
                }
            }
        }
    }

    val importIcsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { isStream ->
                        isStream.bufferedReader().readText()
                    }.orEmpty()
                    val targetCalId = state.calendars.firstOrNull { it.isEnabled }?.id ?: 1L
                    val count = viewModel.importIcs(content, targetCalId)
                    exportStatusMessage = "Imported $count events into calendar."
                } catch (e: Exception) {
                    exportStatusMessage = "Import failed: ${e.localizedMessage}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section 1: Appearance & Themes
            AppearanceSection(
                selectedMode = themeMode,
                selectedPalette = colorPalette,
                onSelectMode = { viewModel.setThemeMode(it) },
                onSelectPalette = { viewModel.setColorPalette(it) },
            )

            // Section 2: Google Calendar & Sync
            SyncSection(
                googleAccounts = state.googleAccounts,
                isSyncing = state.isSyncing,
                onSyncNow = { viewModel.syncWithGoogle() },
                onSignInGoogle = { googleLoginLauncher.launch(viewModel.createGoogleLoginIntent()) },
                onOpenSyncSettings = { syncSettingsLauncher.launch(viewModel.createSyncSettingsIntent()) },
            )

            // Section 2: Device Calendars
            CalendarsSection(
                calendars = state.calendars,
                onToggleCalendar = { calId, isEnabled ->
                    viewModel.toggleCalendarEnabled(calId, isEnabled)
                },
                onEnableAll = { viewModel.enableAllCalendars() },
                onDisableAll = { viewModel.disableAllCalendars() },
            )

            // Section 3: Backup & Restore (iCalendar .ics)
            BackupSection(
                statusMessage = exportStatusMessage,
                onExportIcs = {
                    val defaultName = "calendar_backup_${java.time.LocalDate.now()}.ics"
                    exportIcsLauncher.launch(defaultName)
                },
                onImportIcs = {
                    importIcsLauncher.launch(arrayOf("text/calendar", "*/*"))
                },
            )

            // Section 4: Permissions
            PermissionsSection(
                hasPermission = state.hasPermission,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        ),
                    )
                },
                onOpenAppSettings = { appSettingsLauncher.launch(viewModel.createAppSettingsIntent()) },
            )

            // Section 4: About & Version
            AboutSection()

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppearanceSection(
    selectedMode: ThemeMode,
    selectedPalette: ColorPalettePreset,
    onSelectMode: (ThemeMode) -> Unit,
    onSelectPalette: (ColorPalettePreset) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_appearance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Theme Mode Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.theme_mode_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = selectedMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            border = if (!isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectMode(mode) },
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(mode.titleRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Color Palette Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.theme_palette_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                ColorPalettePreset.entries.forEach { preset ->
                    val isSelected = selectedPalette == preset
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = if (isSelected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPalette(preset) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Swatch Preview with light, dark, and accent dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(preset.previewLightBackground)
                                        .border(
                                            1.dp,
                                            Color.Black.copy(alpha = 0.15f),
                                            CircleShape,
                                        ),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(preset.previewDarkBackground)
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.2f),
                                            CircleShape,
                                        ),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(preset.previewAccent)
                                        .border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.surface,
                                            CircleShape,
                                        ),
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectPalette(preset) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncSection(
    googleAccounts: List<Account>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onSignInGoogle: () -> Unit,
    onOpenSyncSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "G",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.google_accounts_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_accounts_sync_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            if (googleAccounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.google_no_accounts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Connected Google Accounts (${googleAccounts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                googleAccounts.forEach { account ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34A853)), // Green dot
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(R.string.google_sync_enabled),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onSyncNow,
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.google_syncing))
                } else {
                    Text(stringResource(R.string.google_sync_now))
                }
            }

            OutlinedButton(
                onClick = onSignInGoogle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.google_add_account))
            }

            TextButton(
                onClick = onOpenSyncSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.google_sync_settings))
            }
        }
    }
}

@Composable
private fun CalendarsSection(
    calendars: List<DeviceCalendar>,
    onToggleCalendar: (Long, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
) {
    val enabledCount = remember(calendars) { calendars.count { it.isEnabled } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_device_calendars),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (calendars.isNotEmpty()) {
                        stringResource(R.string.calendars_enabled_count, enabledCount, calendars.size)
                    } else {
                        stringResource(R.string.settings_device_calendars_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (calendars.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onEnableAll,
                        enabled = enabledCount < calendars.size,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_enable_all))
                    }
                    OutlinedButton(
                        onClick = onDisableAll,
                        enabled = enabledCount > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_disable_all))
                    }
                }
            }

            HorizontalDivider()

            if (calendars.isEmpty()) {
                Text(
                    text = "No calendars found. Grant calendar permissions to detect device calendars.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                calendars.forEach { cal ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .toggleable(
                                value = cal.isEnabled,
                                role = Role.Switch,
                                onValueChange = { isChecked ->
                                    onToggleCalendar(cal.id, isChecked)
                                },
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (cal.color != 0) Color(cal.color)
                                        else MaterialTheme.colorScheme.primary,
                                    ),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = cal.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (cal.isEnabled) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        },
                                    )
                                    if (cal.isPrimary) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_primary_calendar),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                                val accountLabel = if (cal.accountType == "com.google") {
                                    "Google • ${cal.accountName}"
                                } else if (cal.accountName.isNotBlank()) {
                                    cal.accountName
                                } else {
                                    stringResource(R.string.settings_local_account)
                                }
                                Text(
                                    text = accountLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = cal.isEnabled,
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupSection(
    statusMessage: String?,
    onExportIcs: () -> Unit,
    onImportIcs: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = "Backup & Restore (iCalendar)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Export your events to an RFC 5545 .ics file or restore from external backup files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onExportIcs,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Export .ics")
                }
                OutlinedButton(
                    onClick = onImportIcs,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import .ics")
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsSection(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_permissions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (hasPermission) Color(0xFF34A853) else Color(0xFFEA4335)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasPermission) {
                            stringResource(R.string.settings_permission_granted)
                        } else {
                            stringResource(R.string.settings_permission_denied)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (!hasPermission) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.grant_permission))
                }
            }

            OutlinedButton(
                onClick = onOpenAppSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_open_app_settings))
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "License",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_license),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
