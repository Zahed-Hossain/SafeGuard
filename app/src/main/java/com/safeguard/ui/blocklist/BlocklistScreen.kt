package com.safeguard.ui.blocklist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import com.safeguard.ui.components.PinAuthDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeguard.blocklist.BlockedCategory
import com.safeguard.blocklist.parser.BlocklistJsonParser
import com.safeguard.data.db.BlockedDomain
import com.safeguard.ui.theme.AccentCyan
import com.safeguard.ui.theme.PrimaryBlue
import com.safeguard.ui.theme.StatusActiveGreen
import com.safeguard.ui.theme.StatusWarningRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(
    viewModel: BlocklistViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedDomains by viewModel.displayedDomains.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalBlockedCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedSourceFilter.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    val isPinProtected by viewModel.isPinProtected.collectAsStateWithLifecycle()
    var pendingPinAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinAuthError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var domainToEdit by remember { mutableStateOf<BlockedDomain?>(null) }
    var domainToDelete by remember { mutableStateOf<BlockedDomain?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportJsonText by remember { mutableStateOf<String?>(null) }

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
            subtitle = "Enter your security PIN to modify the blocklist.",
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
                            text = "Blocklist Database",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "$totalCount Domains Blocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentCyan
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
                actions = {
                    IconButton(
                        onClick = { guarded { showImportDialog = true } },
                        modifier = Modifier.testTag("import_domains_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import Domains",
                            tint = AccentCyan
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.exportDomains { json ->
                                exportJsonText = json
                            }
                        },
                        modifier = Modifier.testTag("export_domains_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Domains",
                            tint = PrimaryBlue
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
                containerColor = PrimaryBlue,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("add_domain_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Blocked Domain")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("blocklist_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search blocked domains...") },
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
                    .testTag("search_domains_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    "ALL" to "All Domains",
                    "CUSTOM" to "Custom Rules",
                    "BUILTIN" to "Built-in",
                    "REMOTE" to "Remote Feed"
                )
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { viewModel.setSourceFilter(key) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (displayedDomains.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No domains match '$searchQuery'" else "No domains found in this category",
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
                        items = displayedDomains,
                        key = { it.id }
                    ) { item ->
                        BlockedDomainCard(
                            item = item,
                            onEdit = { guarded { domainToEdit = item } },
                            onDelete = { guarded { domainToDelete = item } }
                        )
                    }
                }
            }
        }
    }

    // Add Domain Dialog
    if (showAddDialog) {
        DomainInputDialog(
            title = "Add Custom Blocked Domain",
            confirmLabel = "Add Domain",
            onDismiss = { showAddDialog = false },
            onConfirm = { rawInput, category ->
                viewModel.addCustomDomain(rawInput, category) {
                    showAddDialog = false
                }
            }
        )
    }

    // Edit Domain Dialog
    domainToEdit?.let { domain ->
        DomainInputDialog(
            title = "Edit Blocked Domain",
            confirmLabel = "Save Changes",
            initialDomain = domain.domain,
            initialCategory = try { BlockedCategory.valueOf(domain.category) } catch (e: Exception) { BlockedCategory.ADULT },
            onDismiss = { domainToEdit = null },
            onConfirm = { rawInput, category ->
                viewModel.editCustomDomain(domain.id, rawInput, category) {
                    domainToEdit = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    domainToDelete?.let { domain ->
        AlertDialog(
            onDismissRequest = { domainToDelete = null },
            title = { Text("Delete Blocked Domain") },
            text = { Text("Are you sure you want to remove '${domain.domain}' from your blocklist?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDomain(domain)
                        domainToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusWarningRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { domainToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        ImportDomainsDialog(
            onDismiss = { showImportDialog = false },
            onImport = { rawText, category ->
                viewModel.importDomains(rawText, category) {
                    showImportDialog = false
                }
            }
        )
    }

    // Export Dialog
    exportJsonText?.let { json ->
        AlertDialog(
            onDismissRequest = { exportJsonText = null },
            title = { Text("Export Blocklist (JSON)") },
            text = {
                Column {
                    Text(
                        text = "Standard SafeGuard blocklist JSON format:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(4.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            item {
                                Text(
                                    text = json,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(json))
                        exportJsonText = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportJsonText = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun BlockedDomainCard(
    item: BlockedDomain,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = StatusWarningRed,
                modifier = Modifier.size(20.dp)
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
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(text = item.category, color = PrimaryBlue)
                    Badge(
                        text = item.source,
                        color = when (item.source) {
                            "CUSTOM" -> AccentCyan
                            "REMOTE" -> StatusActiveGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (item.source == "CUSTOM") {
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
}

@Composable
fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DomainInputDialog(
    title: String,
    confirmLabel: String,
    initialDomain: String = "",
    initialCategory: BlockedCategory = BlockedCategory.ADULT,
    onDismiss: () -> Unit,
    onConfirm: (String, BlockedCategory) -> Unit
) {
    var rawInput by remember { mutableStateOf(initialDomain) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }

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
                    label = { Text("Domain or URL") },
                    placeholder = { Text("e.g. https://www.example.com/page") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("domain_input_field")
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

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Category:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val categories = listOf(
                        BlockedCategory.ADULT,
                        BlockedCategory.PORNOGRAPHY,
                        BlockedCategory.EXPLICIT,
                        BlockedCategory.GAMBLING,
                        BlockedCategory.INAPPROPRIATE
                    )
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.displayName, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rawInput, selectedCategory) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.testTag("confirm_domain_button")
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

@Composable
fun ImportDomainsDialog(
    onDismiss: () -> Unit,
    onImport: (String, BlockedCategory) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(BlockedCategory.ADULT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Blocked Domains") },
        text = {
            Column {
                Text(
                    text = "Paste domains separated by line, comma, or standard JSON blocklist payload:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("example.com\nhttps://bad-site.xxx/preview\nother-domain.test") },
                    minLines = 5,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_text_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(rawText, selectedCategory) },
                enabled = rawText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.testTag("submit_import_button")
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
