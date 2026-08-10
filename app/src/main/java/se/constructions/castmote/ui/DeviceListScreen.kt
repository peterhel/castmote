package se.constructions.castmote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.constructions.castmote.discovery.CastDevice
import se.constructions.castmote.discovery.ManualDevices

@Composable
fun DeviceListScreen(
    devices: List<CastDevice>,
    onSelect: (CastDevice) -> Unit,
    onConnectManual: (String) -> Unit,
    onRemoveManual: (String) -> Unit,
) {
    var ip by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Chromecasts", style = MaterialTheme.typography.headlineSmall)

        // Devices first — the everyday flow. mDNS discovery finds them automatically.
        if (devices.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    "Searching your network… make sure you're on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(devices, key = { it.id }) { device ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(device) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.friendlyName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (device.model.isNotBlank()) {
                                Text(
                                    device.model,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (ManualDevices.isManual(device)) {
                            TextButton(onClick = { onRemoveManual(device.host) }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        // Manual IP is the VPN/other-subnet edge case — kept out of the way behind a toggle.
        if (showManual) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fun submit() { if (ip.isNotBlank()) { onConnectManual(ip.trim()); ip = ""; showManual = false } }
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Chromecast IP") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { submit() }) { Text("Connect") }
            }
        } else {
            TextButton(onClick = { showManual = true }) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add by IP…")
            }
        }
    }
}
