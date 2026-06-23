package org.thepacket.meshcore.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.thepacket.meshcore.app.ChannelEntry
import org.thepacket.meshcore.app.Conversation
import org.thepacket.meshcore.app.MeshSession
import org.thepacket.meshcore.protocol.Contact
import org.thepacket.meshcore.protocol.ContactType
import org.thepacket.meshcore.protocol.SelfInfo
import org.thepacket.meshcore.protocol.toHex
import org.thepacket.meshcore.protocol.hexToBytes
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun HomeContent(
    session: MeshSession,
    self: SelfInfo?,
    channels: List<ChannelEntry>,
    contacts: List<Contact>,
    onOpenConversation: (id: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    // An exported contact card arrives asynchronously — show it for copy/share.
    var exportedCard by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) {
        session.exportedContact.collect { exportedCard = it }
    }

    Column(modifier.fillMaxSize()) {
        self?.let {
            Box(Modifier.padding(12.dp)) { DeviceHeader(it) }
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Contacts") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Channels") })
        }
        when (tab) {
            0 -> ContactsList(session, self, contacts, onOpenConversation)
            else -> ChannelsList(session, channels, onOpenConversation)
        }
    }

    exportedCard?.let { card ->
        ExportCardDialog(card) { exportedCard = null }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactsList(
    session: MeshSession,
    self: SelfInfo?,
    contacts: List<Contact>,
    onOpen: (String, String) -> Unit,
) {
    var detail by remember { mutableStateOf<Contact?>(null) }
    var showImport by remember { mutableStateOf(false) }

    // Sorted alphabetically by display name (case-insensitive).
    val sorted = remember(contacts) {
        contacts.sortedBy { (it.name.ifBlank { it.keyPrefixHex }).lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedButton(onClick = { showImport = true }) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Import contact")
            }
        }
        if (sorted.isEmpty()) {
            EmptyHint("No contacts synced yet.")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.keyPrefixHex }) { c ->
                    ConversationRow(
                        icon = if (c.isRepeater) Icons.Default.Router else Icons.Default.Person,
                        tint = nameColor(c.name.ifBlank { c.keyPrefixHex }),
                        title = c.name.ifBlank { c.keyPrefixHex },
                        subtitle = contactTypeLabel(c.type),
                        onClick = { onOpen(Conversation.dmId(c), c.name.ifBlank { c.keyPrefixHex }) },
                        onLongClick = { detail = c },
                    )
                }
            }
        }
    }

    detail?.let { c ->
        NodeDetailSheet(
            name = c.name.ifBlank { c.keyPrefixHex },
            type = c.type,
            isSelf = false,
            contact = c,
            heard = null,
            self = self,
            onDismiss = { detail = null },
            onShare = { session.shareContact(c); detail = null },
            onResetPath = { session.resetPath(c) },
            onExport = { session.exportContact(c); detail = null },
            onRemove = { session.removeContact(c); detail = null },
        )
    }

    if (showImport) {
        ImportContactDialog(
            onDismiss = { showImport = false },
            onImport = { hex -> session.importContact(hex); showImport = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelsList(
    session: MeshSession,
    channels: List<ChannelEntry>,
    onOpen: (String, String) -> Unit,
) {
    var editing by remember { mutableStateOf<ChannelEntry?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  New channel")
            }
        }
        if (channels.isEmpty()) {
            EmptyHint("No channels.")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(channels, key = { "ch:${it.index}" }) { ch ->
                    ConversationRow(
                        icon = Icons.Default.Campaign,
                        tint = MaterialTheme.colorScheme.tertiary,
                        title = ch.displayName,
                        subtitle = "Channel ${ch.index} · long-press to edit",
                        onClick = { onOpen(Conversation.channelId(ch.index), ch.displayName) },
                        onLongClick = { editing = ch },
                    )
                }
            }
        }
    }

    val existing = channels.map { it.index }.toSet()
    if (creating) {
        val nextSlot = (0..7).firstOrNull { it !in existing } ?: 0
        ChannelDialog(
            title = "New channel",
            initialIndex = nextSlot,
            initialName = "",
            initialSecretHex = randomSecretHex(),
            indexFixed = false,
            onDismiss = { creating = false },
            onSave = { idx, name, secret -> session.setChannel(idx, name, secret); creating = false },
        )
    }
    editing?.let { ch ->
        ChannelDialog(
            title = "Edit channel ${ch.index}",
            initialIndex = ch.index,
            initialName = ch.name,
            initialSecretHex = ch.secret.toHex(),
            indexFixed = true,
            onDismiss = { editing = null },
            onSave = { idx, name, secret -> session.setChannel(idx, name, secret); editing = null },
        )
    }
}

@Composable
private fun ImportContactDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import contact") },
        text = {
            Column {
                Text("Paste a contact card (hex) exported from another node.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Contact card") }, singleLine = false, maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExportCardDialog(card: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contact card") },
        text = {
            SelectionContainer {
                Text(card, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ChannelDialog(
    title: String,
    initialIndex: Int,
    initialName: String,
    initialSecretHex: String,
    indexFixed: Boolean,
    onDismiss: () -> Unit,
    onSave: (index: Int, name: String, secret: ByteArray) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var secretHex by remember { mutableStateOf(initialSecretHex) }
    val cleanSecret = secretHex.trim().replace(" ", "").lowercase()
    val secretValid = cleanSecret.length == 32 && cleanSecret.all { it in "0123456789abcdef" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = secretHex, onValueChange = { secretHex = it },
                    label = { Text("Secret (32 hex chars = 128-bit)") }, singleLine = true,
                    isError = !secretValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { secretHex = randomSecretHex() }) { Text("Randomize key") }
                    Text("Slot $initialIndex", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initialIndex, name.trim(), cleanSecret.hexToBytes()) },
                enabled = name.isNotBlank() && secretValid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A fresh random 128-bit channel key as 32 hex chars. */
private fun randomSecretHex(): String =
    ByteArray(16).also { Random.nextBytes(it) }.toHex()

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun DeviceHeader(self: SelfInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(self.name.ifBlank { "(unnamed node)" }, style = MaterialTheme.typography.titleLarge)
            Text("${self.freqMhz} MHz · ${self.bwKhz} kHz · SF${self.radioSf} · 4/${self.radioCr} · ${self.txPower} dBm",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = tint.copy(alpha = 0.18f), shape = CircleShape) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

private fun contactTypeLabel(type: Int) = when (type) {
    ContactType.CHAT -> "Contact"
    ContactType.REPEATER -> "Repeater"
    ContactType.ROOM -> "Room"
    ContactType.SENSOR -> "Sensor"
    else -> "Node"
}

/** Stable per-name colour (matches the on-device UI's hashed-username idea). */
fun nameColor(name: String): Color {
    val palette = listOf(
        Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFFF59E0B), Color(0xFFF472B6),
        Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFFB7185), Color(0xFF34D399),
    )
    return palette[abs(name.hashCode()) % palette.size]
}
