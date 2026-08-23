package com.keluargakendali.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keluargakendali.data.TaskDto

@Composable
fun ChildScreen(
    state: UiState,
    onSubmitTask: (taskId: String, evidence: String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var submittingTask by remember { mutableStateOf<TaskDto?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        state.errorMessage?.let {
            ErrorBanner(it, onDismissMessage)
            Spacer(Modifier.height(12.dp))
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Saldo akses hadiah", fontWeight = FontWeight.Bold)
                    Text("${state.balanceMinutes} menit dari ${state.approvedTaskCount} tugas disetujui")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Tugas kamu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (state.tasks.isEmpty()) {
            Text("Belum ada tugas dari orang tua.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.tasks, key = { it.id }) { task ->
                    ChildTaskCard(task = task, onKirim = { submittingTask = task })
                }
            }
        }
    }

    val current = submittingTask
    if (current != null) {
        SubmitEvidenceDialog(
            task = current,
            loading = state.loading,
            onDismiss = { submittingTask = null },
            onSubmit = { evidence ->
                onSubmitTask(current.id, evidence)
                submittingTask = null
            }
        )
    }
}

@Composable
private fun ChildTaskCard(task: TaskDto, onKirim: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.title, fontWeight = FontWeight.Bold)
                StatusChip(task.status)
            }
            Spacer(Modifier.height(6.dp))
            if (task.description.isNotBlank()) {
                Text(task.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            Text("Hadiah: ${task.rewardMinutes} menit akses", color = MaterialTheme.colorScheme.primary)
            if (task.status == "rejected" && !task.decisionNote.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Catatan orang tua: ${task.decisionNote}", style = MaterialTheme.typography.bodySmall)
            }
            if (task.status == "assigned" || task.status == "rejected") {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onKirim, modifier = Modifier.fillMaxWidth()) {
                    Text("Kirim sebagai selesai")
                }
            }
        }
    }
}

@Composable
private fun SubmitEvidenceDialog(task: TaskDto, loading: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var evidence by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kirim \"${task.title}\" sebagai selesai") },
        text = {
            Column {
                Text(
                    "Ceritakan apa yang sudah kamu lakukan. Saat ini bukti berupa teks saja (belum foto).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    evidence, { evidence = it },
                    label = { Text("Bukti selesai") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(evidence.trim()) }, enabled = !loading) { Text("Kirim") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
