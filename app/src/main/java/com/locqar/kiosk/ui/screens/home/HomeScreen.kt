package com.locqar.kiosk.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locqar.kiosk.viewmodel.KioskScreen

/**
 * Home screen — the kiosk idle state.
 * Three entry paths: Agent (courier), Member (student/customer), Guest (recipient).
 * Long-press on the logo to access device settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigate: (KioskScreen) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "LocQar",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.combinedClickable(
                onClick = { },
                onLongClick = onOpenSettings,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Smart Parcel Locker",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = "How would you like to proceed?",
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Agent button
        Button(
            onClick = { onNavigate(KioskScreen.AGENT_LOGIN) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("I'm a Courier Agent", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Member button
        OutlinedButton(
            onClick = { onNavigate(KioskScreen.MEMBER_LOGIN) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("I'm a Member / Student", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Guest button
        OutlinedButton(
            onClick = { onNavigate(KioskScreen.GUEST_ORDER_ENTRY) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("I have an Order Number", fontSize = 18.sp)
        }
    }
}
