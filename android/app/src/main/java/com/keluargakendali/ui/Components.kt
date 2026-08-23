package com.keluargakendali.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.keluargakendali.data.statusLabel

/** Chip status tugas — berwarna per status (hijau/emas/merah/netral), sesuai mockup. */
@Composable
fun StatusChip(status: String) {
    val icon = when (status) {
        "approved" -> Icons.Default.CheckCircle
        "submitted" -> Icons.Default.HourglassTop
        "rejected" -> Icons.Default.Error
        else -> Icons.Default.HourglassTop
    }
    val (container, content) = when (status) {
        "approved" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "submitted" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "rejected" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(statusLabel(status)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        shape = RoundedCornerShape(999.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = content,
            leadingIconContentColor = content
        )
    )
}

/**
 * Field password/PIN dengan tombol lihat/sembunyikan teks, dipakai di seluruh form auth.
 * Sengaja TIDAK memakai slot `trailingIcon` bawaan OutlinedTextField (terbukti tidak
 * tampil sama sekali di perangkat nyata pada versi Compose yang dipakai project ini) -
 * tombolnya ditaruh sebagai elemen terpisah di sebelah field lewat Row biasa, yang jauh
 * lebih sederhana dan pasti terlihat.
 */
@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, keyboardType: KeyboardType) {
    var visible by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { visible = !visible }) {
            Icon(
                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (visible) "Sembunyikan" else "Tampilkan"
            )
        }
    }
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
