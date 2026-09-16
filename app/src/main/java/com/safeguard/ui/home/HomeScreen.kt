package com.safeguard.ui.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.safeguard.ui.theme.AccentCyan
import com.safeguard.ui.theme.PrimaryBlue
import com.safeguard.ui.theme.StatusActiveGreen
import com.safeguard.ui.theme.StatusActiveGreenContainer
import com.safeguard.ui.theme.StatusActiveGreenOnContainer
import com.safeguard.ui.theme.StatusDisabledRed
import com.safeguard.ui.theme.StatusDisabledRedContainer
import com.safeguard.ui.theme.StatusDisabledRedOnContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.safeguard.ui.components.PinAuthDialog
import com.safeguard.vpn.VpnErrorCode
import com.safeguard.vpn.VpnStatus
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBlockedPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val isPinProtected by viewModel.isPinProtected.collectAsStateWithLifecycle()

    var showPinDialogForDisable by remember { mutableStateOf(false) }
    var pinErrorForDisable by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val isActive = vpnState.status == VpnStatus.ACTIVE
    val isError = vpnState.status == VpnStatus.ERROR

    // Official Android VPN permission launcher using VpnService.prepare()
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            // User denied VPN consent
            viewModel.onVpnPermissionDenied()
        }
    }

    fun handleReconnect() {
        viewModel.clearError()
        if (!viewModel.isNetworkAvailable()) {
            viewModel.onNetworkUnavailable()
            return
        }
        if (viewModel.isAnotherVpnActive()) {
            viewModel.onAnotherVpnActive()
            return
        }
        val intent = viewModel.getVpnPermissionIntent()
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            viewModel.onVpnPermissionGranted()
        }
    }

    fun handleToggle() {
        viewModel.clearError()
        if (isActive) {
            if (isPinProtected) {
                pinErrorForDisable = null
                showPinDialogForDisable = true
            } else {
                viewModel.stopProtection()
            }
        } else {
            handleReconnect()
        }
    }

    if (showPinDialogForDisable) {
        PinAuthDialog(
            title = "Disable Protection",
            subtitle = "Enter your PIN to pause SafeGuard content filtering.",
            errorMessage = pinErrorForDisable,
            onDismiss = {
                showPinDialogForDisable = false
                pinErrorForDisable = null
            },
            onVerify = { enteredPin ->
                coroutineScope.launch {
                    val ok = viewModel.verifyPin(enteredPin)
                    if (ok) {
                        showPinDialogForDisable = false
                        pinErrorForDisable = null
                        viewModel.stopProtection()
                    } else {
                        pinErrorForDisable = "Incorrect PIN. Try again."
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_safeguard_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = stringResource(id = R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("dashboard_header")
                    )
                    Text(
                        text = stringResource(id = R.string.tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("dashboard_subtitle")
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Pulsing Animation for Active State
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isActive) 1.08f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )

            // Primary Interactive Status Shield Card
            val statusColor by animateColorAsState(
                targetValue = when (vpnState.status) {
                    VpnStatus.ACTIVE -> StatusActiveGreen
                    VpnStatus.ERROR -> Color(0xFFF57C00)
                    VpnStatus.INACTIVE -> StatusDisabledRed
                },
                label = "statusColor"
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Shield Ring Indicator
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        statusColor.copy(alpha = 0.2f),
                                        statusColor.copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                width = 3.dp,
                                color = statusColor.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { handleToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = statusColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(90.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (vpnState.status) {
                                        VpnStatus.ACTIVE -> Icons.Default.Shield
                                        VpnStatus.ERROR -> Icons.Default.Warning
                                        VpnStatus.INACTIVE -> Icons.Default.PowerSettingsNew
                                    },
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Main Status Label (REAL status: 🟢 PROTECTION ACTIVE or 🔴 PROTECTION DISABLED)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isActive) "🟢" else "🔴",
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isActive) "PROTECTION ACTIVE" else "PROTECTION DISABLED",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) StatusActiveGreen else StatusDisabledRed,
                            modifier = Modifier.testTag("main_status_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status Explanation Text
                    if (isActive) {
                        Text(
                            text = stringResource(id = R.string.status_active_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("status_description")
                        )
                    } else {
                        Text(
                            text = "Protection is not active.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusDisabledRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("status_description")
                        )
                        val failureDetail = when {
                            vpnState.errorCode == VpnErrorCode.PERMISSION_DENIED -> "VPN permission was revoked. SafeGuard requires VPN permission to filter content."
                            vpnState.errorCode == VpnErrorCode.ANOTHER_VPN_ACTIVE -> "Another VPN is active on your device. Android supports one VPN at a time."
                            vpnState.errorCode == VpnErrorCode.NETWORK_UNAVAILABLE -> "No network connection detected."
                            vpnState.errorCode == VpnErrorCode.SERVICE_STOPPED -> "SafeGuard VPN service stopped unexpectedly."
                            vpnState.errorMessage != null -> vpnState.errorMessage!!
                            else -> "Content filtering is paused. Tap below to re-engage."
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = failureDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Button (PAUSE PROTECTION or RECONNECT PROTECTION)
                    Button(
                        onClick = { handleToggle() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isActive -> MaterialTheme.colorScheme.surfaceVariant
                                isError -> Color(0xFFE65100)
                                else -> PrimaryBlue
                            },
                            contentColor = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(48.dp)
                            .testTag(if (isActive) "toggle_protection_button" else "reconnect_protection_button")
                    ) {
                        if (!isActive) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isActive) {
                                stringResource(id = R.string.disable_protection)
                            } else {
                                "RECONNECT PROTECTION"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Optional error dismissal button
                    if (isError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.testTag("dismiss_error_button")
                        ) {
                            Text(
                                text = stringResource(id = R.string.dismiss),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Statistics Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Protection Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3 Stat Cards: Blocked Today, Blocked This Week, Blocked This Month
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = stringResource(id = R.string.blocked_today),
                    value = stats.blockedToday.toString(),
                    modifier = Modifier.weight(1f),
                    testTag = "stat_today"
                )
                StatCard(
                    title = stringResource(id = R.string.blocked_this_week),
                    value = stats.blockedThisWeek.toString(),
                    modifier = Modifier.weight(1f),
                    testTag = "stat_week"
                )
                StatCard(
                    title = stringResource(id = R.string.blocked_this_month),
                    value = stats.blockedThisMonth.toString(),
                    modifier = Modifier.weight(1f),
                    testTag = "stat_month"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: VIEW STATISTICS & SETTINGS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToStatistics,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("view_statistics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.view_statistics),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onNavigateToSettings,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.settings),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Preview Link for Blocked Website Screen
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBlockedPreview() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Test Blocked Page Preview",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "View →",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = PrimaryBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}
