package com.safeguard.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeguard.ui.components.PinAuthDialog
import com.safeguard.ui.components.PinSetupDialog
import com.safeguard.ui.theme.AccentCyan
import com.safeguard.ui.theme.PrimaryBlue
import com.safeguard.ui.theme.StatusActiveGreen
import com.safeguard.ui.theme.StatusDisabledRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBlocklist: () -> Unit = {},
    onNavigateToWhitelist: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToDatabaseUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Protection
    val isProtectionEnabled by viewModel.isProtectionEnabled.collectAsStateWithLifecycle()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsStateWithLifecycle()
    val isPinProtected by viewModel.isPinProtected.collectAsStateWithLifecycle()

    // Filtering
    val blockAdult by viewModel.blockAdult.collectAsStateWithLifecycle()
    val blockPornography by viewModel.blockPornography.collectAsStateWithLifecycle()
    val blockExplicit by viewModel.blockExplicit.collectAsStateWithLifecycle()
    val blockNsfw by viewModel.blockNsfw.collectAsStateWithLifecycle()
    val blockAdultStreaming by viewModel.blockAdultStreaming.collectAsStateWithLifecycle()

    // Privacy
    val saveStatistics by viewModel.saveStatistics.collectAsStateWithLifecycle()
    val storeBlockedDomainNames by viewModel.storeBlockedDomainNames.collectAsStateWithLifecycle()

    // Updates
    val databaseVersion by viewModel.databaseVersion.collectAsStateWithLifecycle()
    val lastUpdated by viewModel.lastUpdated.collectAsStateWithLifecycle()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinAuthDialogForDisable by remember { mutableStateOf(false) }
    var pinAuthError by remember { mutableStateOf<String?>(null) }
    var pendingGuardedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var guardedActionTitle by remember { mutableStateOf("Security Verification") }

    var showAlwaysOnVpnDialog by remember { mutableStateOf(false) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf<String?>(null) }

    fun executeGuarded(title: String, action: () -> Unit) {
        if (isPinProtected) {
            guardedActionTitle = title
            pendingGuardedAction = action
            pinAuthError = null
        } else {
            action()
        }
    }

    if (pendingGuardedAction != null) {
        PinAuthDialog(
            title = guardedActionTitle,
            subtitle = "Enter your PIN to change this setting.",
            errorMessage = pinAuthError,
            onDismiss = {
                pendingGuardedAction = null
                pinAuthError = null
            },
            onVerify = { enteredPin ->
                coroutineScope.launch {
                    val verified = viewModel.verifyPin(enteredPin)
                    if (verified) {
                        val action = pendingGuardedAction
                        pendingGuardedAction = null
                        pinAuthError = null
                        action?.invoke()
                    } else {
                        pinAuthError = "Incorrect PIN. Try again."
                    }
                }
            }
        )
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onDismiss = { showPinSetupDialog = false },
            onSavePin = { newPin ->
                coroutineScope.launch {
                    val success = viewModel.setPin(newPin)
                    if (success) {
                        showPinSetupDialog = false
                        Toast.makeText(context, "PIN protection enabled", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showPinAuthDialogForDisable) {
        PinAuthDialog(
            title = "Disable PIN Protection",
            subtitle = "Enter your current PIN to turn off PIN lock.",
            errorMessage = pinAuthError,
            onDismiss = {
                showPinAuthDialogForDisable = false
                pinAuthError = null
            },
            onVerify = { enteredPin ->
                coroutineScope.launch {
                    val disabled = viewModel.disablePin(enteredPin)
                    if (disabled) {
                        showPinAuthDialogForDisable = false
                        pinAuthError = null
                        Toast.makeText(context, "PIN protection removed", Toast.LENGTH_SHORT).show()
                    } else {
                        pinAuthError = "Incorrect PIN. Try again."
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // SECTION 1: PROTECTION
            // ==========================================
            item {
                Spacer(modifier = Modifier.height(2.dp))
                SectionHeader("PROTECTION")
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Protection Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Protection Status",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isProtectionEnabled) "Active • Filtering local DNS queries" else "Disabled • Traffic is unfiltered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isProtectionEnabled) StatusActiveGreen else StatusDisabledRed
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (isProtectionEnabled) StatusActiveGreen.copy(alpha = 0.15f) else StatusDisabledRed.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isProtectionEnabled) Icons.Default.Shield else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isProtectionEnabled) StatusActiveGreen else StatusDisabledRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Auto Start
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto Start",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Automatically restore filtering on device restart.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoStartOnBoot,
                                onCheckedChange = { isChecked ->
                                    executeGuarded("Modify Auto Start") {
                                        viewModel.setAutoStartOnBoot(isChecked)
                                    }
                                },
                                modifier = Modifier.testTag("auto_start_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // PIN Protection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PIN Protection",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isPinProtected) "Settings & pauses locked with PIN" else "No passcode required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isPinProtected,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        showPinSetupDialog = true
                                    } else {
                                        showPinAuthDialogForDisable = true
                                    }
                                },
                                modifier = Modifier.testTag("pin_protection_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Always-on VPN Help
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAlwaysOnVpnDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Always-on VPN Help",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Learn how to prevent bypass in Android system settings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 2: FILTERING
            // ==========================================
            item {
                SectionHeader("FILTERING")
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FilterToggleItem(
                            title = "Adult Content",
                            subtitle = "General adult material and sexually explicit hosts.",
                            checked = blockAdult,
                            tag = "filter_adult_switch",
                            onCheckedChange = { checked ->
                                executeGuarded("Modify Adult Filter") { viewModel.setBlockAdult(checked) }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        FilterToggleItem(
                            title = "Pornography",
                            subtitle = "Known pornography tube websites and video portals.",
                            checked = blockPornography,
                            tag = "filter_pornography_switch",
                            onCheckedChange = { checked ->
                                executeGuarded("Modify Pornography Filter") { viewModel.setBlockPornography(checked) }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        FilterToggleItem(
                            title = "Explicit Content",
                            subtitle = "Erotic media, sexual imagery, and extreme adult content.",
                            checked = blockExplicit,
                            tag = "filter_explicit_switch",
                            onCheckedChange = { checked ->
                                executeGuarded("Modify Explicit Filter") { viewModel.setBlockExplicit(checked) }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        FilterToggleItem(
                            title = "NSFW",
                            subtitle = "Not Safe For Work image boards and chat sites.",
                            checked = blockNsfw,
                            tag = "filter_nsfw_switch",
                            onCheckedChange = { checked ->
                                executeGuarded("Modify NSFW Filter") { viewModel.setBlockNsfw(checked) }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        FilterToggleItem(
                            title = "Adult Streaming",
                            subtitle = "Live adult webcams and streaming video services.",
                            checked = blockAdultStreaming,
                            tag = "filter_adult_streaming_switch",
                            onCheckedChange = { checked ->
                                executeGuarded("Modify Adult Streaming Filter") { viewModel.setBlockAdultStreaming(checked) }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Custom Blocklist
                        NavigationSettingItem(
                            title = "Custom Blocklist",
                            subtitle = "Manage user-defined custom blocked domains",
                            icon = Icons.Default.Block,
                            onClick = onNavigateToBlocklist,
                            tag = "nav_custom_blocklist"
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Whitelist
                        NavigationSettingItem(
                            title = "Whitelist",
                            subtitle = "Allowed domains that bypass filtering",
                            icon = Icons.Default.CheckCircle,
                            onClick = onNavigateToWhitelist,
                            tag = "nav_whitelist"
                        )
                    }
                }
            }

            // ==========================================
            // SECTION 3: PRIVACY
            // ==========================================
            item {
                SectionHeader("PRIVACY")
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Statistics
                        FilterToggleItem(
                            title = "Statistics",
                            subtitle = "Save numeric counts of blocked threats locally.",
                            checked = saveStatistics,
                            tag = "privacy_statistics_switch",
                            onCheckedChange = { checked ->
                                viewModel.setSaveStatistics(checked)
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Domain Logging (OFF by default)
                        FilterToggleItem(
                            title = "Domain Logging",
                            subtitle = "Store Blocked Domain Names (OFF by default). When off, domains are never written.",
                            checked = storeBlockedDomainNames,
                            tag = "privacy_domain_logging_switch",
                            onCheckedChange = { checked ->
                                executeGuarded("Modify Domain Logging") {
                                    viewModel.setStoreBlockedDomainNames(checked)
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Clear Data
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearDataDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Clear Data",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Reset statistics and wipe all local security logs.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Privacy Overview Link
                        NavigationSettingItem(
                            title = "Privacy Statement & Architecture",
                            subtitle = "Details on local filtering and zero data collection",
                            icon = Icons.Default.PrivacyTip,
                            onClick = onNavigateToPrivacy,
                            tag = "nav_privacy_details"
                        )
                    }
                }
            }

            // ==========================================
            // SECTION 4: UPDATES
            // ==========================================
            item {
                SectionHeader("UPDATES")
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Update Protection Database Action
                        NavigationSettingItem(
                            title = "Update Protection Database",
                            subtitle = "Check for latest threat definitions and rules",
                            icon = Icons.Default.Update,
                            onClick = onNavigateToDatabaseUpdate,
                            tag = "nav_database_update"
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Automatic Updates
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic Updates",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Daily background sync via Android WorkManager.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.setAutoUpdateEnabled(checked)
                                },
                                modifier = Modifier.testTag("automatic_updates_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Database Version
                        InfoRow(
                            label = "Database Version",
                            value = databaseVersion
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Last Updated
                        InfoRow(
                            label = "Last Updated",
                            value = lastUpdated
                        )
                    }
                }
            }

            // ==========================================
            // SECTION 5: ABOUT
            // ==========================================
            item {
                SectionHeader("ABOUT")
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(
                            label = "App Version",
                            value = "SafeGuard 1.7.0 (Production Clean)"
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        NavigationSettingItem(
                            title = "Privacy Policy",
                            subtitle = "Our strict no-logging & on-device filtering policy",
                            icon = Icons.Default.Shield,
                            onClick = {
                                showAboutDialog = "SafeGuard Privacy Policy:\n\n1. No Data Collection: SafeGuard never sends your browsing activity, domain lookups, or device telemetry to remote servers.\n2. Local Filtering: Filtering is performed on-device using Android VPN loopback.\n3. Security & Integrity: No passwords, financial records, or private messages are ever read."
                            },
                            tag = "about_privacy_policy"
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        NavigationSettingItem(
                            title = "Terms",
                            subtitle = "Terms of Service for safe device usage",
                            icon = Icons.Default.Info,
                            onClick = {
                                showAboutDialog = "SafeGuard Terms of Service:\n\nSafeGuard is designed to help users and families maintain digital wellbeing by blocking adult, pornographic, and inappropriate content. SafeGuard is provided free and open for personal protection."
                            },
                            tag = "about_terms"
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        NavigationSettingItem(
                            title = "Open Source Licenses",
                            subtitle = "Jetpack Compose, Room, DataStore, Coroutines",
                            icon = Icons.Default.Key,
                            onClick = {
                                showAboutDialog = "Open Source Licenses:\n\n• Android Jetpack & Compose (Apache 2.0)\n• Kotlin Coroutines & Serialization (Apache 2.0)\n• Room SQLite Persistence (Apache 2.0)\n• Material 3 Components (Apache 2.0)"
                            },
                            tag = "about_licenses"
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // Always-on VPN Help Dialog
        if (showAlwaysOnVpnDialog) {
            AlertDialog(
                onDismissRequest = { showAlwaysOnVpnDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = { Text("Always-on VPN Guidance") },
                text = {
                    Column {
                        Text(
                            text = "To prevent users or apps from bypassing SafeGuard:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "1. Open Android Settings > Network & Internet > VPN.\n2. Tap the gear icon next to SafeGuard.\n3. Enable 'Always-on VPN'.\n4. (Optional) Enable 'Block connections without VPN' to block bypass completely.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAlwaysOnVpnDialog = false
                            try {
                                context.startActivity(viewModel.getOpenVpnSettingsIntent())
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, "Open Settings > Network > VPN", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("OPEN VPN SETTINGS")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAlwaysOnVpnDialog = false }) {
                        Text("GOT IT")
                    }
                }
            )
        }

        // Clear Local Data Confirmation Dialog
        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text("Clear Local Data?") },
                text = { Text("This will reset all statistics counts, delete all event logs, and reset privacy preferences.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearLocalData()
                            showClearDataDialog = false
                            Toast.makeText(context, "Local data cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("CLEAR DATA")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        // About / Policy Informational Dialog
        if (showAboutDialog != null) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = null },
                title = { Text("Information") },
                text = { Text(showAboutDialog.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = null }) {
                        Text("CLOSE")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = AccentCyan,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FilterToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun NavigationSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
