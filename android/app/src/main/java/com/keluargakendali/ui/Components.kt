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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Satu item top-menu (Dashboard/Daftar Tugas/dst) - badgeCount > 0 menampilkan lingkaran angka di atas ikonnya. */
data class TabItem(val label: String, val icon: ImageVector, val badgeCount: Int = 0)

/**
 * Top menu (tab bar) utama dashboard - dipakai baik oleh ParentScreen maupun ChildScreen
 * (dengan daftar tab yang berbeda sesuai peran). Sengaja TabRow biasa (bukan ScrollableTabRow)
 * supaya semua tab MUAT dalam satu baris tanpa perlu digulir - tiap tab otomatis berbagi rata
 * lebar layar. Pengaturan sengaja TIDAK ikut di sini (lihat MainActivity) supaya jumlah tab
 * tetap sedikit dan muat nyaman.
 */
@Composable
fun PactioTabRow(items: List<TabItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selectedIndex) {
        items.forEachIndexed { index, item ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                text = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                icon = {
                    if (item.badgeCount > 0) {
                        BadgedBox(badge = { Badge { Text(if (item.badgeCount > 99) "99+" else item.badgeCount.toString()) } }) {
                            Icon(item.icon, contentDescription = item.label)
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.label)
                    }
                }
            )
        }
    }
}

/**
 * Baris sub-tab pemilih thread chat (grup "Semua Anak"/"Keluarga" + tiap thread privat) - tampil
 * tepat di bawah top menu utama SELAGI tab Chat aktif, dipakai baik oleh ParentScreen maupun
 * ChildScreen. Menggantikan dropdown lama khusus untuk kebutuhan ini - lihat renderChatSubTabs
 * di web/app.js untuk pola yang setara.
 */
@Composable
fun ChatSubTabs(options: List<Pair<String, String>>, selectedId: String, unreadByThread: Map<String, Int>, onSelect: (String) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = options.indexOfFirst { it.first == selectedId }.coerceAtLeast(0),
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        options.forEach { (id, label) ->
            val unread = unreadByThread[id] ?: 0
            Tab(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                text = {
                    if (unread > 0) {
                        BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    }
}

/**
 * Satu dropdown pilihan (Material3 ExposedDropdownMenuBox, read-only text field) - dipakai
 * untuk filter Daftar Tugas (anak/status) di ParentScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
    modifier: Modifier = Modifier,
    label: String,
    selectedLabel: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onSelect(value); expanded = false })
            }
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
