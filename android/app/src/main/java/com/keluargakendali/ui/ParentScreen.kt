package com.keluargakendali.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.keluargakendali.data.EvidenceFileDto
import com.keluargakendali.data.PactioApi
import com.keluargakendali.data.TaskDto
import com.keluargakendali.data.UserDto
import com.keluargakendali.data.statusLabel
import java.io.File

@Composable
fun ParentScreen(
    state: UiState,
    onAddChild: (name: String, pin: String) -> Unit,
    onDeleteChild: (childId: String) -> Unit,
    onCreateTask: (childId: String, title: String, description: String, rewardMinutes: Int) -> Unit,
    onDecide: (taskId: String, approved: Boolean, note: String) -> Unit,
    onSetLock: (childId: String, enabled: Boolean) -> Unit,
    onDismissMessage: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showCreateTask by remember { mutableStateOf(false) }
    var lockSectionExpanded by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var childFilter by remember { mutableStateOf<String?>(null) }
    var detailTask by remember { mutableStateOf<TaskDto?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            state.errorMessage?.let {
                ErrorBanner(it, onDismissMessage)
                Spacer(Modifier.height(12.dp))
            }

            // Satu kartu ringkas: identitas keluarga, kunci per anak, dan aksi utama.
            // Tambah/hapus profil anak dipindah ke Pengaturan (ikon gerigi) - jarang dipakai,
            // tidak perlu selalu terlihat di layar utama.
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(state.family?.name ?: "Keluarga", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.family?.code != null) {
                                Text(
                                    state.family.code,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                            }
                        }
                    }

                    if (state.children.isEmpty()) {
                        Text(
                            "Belum ada profil anak. Tambah lewat Pengaturan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LockSection(
                            children = state.children,
                            expanded = lockSectionExpanded,
                            onToggleExpanded = { lockSectionExpanded = !lockSectionExpanded },
                            loading = state.loading,
                            onSetLock = onSetLock
                        )
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
                        WaitingTaskCard(
                            task = task,
                            childName = state.children.find { it.id == task.childId }?.name,
                            token = state.token,
                            loading = state.loading,
                            onDecide = onDecide
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Semua tugas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TaskFilterRow(
                children = state.children,
                selectedChildId = childFilter,
                onSelectChild = { childFilter = it },
                selectedStatus = statusFilter,
                onSelectStatus = { statusFilter = it }
            )
            Spacer(Modifier.height(8.dp))
            val filteredTasks = state.tasks.filter { task ->
                (statusFilter == null || task.status == statusFilter) && (childFilter == null || task.childId == childFilter)
            }
            if (filteredTasks.isEmpty()) {
                Text("Tidak ada tugas yang cocok dengan filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskSummaryCard(
                            task = task,
                            childName = state.children.find { it.id == task.childId }?.name,
                            onClick = { detailTask = task }
                        )
                    }
                }
            }
        }

        // Tombol "Buat Tugas" jadi FAB bulat "+" (bukan tombol lebar penuh) - lebih ringkas,
        // konsisten dengan pola aksi utama di aplikasi mobile pada umumnya. Disembunyikan
        // kalau belum ada profil anak (FloatingActionButton M3 tidak punya state disabled) -
        // parent diarahkan ke Pengaturan dulu lewat pesan di kartu atas.
        if (state.children.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showCreateTask = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Buat Tugas")
            }
        }
    }

    val currentDetailTask = detailTask
    if (currentDetailTask != null) {
        TaskDetailDialog(
            task = currentDetailTask,
            childName = state.children.find { it.id == currentDetailTask.childId }?.name,
            token = state.token,
            onDismiss = { detailTask = null }
        )
    }

    if (showSettings) {
        ParentSettingsDialog(
            children = state.children,
            loading = state.loading,
            onAddChild = onAddChild,
            onDeleteChild = onDeleteChild,
            onDismiss = { showSettings = false }
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
private fun WaitingTaskCard(task: TaskDto, childName: String?, token: String?, loading: Boolean, onDecide: (String, Boolean, String) -> Unit) {
    var showRejectDialog by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(task.title, fontWeight = FontWeight.Bold)
            if (childName != null) Text("Anak: $childName", style = MaterialTheme.typography.bodySmall)
            Text("Hadiah: ${task.rewardMinutes} menit akses", color = MaterialTheme.colorScheme.primary)
            if (!task.evidence.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Bukti: ${task.evidence}", style = MaterialTheme.typography.bodyMedium)
            }
            if (task.evidenceFiles.isNotEmpty() && token != null) {
                Spacer(Modifier.height(8.dp))
                EvidenceGallery(token = token, task = task)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide(task.id, true, "") }, enabled = !loading, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("Setujui") }
                OutlinedButton(onClick = { showRejectDialog = true }, enabled = !loading, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("Tolak") }
            }
        }
    }

    if (showRejectDialog) {
        RejectTaskDialog(
            taskTitle = task.title,
            loading = loading,
            onDismiss = { showRejectDialog = false },
            onConfirm = { note -> onDecide(task.id, false, note); showRejectDialog = false }
        )
    }
}

/**
 * Galeri kecil semua berkas bukti satu tugas (bisa campuran foto & PDF) - dipakai baik di
 * kartu "Menunggu persetujuan" maupun di TaskDetailDialog. Tiap berkas diambil terpisah lewat
 * GET /tasks/:id/evidence/:fileId (lihat PactioApi.getEvidenceFileBytes).
 */
@Composable
private fun EvidenceGallery(token: String, task: TaskDto, thumbnailSize: Dp = 72.dp) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        task.evidenceFiles.forEach { file ->
            EvidenceFileThumbnail(token = token, taskId = task.id, file = file, size = thumbnailSize)
        }
    }
}

/**
 * Satu berkas bukti: foto ditampilkan sebagai pratinjau langsung (bisa diklik untuk pratinjau
 * penuh layar); dokumen PDF ditampilkan sebagai ikon (diklik -> diunduh ke cache lalu dibuka
 * lewat aplikasi pembaca PDF eksternal yang sudah terpasang di HP, via FileProvider - lihat
 * openPdfExternally & AndroidManifest.xml).
 */
@Composable
private fun EvidenceFileThumbnail(token: String, taskId: String, file: EvidenceFileDto, size: Dp) {
    val context = LocalContext.current
    var bytes by remember(file.id) { mutableStateOf<ByteArray?>(null) }
    var failed by remember(file.id) { mutableStateOf(false) }
    var showPreview by remember(file.id) { mutableStateOf(false) }

    LaunchedEffect(file.id) {
        try {
            bytes = PactioApi.getEvidenceFileBytes(token, taskId, file.id)
        } catch (error: Exception) {
            failed = true
        }
    }

    val isPdf = file.mime == "application/pdf"
    val currentBytes = bytes
    val bitmap = remember(file.id, currentBytes) {
        if (currentBytes != null && !isPdf) BitmapFactory.decodeByteArray(currentBytes, 0, currentBytes.size) else null
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(enabled = currentBytes != null) {
                if (isPdf) openPdfExternally(context, currentBytes!!, file.id) else showPreview = true
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Foto bukti tugas",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            currentBytes != null && isPdf -> Icon(Icons.Default.Description, contentDescription = "Dokumen PDF bukti tugas")
            failed -> Icon(Icons.Default.ErrorOutline, contentDescription = "Gagal memuat", tint = MaterialTheme.colorScheme.error)
            else -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }

    if (showPreview && bitmap != null) {
        ImagePreviewDialog(bitmap = bitmap, onDismiss = { showPreview = false })
    }
}

/** Pratinjau foto bukti tugas dalam ukuran penuh, dibuka dari EvidenceFileThumbnail. */
@Composable
private fun ImagePreviewDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Pratinjau foto bukti",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/**
 * Menyimpan berkas PDF ke cache/evidence/ lalu membukanya lewat aplikasi pembaca PDF eksternal
 * yang sudah terpasang di HP - via content:// resmi (FileProvider), bukan file:// langsung
 * (diblokir sejak Android 7+). Kalau tidak ada aplikasi pembaca PDF, tampilkan pesan singkat
 * alih-alih membiarkan aplikasi crash.
 */
private fun openPdfExternally(context: Context, bytes: ByteArray, fileId: String) {
    try {
        val cacheDir = File(context.cacheDir, "evidence").apply { mkdirs() }
        val file = File(cacheDir, "$fileId.pdf")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (error: Exception) {
        Toast.makeText(context, "Tidak ada aplikasi pembaca PDF di HP ini.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RejectTaskDialog(taskTitle: String, loading: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tolak \"$taskTitle\"?") },
        text = {
            Column {
                Text(
                    "Anak akan melihat tugas ini perlu diulang. Beri tahu alasannya (opsional).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("Catatan untuk anak") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(note.trim()) }, enabled = !loading) { Text("Tolak Tugas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/**
 * Baris ringkas satu tugas di daftar "Semua Tugas" - sengaja dibuat pendek (cuma judul +
 * satu baris info), diklik untuk membuka TaskDetailDialog berisi detail lengkap & bukti.
 */
@Composable
private fun TaskSummaryCard(task: TaskDto, childName: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    (if (childName != null) "$childName · " else "") + "${task.rewardMinutes} menit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(task.status)
        }
    }
}

/** Popup detail lengkap satu tugas, dibuka dari TaskSummaryCard - termasuk galeri bukti. */
@Composable
private fun TaskDetailDialog(task: TaskDto, childName: String?, token: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(task.status)
                if (childName != null) Text("Anak: $childName", style = MaterialTheme.typography.bodySmall)
                Text("Hadiah: ${task.rewardMinutes} menit akses", color = MaterialTheme.colorScheme.primary)
                if (task.description.isNotBlank()) {
                    Text(task.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (!task.evidence.isNullOrBlank()) {
                    Text("Bukti (teks): ${task.evidence}", style = MaterialTheme.typography.bodyMedium)
                }
                if (task.evidenceFiles.isNotEmpty() && token != null) {
                    Text("Berkas bukti:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    EvidenceGallery(token = token, task = task, thumbnailSize = 88.dp)
                }
                if (task.status == "rejected" && !task.decisionNote.isNullOrBlank()) {
                    Text("Catatan orang tua: ${task.decisionNote}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/**
 * Kontrol Perangkat, diringkas jadi satu baris yang bisa di-expand/collapse (tertutup
 * secara default) - cuma menampilkan ringkasan (berapa anak sedang terkunci) sampai orang
 * tua sengaja membukanya, supaya tidak memenuhi layar tiap kali dibuka.
 */
@Composable
private fun LockSection(
    children: List<UserDto>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    loading: Boolean,
    onSetLock: (childId: String, enabled: Boolean) -> Unit
) {
    val lockedCount = children.count { it.lockModeEnabled }

    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Kontrol Perangkat", fontWeight = FontWeight.SemiBold)
                Text(
                    if (lockedCount == 0) "Tidak ada yang dikunci" else "$lockedCount dari ${children.size} anak dikunci",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lockedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Sembunyikan" else "Tampilkan"
            )
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                children.forEach { child ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(child.name)
                        Switch(
                            checked = child.lockModeEnabled,
                            onCheckedChange = { onSetLock(child.id, it) },
                            enabled = !loading
                        )
                    }
                }
            }
        }
    }
}

/**
 * Filter "Semua tugas": dua dropdown berdampingan (anak & status), bukan baris chip yang
 * digulir - lebih ringkas di layar sempit. Dropdown anak hanya muncul kalau ada lebih dari
 * satu profil anak (percuma memfilter kalau cuma satu).
 */
@Composable
private fun TaskFilterRow(
    children: List<UserDto>,
    selectedChildId: String?,
    onSelectChild: (String?) -> Unit,
    selectedStatus: String?,
    onSelectStatus: (String?) -> Unit
) {
    val statuses = listOf("assigned", "submitted", "approved", "rejected")

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (children.size > 1) {
            FilterDropdown(
                modifier = Modifier.weight(1f),
                label = "Anak",
                selectedLabel = children.find { it.id == selectedChildId }?.name ?: "Semua Anak",
                options = listOf<Pair<String?, String>>(null to "Semua Anak") + children.map { it.id to it.name },
                onSelect = onSelectChild
            )
        }
        FilterDropdown(
            modifier = Modifier.weight(1f),
            label = "Status",
            selectedLabel = selectedStatus?.let { statusLabel(it) } ?: "Semua Status",
            options = listOf<Pair<String?, String>>(null to "Semua Status") + statuses.map { it to statusLabel(it) },
            onSelect = onSelectStatus
        )
    }
}

/** Satu dropdown filter (Material3 ExposedDropdownMenuBox, read-only text field). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
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

/**
 * Pengaturan orang tua: kelola profil anak (tambah/hapus) - dipisah dari layar utama
 * (dibuka lewat ikon gerigi) supaya layar utama tidak dipenuhi aksi yang jarang dipakai.
 */
@Composable
private fun ParentSettingsDialog(
    children: List<UserDto>,
    loading: Boolean,
    onAddChild: (name: String, pin: String) -> Unit,
    onDeleteChild: (childId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddChild by remember { mutableStateOf(false) }
    var childPendingDelete by remember { mutableStateOf<UserDto?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan") },
        text = {
            Column {
                Text("Profil Anak", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (children.isEmpty()) {
                    Text(
                        "Belum ada profil anak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    children.forEach { child ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(child.name)
                            IconButton(onClick = { childPendingDelete = child }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus ${child.name}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showAddChild = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Tambah Anak")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )

    if (showAddChild) {
        AddChildDialog(
            loading = loading,
            onDismiss = { showAddChild = false },
            onSubmit = { name, pin -> onAddChild(name, pin); showAddChild = false }
        )
    }

    val deleteTarget = childPendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { childPendingDelete = null },
            title = { Text("Hapus profil ${deleteTarget.name}?") },
            text = {
                Text(
                    "Semua tugas dan riwayatnya akan ikut terhapus, dan perangkat anak ini " +
                        "akan otomatis keluar. Tindakan ini tidak bisa dibatalkan."
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteChild(deleteTarget.id); childPendingDelete = null },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { childPendingDelete = null }) { Text("Batal") } }
        )
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
                PasswordField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, "PIN (4-8 digit)", KeyboardType.NumberPassword)
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
