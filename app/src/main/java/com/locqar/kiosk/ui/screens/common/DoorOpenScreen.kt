package com.locqar.kiosk.ui.screens.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen shown while a door is being opened / waiting for user to close it.
 */
@Composable
fun DoorOpenScreen(
    doorNum: Int?,
    isLoading: Boolean,
    doorIsOpen: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onOpenDoor: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (doorIsOpen) Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = if (doorIsOpen) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (doorNum != null) {
            Text(
                text = "Compartment $doorNum",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Opening door...", fontSize = 18.sp)
            }
            doorIsOpen -> {
                Text(
                    text = successMessage ?: "Door is open",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please close the door when finished.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            errorMessage != null -> {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack) { Text("Back") }
            }
            else -> {
                if (doorNum != null) {
                    Button(
                        onClick = { onOpenDoor(doorNum) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("Open Door $doorNum", fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text("Cancel") }
            }
        }
    }
}
