package com.keluargakendali.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keluargakendali.R

/** Satu topik panduan - `steps` sudah teks jadi (satu baris per langkah), bukan resId. */
private data class GuideSection(val icon: ImageVector, val title: String, val steps: List<String>)

/**
 * Isi Panduan Penggunaan - berbeda dari tur coach-mark (TutorialOverlay.kt) yang cuma menyorot
 * beberapa elemen Dashboard: ini referensi TEKS lengkap untuk semua aksi utama orang tua,
 * termasuk yang tersebar di tab/dialog berbeda (Approval, Chat, Kunci, Pengaturan, dst) - tidak
 * praktis dibuat jadi satu tur sorotan berpindah tab, jadi dibuat sebagai halaman rujukan biasa
 * yang bisa dibuka kapan saja lewat "Panduan Penggunaan" di Pengaturan.
 */
@Composable
private fun guideSections(): List<GuideSection> = listOf(
    GuideSection(Icons.Default.PersonAdd, stringResource(R.string.guide_title_add_child), stringResource(R.string.guide_steps_add_child).split("\n")),
    GuideSection(Icons.Default.Add, stringResource(R.string.guide_title_create_task), stringResource(R.string.guide_steps_create_task).split("\n")),
    GuideSection(Icons.Default.FactCheck, stringResource(R.string.guide_title_approval), stringResource(R.string.guide_steps_approval).split("\n")),
    GuideSection(Icons.Default.Chat, stringResource(R.string.guide_title_chat), stringResource(R.string.guide_steps_chat).split("\n")),
    GuideSection(Icons.Default.Lock, stringResource(R.string.guide_title_lock), stringResource(R.string.guide_steps_lock).split("\n")),
    GuideSection(Icons.Default.Backup, stringResource(R.string.guide_title_backup), stringResource(R.string.guide_steps_backup).split("\n")),
    GuideSection(Icons.Default.Password, stringResource(R.string.guide_title_change_password), stringResource(R.string.guide_steps_change_password).split("\n")),
    GuideSection(Icons.Default.Delete, stringResource(R.string.guide_title_delete_child), stringResource(R.string.guide_steps_delete_child).split("\n")),
    GuideSection(Icons.Default.History, stringResource(R.string.guide_title_activity_log), stringResource(R.string.guide_steps_activity_log).split("\n")),
    GuideSection(Icons.Default.DarkMode, stringResource(R.string.guide_title_theme), stringResource(R.string.guide_steps_theme).split("\n")),
    GuideSection(Icons.Default.Translate, stringResource(R.string.guide_title_language), stringResource(R.string.guide_steps_language).split("\n"))
)

/**
 * Halaman penuh (bukan AlertDialog biasa - kontennya terlalu banyak untuk muat wajar di lebar
 * dialog standar) berisi semua topik panduan di atas, masing-masing sebagai kartu dengan ikon,
 * judul, dan langkah bernomor. Dibuka dari tombol "Panduan Penggunaan" di ParentSettingsDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val sections = guideSections()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.action_open_guide)) },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sections) { section -> GuideSectionCard(section) }
            }
        }
    }
}

@Composable
private fun GuideSectionCard(section: GuideSection) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            section.steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
