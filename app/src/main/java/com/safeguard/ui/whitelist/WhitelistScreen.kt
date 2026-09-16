package com.safeguard.ui.whitelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.safeguard.ui.components.PinAuthDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeguard.blocklist.parser.BlocklistJsonParser
import com.safeguard.data.db.WhitelistDomain
import com.safeguard.ui.theme.AccentCyan
import com.safeguard.ui.theme.PrimaryBlue
import com.safeguard.ui.theme.StatusActiveGreen
import com.safeguard.ui.theme.StatusWarningRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(
    viewModel: WhitelistViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedWhitelist by viewModel.displayedWhitelist.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalWhitelistCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val isPinProtected by viewModel.isPinProtected.collectAsStateWithLifecycle()
    var pendingPinAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinAuthError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var domainToEdit by remember { mutableStateOf<WhitelistDomain?>(null) }
    var domainToDelete by remember { mutableStateOf<WhitelistDomain?>(null) }

    fun guarded(action: () -> Unit) {
        if (isPinProtected) {
            pendingPinAction = action
            pinAuthError = null
        } else {
            action()
        }
    }

    if (pendingPinAction != null) {
        PinAuthDialog(
            title = "PIN Required",
            subtitle = "Enter your security PIN to modify the whitelist.",
            errorMessage = pinAuthError,
            onDismiss = {
                pendingPinAction = null
                pinAuthError = null
            },
            onVerify = { enteredPin ->
                coroutineScope.launch {
                    val verified = viewModel.verifyPin(enteredPin)
                    if (verified) {
                        val act = pendingPinAction
                        pendingPinAction = null
                        pinAuthError = null
                        act?.invoke()
                    } else {
                        pinAuthError = "Incorrect PIN. Try again."
                    }
                }
            }
        )
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trusted Whitelist",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "$totalCount Allowed Domains",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusActiveGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { guarded { showAddDialog = true } },
                containerColor = StatusActiveGreen,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("add_whitelist_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Trusted Domain")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("whitelist_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Whitelist Override Guarantee Banner
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = StatusActiveGreen.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = StatusActiveGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Whitelist Override Guarantee",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusActiveGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Domains listed here have highest priority and will NEVER be blocked by adult filters or DNS sinkholes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search whitelist...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_whitelist_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (displayedWhitelist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No domains match '$searchQuery'" else "No whitelisted domains yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = displayedWhitelist,
                        key = { it.id }
                    ) { item ->
                        WhitelistCard(
                            item = item,
                            onEdit = { guarded { domainToEdit = item } },
                            onDelete = { guarded { domainToDelete = item } }
                        )
                    }
                }
            }
        }
    }

    // Add Whitelist Dialog
    if (showAddDialog) {
        WhitelistInputDialog(
            title = "Add Trusted Domain",
            confirmLabel = "Add to Whitelist",
            onDismiss = { showAddDialog = false },
            onConfirm = { rawInput ->
                viewModel.addWhitelistDomain(rawInput) {
                    showAddDialog = false
                }
            }
        )
    }

    // Edit Whitelist Dialog
    domainToEdit?.let { domain ->
        WhitelistInputDialog(
            title = "Edit Trusted Domain",
            confirmLabel = "Save Changes",
            initialDomain = domain.domain,
            onDismiss = { domainToEdit = null },
            onConfirm = { rawInput ->
                viewModel.editWhitelistDomain(domain.id, rawInput) {
                    domainToEdit = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    domainToDelete?.let { domain ->
        AlertDialog(
            onDismissRequest = { domainToDelete = null },
            title = { Text("Remove from Whitelist") },
            text = { Text("Are you sure you want to remove '${domain.domain}' from the whitelist? Standard blocklist rules will re-apply.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWhitelistDomain(domain)
                        domainToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusWarningRed)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { domainToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun WhitelistCard(
    item: WhitelistDomain,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(item.createdAt) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(item.createdAt))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StatusActiveGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.domain,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Added $dateStr • Allowed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = StatusWarningRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun WhitelistInputDialog(
    title: String,
    confirmLabel: String,
    initialDomain: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var rawInput by remember { mutableStateOf(initialDomain) }
    val normalized = BlocklistJsonParser.normalizeDomainInput(rawInput)
    val isValid = BlocklistJsonParser.isValidDomainFormat(normalized)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Trusted Domain or URL") },
                    placeholder = { Text("e.g. https://www.khanacademy.org/math") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whitelist_input_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Normalization preview
                if (rawInput.isNotBlank()) {
                    Text(
                        text = "Stored as: $normalized",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isValid) StatusActiveGreen else StatusWarningRed,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isValid) {
                        Text(
                            text = "Invalid domain format",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusWarningRed,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rawInput) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                modifier = Modifier.testTag("confirm_whitelist_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
