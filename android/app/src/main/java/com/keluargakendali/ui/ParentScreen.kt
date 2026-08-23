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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.keluargakendali.data.TaskDto
import com.keluargakendali.data.UserDto

@Composable
fun ParentScreen(
    state: UiState,
    onAddChild: (name: String, pin: String) -> Unit,
    onCreateTask: (childId: String, title: String, description: String, rewardMinutes: Int) -> Unit,
    onDecide: (taskId: String, approved: Boolean, note: String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var showAddChild by remember { mutableStateOf(false) }
    var showCreateTask by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        state.errorMessage?.let {
            ErrorBanner(it, onDismissMessage)
            Spacer(Modifier.height(12.dp))
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(state.family?.name ?: "Keluarga", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (state.family?.code != null) {
                    Text("Kode keluarga untuk anak: ${state.family.code}", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.children.forEach { child ->
                        AssistChip(onClick = {}, label = { Text(child.name) }, leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAddChild = true }) { Text("Tambah Anak") }
                    Button(onClick = { showCreateTask = true }, enabled = state.children.isNotEmpty()) { Text("Buat Tugas") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Menunggu persetujuan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val waiting = state.tasks.filter { it.status == "submitted" }
        if (waiting.isEmpty()) {
            Text("Belum ada tugas yang dikirim anak.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(waiting, key = { it.id }) { task ->
                    WaitingTaskCard(task = task, childName = state.children.find { it.id == task.childId }?.name, onDecide = onDecide)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Semua tugas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.tasks, key = { it.id }) { task ->
                TaskSummaryCard(task = task, childName = state.children.find { it.id == task.childId }?.name)
            }
        }
    }

    if (showAddChild) {
        AddChildDialog(
            loading = state.loading,
            onDismiss = { showAddChild = false },
            onSubmit = { name, pin -> onAddChild(name, pin); showAddChild = false }
        )
    }
    if (showCreateTask) {
        CreateTaskDialog(
            children = state.children,
            loading = state.loading,
            onDismiss = { showCreateTask = false },
            onSubmit = { childId, title, description, minutes ->
                onCreateTask(childId, title, description, minutes)
                showCreateTask = false
            }
        )
    }
}

@Composable
private fun WaitingTaskCard(task: TaskDto, childName: String?, onDecide: (String, Boolean, String) -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(task.title, fontWeight = FontWeight.Bold)
            if (childName != null) Text("Anak: $childName", style = MaterialTheme.typography.bodySmall)
            Text("Hadiah: ${task.rewardMinutes} menit akses", color = MaterialTheme.colorScheme.primary)
            if (!task.evidence.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Bukti: ${task.evidence}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide(task.id, true, "") }, modifier = Modifier.weight(1f)) { Text("Setujui") }
                OutlinedButton(onClick = { onDecide(task.id, false, "") }, modifier = Modifier.weight(1f)) { Text("Tolak") }
            }
        }
    }
}

@Composable
private fun TaskSummaryCard(task: TaskDto, childName: String?) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.title, fontWeight = FontWeight.Bold)
                StatusChip(task.status)
            }
            if (childName != null) Text("Anak: $childName", style = MaterialTheme.typography.bodySmall)
            Text("Hadiah: ${task.rewardMinutes} menit akses", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AddChildDialog(loading: Boolean, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah profil anak") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama anak") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("PIN (4-8 digit)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name.trim(), pin) },
                enabled = !loading && name.isNotBlank() && pin.length in 4..8
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskDialog(
    children: List<UserDto>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (childId: String, title: String, description: String, rewardMinutes: Int) -> Unit
) {
    var selectedChild by remember { mutableStateOf(children.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rewardText by remember { mutableStateOf("15") }
    val rewardMinutes = rewardText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat tugas baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedChild?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Anak") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        children.forEach { child ->
                            DropdownMenuItem(
                                text = { Text(child.name) },
                                onClick = { selectedChild = child; expanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Judul tugas") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Deskripsi (opsional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    rewardText, { rewardText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Hadiah (menit, 1-240)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedChild!!.id, title.trim(), description.trim(), rewardMinutes!!) },
                enabled = !loading && selectedChild != null && title.isNotBlank() &&
                    rewardMinutes != null && rewardMinutes in 1..240
            ) { Text("Buat Tugas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
