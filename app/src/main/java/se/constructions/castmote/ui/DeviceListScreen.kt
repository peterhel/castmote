package se.constructions.castmote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Chromecasts", style = MaterialTheme.typography.headlineSmall)

        // Discovery can't see devices across a VPN or another subnet — let the user add by IP.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("Chromecast IP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { if (ip.isNotBlank()) { onConnectManual(ip.trim()); ip = "" } },
            ) { Text("Connect") }
        }

        if (devices.isEmpty()) {
            Text("Searching…", Modifier.padding(top = 8.dp))
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(devices, key = { it.id }) { device ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(device) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
    }
}
