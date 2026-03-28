package com.locqar.kiosk.ui.screens.payment

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

/**
 * Payment QR screen — renders a QR code from the Paystack payment URL.
 * Polls the API every 5 seconds to check if payment has been completed.
 */
@Composable
fun PaymentQrScreen(
    paymentUrl: String?,
    orderNumber: String?,
    onPaymentComplete: () -> Unit,
    onCheckPayment: () -> Unit,
    onCancel: () -> Unit,
) {
    // Generate QR bitmap from URL
    val qrBitmap = remember(paymentUrl) {
        paymentUrl?.let { generateQrBitmap(it, 600) }
    }

    // Poll for payment completion every 5 seconds
    LaunchedEffect(orderNumber) {
        while (true) {
            delay(5_000)
            onCheckPayment()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Scan to Pay", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scan the QR code below with your phone to complete payment",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (qrBitmap != null) {
            Card(
                modifier = Modifier.size(280.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Payment QR Code",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Generating QR code...", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (orderNumber != null) {
            Text(
                text = "Order: $orderNumber",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Waiting for payment...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(32.dp))

        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

/**
 * Generate a QR code Bitmap from a string using ZXing.
 */
private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
