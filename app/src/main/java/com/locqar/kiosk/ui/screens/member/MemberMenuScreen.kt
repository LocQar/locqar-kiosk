package com.locqar.kiosk.ui.screens.member

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MemberMenuScreen(
    isSubscriber: Boolean,
    onPickupPackage: () -> Unit,
    onCreateStorage: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome Back!", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        if (isSubscriber) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Subscriber", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onPickupPackage,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text("Pick Up My Package", fontSize = 18.sp) }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCreateStorage,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text("Store My Items", fontSize = 18.sp) }

        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onBack) { Text("Logout") }
    }
}
