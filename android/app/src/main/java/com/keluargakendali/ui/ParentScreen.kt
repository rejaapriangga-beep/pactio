package com.keluargakendali.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.keluargakendali.data.ActivityLogEntryDto
import com.keluargakendali.data.ChatMessageDto
import com.keluargakendali.data.EVIDENCE_MIME_EXT
import com.keluargakendali.data.EvidenceFileDto
import com.keluargakendali.data.FAMILY_CHAT_THREAD_ID
import com.keluargakendali.data.PactioApi
import com.keluargakendali.data.TaskDto
import com.keluargakendali.data.UserDto
import com.keluargakendali.data.activityActionLabel
import com.keluargakendali.data.statusLabel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ParentScreen(
    state: UiState,
    onDecide: (taskId: String, approved: Boolean, note: String) -> Unit,
    onSetLock: (childId: String, enabled: Boolean) -> Unit,
    onCreateTask: (childId: String, title: String, description: String, rewardMinutes: Int) -> Unit,
    onDismissMessage: () -> Unit,
    onRefreshChatUnread: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showCreateTask by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var childFilter by remember { mutableStateOf<String?>(null) }
    var detailTask by remember { mutableStateOf<TaskDto?>(null) }
    var selectedChatThreadId by remember { mutableStateOf<String?>(null) }

    val approvalCount = state.tasks.count { it.status == "submitted" }
    // Pengaturan sengaja TIDAK ikut sebagai tab - dipindah jadi ikon gerigi di TopAppBar
    // (lihat MainActivity), tepat di sebelah kiri "Keluar", supaya 5 tab ini muat satu baris.
    val tabs = listOf(
        TabItem("Dashboard", Icons.Default.Dashboard),
        TabItem("Tugas", Icons.Default.Checklist),
        TabItem("Approval", Icons.Default.FactCheck, badgeCount = approvalCount),
        TabItem("Chat", Icons.Default.Chat, badgeCount = state.chatUnreadTotal),
        TabItem("Kunci", Icons.Default.Lock)
    )

    Column(Modifier.fillMaxSize()) {
        state.errorMessage?.let {
            Box(Modifier.padding(16.dp)) { ErrorBanner(it, onDismissMessage) }
        }

        PactioTabRow(items = tabs, selectedIndex = selectedTab, onSelect = { selectedTab = it })

        when (selectedTab) {
            0 -> ParentDashboardTab(
                state = state,
                approvalCount = approvalCount,
                onCreateTask = { showCreateTask = true },
                onDecide = onDecide,
                onSetLock = onSetLock
            )
            1 -> ParentTaskListTab(
                state = state,
                statusFilter = statusFilter,
                onSelectStatus = { statusFilter = it },
                childFilter = childFilter,
                onSelectChild = { childFilter = it },
                onOpenDetail = { detailTask = it }
            )
            2 -> ParentApprovalTab(state = state, onDecide = onDecide)
            3 -> ParentChatTab(
                state = state,
                selectedThreadId = selectedChatThreadId,
                onSelectThread = { selectedChatThreadId = it },
                onRefreshUnread = onRefreshChatUnread
            )
            4 -> ParentLockTab(children = state.children, loading = state.loading, onSetLock = onSetLock)
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

/**
 * Pengaturan orang tua: kelola profil anak (tambah/hapus). Dipanggil dari MainActivity lewat
 * ikon gerigi di TopAppBar (bukan tab lagi - lihat catatan di ParentScreen), jadi dialog ini
 * PUBLIC dan berdiri sendiri, membawa semua state form-nya sendiri (formulir tambah & konfirmasi
 * hapus tetap popup di atas dialog ini - transient, jarang dipakai).
 */
@Composable
fun ParentSettingsDialog(
    children: List<UserDto>,
    activityLog: List<ActivityLogEntryDto>,
    loading: Boolean,
    token: String?,
    familyName: String?,
    onAddChild: (name: String, pin: String) -> Unit,
    onDeleteChild: (childId: String) -> Unit,
    onResetPin: (childId: String, pin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddChild by remember { mutableStateOf(false) }
    var childPendingDelete by remember { mutableStateOf<UserDto?>(null) }
    var childPendingResetPin by remember { mutableStateOf<UserDto?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan") },
        text = {
            // Dialog ini sudah cukup panjang (profil anak + log aktivitas) - digulir supaya
            // tetap muat di layar kecil, konsisten dengan pola LazyColumn di layar lain.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Profil Anak", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "PIN anak disimpan terenkripsi dan tidak bisa ditampilkan ulang. Kalau anak lupa PIN, gunakan Reset PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            Text(child.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { childPendingResetPin = child }) {
                                Icon(Icons.Default.Key, contentDescription = "Reset PIN ${child.name}")
                            }
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

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                if (token != null) BackupSection(token = token, familyName = familyName)

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Log Aktivitas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (activityLog.isEmpty()) {
                    Text(
                        "Belum ada aktivitas tercatat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    activityLog.forEach { entry -> ActivityLogRow(entry) }
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

    val resetPinTarget = childPendingResetPin
    if (resetPinTarget != null) {
        ResetPinDialog(
            childName = resetPinTarget.name,
            loading = loading,
            onDismiss = { childPendingResetPin = null },
            onSubmit = { pin -> onResetPin(resetPinTarget.id, pin); childPendingResetPin = null }
        )
    }

    val deleteTarget = childPendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { childPendingDelete = null },
            title = { Text("Hapus profil ${deleteTarget.name}?") },
            text = {
                Text(
                    "Semua tugas, riwayat chat, dan foto bukti miliknya akan ikut terhapus, dan " +
                        "perangkat anak ini akan otomatis keluar. Tindakan ini tidak bisa dibatalkan."
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

/**
 * Cadangan data keluarga terenkripsi, diunduh ke penyimpanan yang orang tua pilih sendiri lewat
 * Storage Access Framework (bukan folder tersembunyi aplikasi) - lihat catatan lengkap enkripsi
 * di PactioApi.createBackup/server.js. Alurnya: password dulu -> panggil API -> baru buka
 * pemilih lokasi simpan (CreateDocument) begitu byte-nya sudah siap ditulis.
 */
@Composable
private fun BackupSection(token: String, familyName: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var backupLoading by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var pendingBackupBytes by remember { mutableStateOf<ByteArray?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = pendingBackupBytes
        pendingBackupBytes = null
        if (uri != null && bytes != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                .onSuccess { Toast.makeText(context, "Backup terenkripsi berhasil disimpan. Simpan kata sandinya baik-baik - server TIDAK menyimpannya.", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "Gagal menyimpan berkas backup.", Toast.LENGTH_SHORT).show() }
        }
    }

    LaunchedEffect(pendingBackupBytes) {
        if (pendingBackupBytes != null) {
            val safeFamilyName = (familyName ?: "pactio").lowercase().replace(Regex("[^a-z0-9]+"), "-")
            val dateStr = java.time.LocalDate.now().toString()
            createDocumentLauncher.launch("pactio-backup-$safeFamilyName-$dateStr.json")
        }
    }

    Column {
        Text("Cadangan Data", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Unduh salinan data keluarga (profil anak, tugas, riwayat chat) sebagai berkas terenkripsi ke penyimpanan HP kamu. Kata sandinya kamu tentukan sendiri - server TIDAK menyimpannya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showPasswordDialog = true }, modifier = Modifier.fillMaxWidth(), enabled = !backupLoading) {
            Text("Unduh Backup Terenkripsi")
        }
    }

    if (showPasswordDialog) {
        BackupPasswordDialog(
            loading = backupLoading,
            error = backupError,
            onDismiss = { showPasswordDialog = false; backupError = null },
            onSubmit = { password ->
                backupLoading = true
                backupError = null
                scope.launch {
                    runCatching { PactioApi.createBackup(token, password) }
                        .onSuccess { result ->
                            val json = org.json.JSONObject()
                                .put("format", result.format)
                                .put("salt", result.salt)
                                .put("iv", result.iv)
                                .put("tag", result.tag)
                                .put("ciphertext", result.ciphertext)
                            pendingBackupBytes = json.toString(2).toByteArray(Charsets.UTF_8)
                            showPasswordDialog = false
                        }
                        .onFailure { backupError = it.message ?: "Gagal membuat backup." }
                    backupLoading = false
                }
            }
        )
    }
}

@Composable
private fun BackupPasswordDialog(loading: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && password != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unduh Backup Terenkripsi") },
        text = {
            Column {
                Text(
                    "Buat kata sandi backup (minimal 8 karakter). Kata sandi ini HARUS kamu ingat sendiri - dipakai lagi nanti untuk membuka berkas ini, server tidak menyimpannya sama sekali.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(password, { password = it }, "Kata sandi backup", KeyboardType.Password)
                PasswordField(confirm, { confirm = it }, "Ulangi kata sandi", KeyboardType.Password)
                if (mismatch) Text("Kata sandi tidak sama.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(password) },
                enabled = !loading && password.length >= 8 && password == confirm
            ) { Text("Unduh Backup") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ResetPinDialog(childName: String, loading: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset PIN $childName") },
        text = {
            Column {
                Text(
                    "PIN lama langsung tidak berlaku begitu PIN baru disimpan. Beri tahu PIN baru ini ke $childName secara langsung.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, "PIN baru (4-8 digit)", KeyboardType.NumberPassword)
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(pin) }, enabled = !loading && pin.length in 4..8) { Text("Simpan PIN Baru") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Satu baris Log Aktivitas - lihat activityActionLabel di Models.kt untuk pemetaan kode -> teks. */
@Composable
private fun ActivityLogRow(entry: ActivityLogEntryDto) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                buildString {
                    append(entry.actorName)
                    append(if (entry.actorRole == "parent") " (Orang Tua) " else " (Anak) ")
                    append(activityActionLabel(entry.action))
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatActivityLogTime(entry.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (entry.detail.isNotBlank()) {
            Text(entry.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

/** Sama polanya dengan formatChatTime di ChatScreen.kt, ditambah tanggal (log aktivitas bisa lebih dari sehari). */
private fun formatActivityLogTime(iso: String): String = try {
    val local = java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
    "%02d/%02d %02d:%02d".format(local.dayOfMonth, local.monthValue, local.hour, local.minute)
} catch (error: Exception) {
    ""
}

/** Ringkasan keluarga + aksi utama "Buat Tugas" (FAB bulat "+", gaya dompetdigitalku). */
@Composable
private fun ParentDashboardTab(
    state: UiState,
    approvalCount: Int,
    onCreateTask: () -> Unit,
    onDecide: (taskId: String, approved: Boolean, note: String) -> Unit,
    onSetLock: (childId: String, enabled: Boolean) -> Unit
) {
    // Pop-up review/follow-up cepat saat kartu ringkasan diklik - lihat DashboardStatCard(onClick).
    var cardModal by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.family?.name ?: "Keluarga", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
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
                    if (state.children.isEmpty()) {
                        Text(
                            "Belum ada profil anak. Tambah lewat ikon gerigi Pengaturan di kanan atas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardStatCard(modifier = Modifier.weight(1f), label = "Menunggu Approval", value = approvalCount.toString(), onClick = { cardModal = "approval" })
                DashboardStatCard(modifier = Modifier.weight(1f), label = "Anak", value = state.children.size.toString(), onClick = { cardModal = "children" })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardStatCard(modifier = Modifier.weight(1f), label = "Terkunci", value = state.children.count { it.lockModeEnabled }.toString(), onClick = { cardModal = "locked" })
                DashboardStatCard(modifier = Modifier.weight(1f), label = "Total Tugas", value = state.tasks.size.toString(), onClick = { cardModal = "tasks" })
            }

            if (state.children.isNotEmpty()) {
                IncompleteTasksByChildCard(children = state.children, tasks = state.tasks)
                DashboardChatPreviewCard(messages = state.dashboardChatPreview, currentUserId = state.currentUser?.id, children = state.children)
                // FAB (di bawah) menutupi bagian bawah - beri jarak supaya kartu terakhir tidak tertutup.
                Spacer(Modifier.height(64.dp))
            }
        }

        if (state.children.isNotEmpty()) {
            FloatingActionButton(
                onClick = onCreateTask,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Buat Tugas")
            }
        }
    }

    when (cardModal) {
        "approval" -> DashboardApprovalDialog(
            tasks = state.tasks.filter { it.status == "submitted" },
            children = state.children,
            token = state.token,
            loading = state.loading,
            onDecide = onDecide,
            onDismiss = { cardModal = null }
        )
        "children" -> DashboardChildrenDialog(children = state.children, tasks = state.tasks, onDismiss = { cardModal = null })
        "locked" -> DashboardLockedDialog(
            children = state.children.filter { it.lockModeEnabled },
            loading = state.loading,
            onSetLock = onSetLock,
            onDismiss = { cardModal = null }
        )
        "tasks" -> DashboardTasksDialog(tasks = state.tasks, children = state.children, token = state.token, onDismiss = { cardModal = null })
    }
}

@Composable
private fun DashboardApprovalDialog(
    tasks: List<TaskDto>,
    children: List<UserDto>,
    token: String?,
    loading: Boolean,
    onDecide: (String, Boolean, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Menunggu Approval") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tasks.isEmpty()) {
                    Text("Tidak ada tugas yang menunggu approval.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tasks.forEach { task ->
                        WaitingTaskCard(task = task, childName = children.find { it.id == task.childId }?.name, token = token, loading = loading, onDecide = onDecide)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun DashboardChildrenDialog(children: List<UserDto>, tasks: List<TaskDto>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anak") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (children.isEmpty()) {
                    Text("Belum ada profil anak.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    children.forEach { child ->
                        val incomplete = tasks.count { it.childId == child.id && it.status != "approved" }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(child.name, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (child.lockModeEnabled) {
                                    Text(
                                        "Terkunci",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    "$incomplete tugas tertunda",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun DashboardLockedDialog(children: List<UserDto>, loading: Boolean, onSetLock: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perangkat Terkunci") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (children.isEmpty()) {
                    Text("Tidak ada perangkat yang terkunci saat ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    children.forEach { child ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(child.name, fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = { onSetLock(child.id, false) }, enabled = !loading) { Text("Buka Kunci") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun DashboardTasksDialog(tasks: List<TaskDto>, children: List<UserDto>, token: String?, onDismiss: () -> Unit) {
    // Detail satu tugas dibuka DI ATAS pop-up ini (bukan menutupnya) - state lokal, terpisah
    // dari detailTaskId milik ParentScreen (tab Daftar Tugas), supaya tidak saling mengganggu.
    var detailTask by remember { mutableStateOf<TaskDto?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Semua Tugas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tasks.isEmpty()) {
                    Text("Belum ada tugas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tasks.forEach { task ->
                        TaskSummaryCard(task = task, childName = children.find { it.id == task.childId }?.name, onClick = { detailTask = task })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )

    val currentDetailTask = detailTask
    if (currentDetailTask != null) {
        TaskDetailDialog(
            task = currentDetailTask,
            childName = children.find { it.id == currentDetailTask.childId }?.name,
            token = token,
            onDismiss = { detailTask = null }
        )
    }
}

/** "Tugas belum selesai" = semua status kecuali approved, dipisah per anak - lihat renderDashboardIncompleteTasks di web/app.js (kode yang setara). */
@Composable
private fun IncompleteTasksByChildCard(children: List<UserDto>, tasks: List<TaskDto>) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Tugas Belum Selesai", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            children.forEach { child ->
                val incomplete = tasks.filter { it.childId == child.id && it.status != "approved" }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(child.name, fontWeight = FontWeight.SemiBold)
                    if (incomplete.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${incomplete.size} tertunda",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                if (incomplete.isEmpty()) {
                    Text(
                        "Semua tugas sudah selesai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    incomplete.forEach { task ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(task.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            StatusChip(task.status)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pratinjau 4 pesan terakhir grup keluarga - lihat renderDashboardChatPreview di web/app.js
 * (kode yang setara). Sengaja TIDAK private - dipakai ulang oleh ChildDashboardTab di
 * ChildScreen.kt (satu package "ui" yang sama), supaya tampilannya identik di kedua peran.
 */
@Composable
fun DashboardChatPreviewCard(messages: List<ChatMessageDto>, currentUserId: String?, children: List<UserDto>) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Percakapan Grup Terakhir", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (messages.isEmpty()) {
                Text(
                    "Belum ada percakapan di grup keluarga.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                messages.forEach { message ->
                    val senderLabel = when {
                        message.senderId == currentUserId -> "Kamu"
                        message.senderRole == "parent" -> "Orang Tua"
                        else -> children.find { it.id == message.senderId }?.name ?: "Anak"
                    }
                    val preview = if (message.type == "photo") "📷 Foto" else (message.text ?: "")
                    Text(
                        "$senderLabel: $preview",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(modifier: Modifier = Modifier, label: String, value: String, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Daftar semua tugas dengan filter dropdown (anak & status) - baris ringkas, klik untuk detail. */
@Composable
private fun ParentTaskListTab(
    state: UiState,
    statusFilter: String?,
    onSelectStatus: (String?) -> Unit,
    childFilter: String?,
    onSelectChild: (String?) -> Unit,
    onOpenDetail: (TaskDto) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TaskFilterRow(
            children = state.children,
            selectedChildId = childFilter,
            onSelectChild = onSelectChild,
            selectedStatus = statusFilter,
            onSelectStatus = onSelectStatus
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
                        onClick = { onOpenDetail(task) }
                    )
                }
            }
        }
    }
}

/** Tugas yang dikirim anak dan menunggu disetujui/ditolak. */
@Composable
private fun ParentApprovalTab(state: UiState, onDecide: (taskId: String, approved: Boolean, note: String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
    }
}

/**
 * Pemilih thread: grup "Semua Anak" (bersama semua anak + orang tua) selalu jadi pilihan
 * pertama, ditambah satu thread privat per anak. Selalu ditampilkan (bukan cuma kalau anak
 * lebih dari satu) karena bahkan dengan 1 anak, grup dan thread privat tetap dua riwayat
 * yang berbeda.
 */
@Composable
private fun ParentChatTab(
    state: UiState,
    selectedThreadId: String?,
    onSelectThread: (String) -> Unit,
    onRefreshUnread: () -> Unit
) {
    if (state.children.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Belum ada profil anak untuk diajak chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val activeThreadId = selectedThreadId ?: FAMILY_CHAT_THREAD_ID
    val options = listOf(FAMILY_CHAT_THREAD_ID to "Semua Anak") + state.children.map { it.id to it.name }
    Column(Modifier.fillMaxSize()) {
        ChatSubTabs(
            options = options,
            selectedId = activeThreadId,
            unreadByThread = state.chatUnreadByThread,
            onSelect = onSelectThread
        )
        ChatScreen(state = state, childId = activeThreadId, onRefreshUnread = onRefreshUnread)
    }
}

/** Kontrol Perangkat - saklar kunci per anak, sekarang tab sendiri jadi bisa ditampilkan penuh (tidak perlu expand/collapse lagi). */
@Composable
private fun ParentLockTab(children: List<UserDto>, loading: Boolean, onSetLock: (childId: String, enabled: Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (children.isEmpty()) {
            Text("Belum ada profil anak.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        val lockedCount = children.count { it.lockModeEnabled }
        Text(
            if (lockedCount == 0) "Tidak ada yang dikunci" else "$lockedCount dari ${children.size} anak dikunci",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (lockedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(children, key = { it.id }) { child ->
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(child.name, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = child.lockModeEnabled,
                            onCheckedChange = { onSetLock(child.id, it) },
                            enabled = !loading,
                            // Merah (bukan warna aksen oranye standar) saat TERKUNCI - kontras
                            // lebih tinggi & lebih jelas maknanya ("terkunci/dibatasi"), sama
                            // dengan perubahan warna toggle di web/app.css.
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.error,
                                checkedThumbColor = MaterialTheme.colorScheme.onError
                            )
                        )
                    }
                }
            }
        }
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
 * Galeri kecil semua berkas bukti satu tugas (bisa campuran foto & dokumen) - dipakai baik di
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
 * penuh layar); dokumen (PDF, Word, Excel, PowerPoint, TXT) ditampilkan sebagai ikon (diklik ->
 * diunduh ke cache lalu dibuka lewat aplikasi eksternal yang sesuai jenisnya (mis. pembaca PDF,
 * Word) yang sudah terpasang di HP, via FileProvider - lihat openDocumentExternally &
 * AndroidManifest.xml).
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

    val isImage = file.mime == "image/jpeg" || file.mime == "image/png"
    val currentBytes = bytes
    val bitmap = remember(file.id, currentBytes) {
        if (currentBytes != null && isImage) BitmapFactory.decodeByteArray(currentBytes, 0, currentBytes.size) else null
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(enabled = currentBytes != null) {
                if (isImage) showPreview = true else openDocumentExternally(context, currentBytes!!, file.id, file.mime)
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
            currentBytes != null && !isImage -> Icon(Icons.Default.Description, contentDescription = "Dokumen bukti tugas")
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
 * Menyimpan berkas dokumen (PDF, Word, Excel, PowerPoint, TXT) ke cache/evidence/ lalu
 * membukanya lewat aplikasi eksternal yang sesuai jenisnya (OS yang memilih berdasar mime -
 * pembaca PDF untuk PDF, Word/kompatibel untuk .docx, dst) yang sudah terpasang di HP - via
 * content:// resmi (FileProvider), bukan file:// langsung (diblokir sejak Android 7+). Kalau
 * tidak ada aplikasi yang cocok, tampilkan pesan singkat alih-alih membiarkan aplikasi crash.
 */
private fun openDocumentExternally(context: Context, bytes: ByteArray, fileId: String, mime: String) {
    try {
        val ext = EVIDENCE_MIME_EXT[mime] ?: "bin"
        val cacheDir = File(context.cacheDir, "evidence").apply { mkdirs() }
        val file = File(cacheDir, "$fileId.$ext")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (error: Exception) {
        Toast.makeText(context, "Tidak ada aplikasi yang bisa membuka jenis berkas ini di HP ini.", Toast.LENGTH_SHORT).show()
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
