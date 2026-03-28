package com.locqar.kiosk.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings / provisioning screen — accessed by long-pressing the LocQar logo on the home screen.
 * Allows setting: locker serial number, station number, demo mode toggle.
 */
@Composable
fun SettingsScreen(
    currentLockerSN: String,
    currentStationNumber: Int,
    isDemoMode: Boolean,
    onSave: (lockerSN: String, stationNumber: Int, demoMode: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var lockerSN by remember { mutableStateOf(currentLockerSN) }
    var stationNum by remember { mutableStateOf(currentStationNumber.toString()) }
    var demoMode by remember { mutableStateOf(isDemoMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
    ) {
        Text("Kiosk Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Configure this kiosk for deployment", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = lockerSN,
            onValueChange = { lockerSN = it },
            label = { Text("Locker Serial Number") },
            placeholder = { Text("e.g. 20422469802A-001") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = stationNum,
            onValueChange = { stationNum = it.filter { c -> c.isDigit() } },
            label = { Text("RS485 Station Number") },
            placeholder = { Text("1") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Set by DIP switch on the lock control board (1–255)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Demo Mode", fontWeight = FontWeight.Medium)
                Text(
                    "Simulates hardware without RS485 connection",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = demoMode, onCheckedChange = { demoMode = it })
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onSave(lockerSN, stationNum.toIntOrNull() ?: 1, demoMode) },
            enabled = lockerSN.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Save & Apply", fontSize = 18.sp) }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back")
        }
    }
}
