package com.locqar.kiosk.ui.screens.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentOrderListScreen(
    orderNumbers: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onSelectOrder: (String) -> Unit,
    onBack: () -> Unit,
) {
    var manualOrder by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
    ) {
        Text("Select Order", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Pick an order from the list or enter manually", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))

        // Manual entry
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = manualOrder,
                onValueChange = { manualOrder = it },
                label = { Text("Order Number") },
                placeholder = { Text("LQ-...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { onSelectOrder(manualOrder) },
                enabled = manualOrder.isNotBlank() && !isLoading,
            ) { Text("Go") }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (orderNumbers.isNotEmpty()) {
            Text("Your Orders at this Locker:", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(orderNumbers) { orderNum ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectOrder(orderNum) },
                ) {
                    Text(
                        text = orderNum,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 18.sp,
                    )
                }
            }
        }

        TextButton(onClick = onBack) { Text("Back") }
    }
}
