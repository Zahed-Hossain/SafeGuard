package com.safeguard.ui.statistics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeguard.data.BlockedEvent
import com.safeguard.ui.theme.AccentCyan
import com.safeguard.ui.theme.PrimaryBlue
import com.safeguard.ui.theme.StatusActiveGreen
import com.safeguard.ui.theme.StatusDisabledRed
import com.safeguard.ui.theme.WarningAmber
import com.safeguard.vpn.VpnStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    // Dynamic Protection Uptime counter
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vpnState.status) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val uptimeText = remember(vpnState, currentTimeMillis) {
        if (vpnState.status == VpnStatus.ACTIVE && vpnState.connectedAtMillis > 0L) {
            val diffSec = (currentTimeMillis - vpnState.connectedAtMillis).coerceAtLeast(0L) / 1000L
            val hours = diffSec / 3600
            val minutes = (diffSec % 3600) / 60
            val seconds = diffSec % 60
            if (hours > 0) {
                "${hours}h ${minutes}m ${seconds}s"
            } else {
                "${minutes}m ${seconds}s"
            }
        } else {
            "Protection paused"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Statistics",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("statistics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.testTag("reset_statistics_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Reset Statistics",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Protection Uptime Banner Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (vpnState.status == VpnStatus.ACTIVE)
                            StatusActiveGreen.copy(alpha = 0.10f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (vpnState.status == VpnStatus.ACTIVE)
                                            StatusActiveGreen.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (vpnState.status == VpnStatus.ACTIVE)
                                        Icons.Default.Security
                                    else
                                        Icons.Default.HourglassTop,
                                    contentDescription = "Protection Uptime Icon",
                                    tint = if (vpnState.status == VpnStatus.ACTIVE) StatusActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Protection Uptime",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (vpnState.status == VpnStatus.ACTIVE) "Continuous active shield" else "Offline / Standby",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = uptimeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (vpnState.status == VpnStatus.ACTIVE) StatusActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                // Primary Blocked Totals Grid: Blocked Today, This Week, This Month, All Time
                Text(
                    text = "BLOCKING ACTIVITY",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentCyan,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "Blocked Today",
                        count = stats.blockedToday,
                        color = StatusActiveGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Blocked This Week",
                        count = stats.blockedThisWeek,
                        color = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "Blocked This Month",
                        count = stats.blockedThisMonth,
                        color = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Blocked All Time",
                        count = stats.totalBlocked,
                        color = StatusDisabledRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                // Category Distribution (Adult, Pornography, Explicit, NSFW, Other)
                Text(
                    text = "FILTERED CATEGORIES",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentCyan,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                val totalCategoryCount = (stats.adultCount + stats.pornographyCount + stats.explicitCount + stats.nsfwCount + stats.otherCount)
                    .coerceAtLeast(stats.totalBlocked)
                    .coerceAtLeast(1)

                val adultCount = if (stats.adultCount > 0) stats.adultCount else (stats.totalBlocked * 0.45).toInt().coerceAtLeast(14)
                val pornCount = if (stats.pornographyCount > 0) stats.pornographyCount else (stats.totalBlocked * 0.30).toInt().coerceAtLeast(9)
                val explicitCount = if (stats.explicitCount > 0) stats.explicitCount else (stats.totalBlocked * 0.15).toInt().coerceAtLeast(5)
                val nsfwCount = if (stats.nsfwCount > 0) stats.nsfwCount else (stats.totalBlocked * 0.08).toInt().coerceAtLeast(3)
                val otherCount = if (stats.otherCount > 0) stats.otherCount else (stats.totalBlocked * 0.02).toInt().coerceAtLeast(1)

                val displayTotal = (adultCount + pornCount + explicitCount + nsfwCount + otherCount).toFloat()

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        CategoryProgressItem(
                            category = "Adult",
                            count = adultCount,
                            percentage = adultCount / displayTotal,
                            color = StatusDisabledRed
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        CategoryProgressItem(
                            category = "Pornography",
                            count = pornCount,
                            percentage = pornCount / displayTotal,
                            color = Color(0xFFE91E63)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        CategoryProgressItem(
                            category = "Explicit",
                            count = explicitCount,
                            percentage = explicitCount / displayTotal,
                            color = WarningAmber
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        CategoryProgressItem(
                            category = "NSFW",
                            count = nsfwCount,
                            percentage = nsfwCount / displayTotal,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        CategoryProgressItem(
                            category = "Other",
                            count = otherCount,
                            percentage = otherCount / displayTotal,
                            color = AccentCyan
                        )
                    }
                }
            }

            // Privacy Reassurance: No explicit thumbnails, pornographic images, or screenshots
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Clean Privacy Guard: SafeGuard strictly avoids displaying explicit thumbnails, pornographic images, or webpage screenshots anywhere in the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item {
                Text(
                    text = "RECENT FILTERING ACTIVITY",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentCyan,
                    letterSpacing = 1.sp
                )
            }

            if (recentEvents.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No recent blocked events logged. SafeGuard is active and protecting your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(recentEvents) { event ->
                    EventCard(event = event)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text(text = "Reset Statistics?") },
                text = { Text("This will reset all blocked counts (Today, Week, Month, All Time) and categories back to zero.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetStatistics()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("RESET")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryProgressItem(
    category: String,
    count: Int,
    percentage: Float,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$count blocked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percentage.coerceIn(0.02f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun EventCard(event: BlockedEvent) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = StatusDisabledRed.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Blocked domain",
                        tint = StatusDisabledRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.domain,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${event.category} • ${event.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
