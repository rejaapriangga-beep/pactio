package com.keluargakendali.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keluargakendali.data.statusLabel

@Composable
fun StatusChip(status: String) {
    val icon = when (status) {
        "approved" -> Icons.Default.CheckCircle
        "submitted" -> Icons.Default.Pending
        "rejected" -> Icons.Default.Error
        else -> Icons.Default.Pending
    }
    AssistChip(onClick = {}, label = { Text(statusLabel(status)) }, leadingIcon = { Icon(icon, contentDescription = null) })
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    }
}
