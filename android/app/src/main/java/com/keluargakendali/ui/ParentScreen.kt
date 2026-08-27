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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.keluargakendali.R
import com.keluargakendali.data.ActivityLogEntryDto
import com.keluargakendali.data.ChatMessageDto
import com.keluargakendali.data.EVIDENCE_MIME_EXT
import com.keluargakendali.data.EvidenceFileDto
import com.keluargakendali.data.FAMILY_CHAT_THREAD_ID
import com.keluargakendali.data.PactioApi
import com.keluargakendali.data.SettingsStore
import com.keluargakendali.data.TaskDto
import com.keluargakendali.data.UserDto
import com.keluargakendali.data.activityActionLabel
import com.keluargakendali.data.statusLabel
import com.keluargakendali.ui.theme.pactioExtraColors
import kotlinx.coroutines.launch
import java.io.File

/**
 * Langkah-langkah tur coach-mark Dashboard orang tua (lihat TutorialOverlay.kt) - fungsi
 * terpisah (bukan langsung di dalam ParentScreen) supaya bisa dipanggil ulang dari MainActivity
 * saat menyusun aksi "Lihat Tutorial Lagi" di ParentSettingsDialog, tanpa perlu menembus balik
 * ke dalam ParentScreen. `key` di tiap langkah HARUS cocok dengan Modifier.tutorialTarget yang
 * dipasang di elemen terkait di bawah.
 */
@Composable
fun dashboardTutorialSteps(): List<TutorialStep> = listOf(
    TutorialStep("family_code", stringResource(R.string.tutorial_step_family_code)),
    TutorialStep("tab_row", stringResource(R.string.tutorial_step_tab_row)),
    TutorialStep("incomplete_tasks", stringResource(R.string.tutorial_step_incomplete_tasks)),
    TutorialStep("create_task_fab", stringResource(R.string.tutorial_step_create_task))
)

@Composable
fun ParentScreen(
    state: UiState,
    onDecide: (taskId: String, approved: Boolean, note: String) -> Unit,
    onSetLock: (childId: String, enabled: Boolean) -> Unit,
    onCreateTask: (childId: String, title: String, description: String, rewardMinutes: Int) -> Unit,
    onAddChild: (name: String, pin: String) -> Unit,
    onDismissMessage: () -> Unit,
    onRefreshChatUnread: () -> Unit,
    tutorialState: TutorialCoachMarkState
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
        TabItem(stringResource(R.string.tab_dashboard), Icons.Default.Dashboard),
        TabItem(stringResource(R.string.tab_tasks), Icons.Default.Checklist),
        TabItem(stringResource(R.string.tab_approval), Icons.Default.FactCheck, badgeCount = approvalCount),
        TabItem(stringResource(R.string.tab_chat), Icons.Default.Chat, badgeCount = state.chatUnreadTotal),
        TabItem(stringResource(R.string.tab_lock), Icons.Default.Lock)
    )

    Column(Modifier.fillMaxSize()) {
        state.errorMessage?.let {
            Box(Modifier.padding(16.dp)) { ErrorBanner(it, onDismissMessage) }
        }

        PactioTabRow(
            items = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.tutorialTarget("tab_row", tutorialState)
        )

        when (selectedTab) {
            0 -> ParentDashboardTab(
                state = state,
                approvalCount = approvalCount,
                onCreateTask = { showCreateTask = true },
                onDecide = onDecide,
                onSetLock = onSetLock,
                tutorialState = tutorialState
            )
            1 -> ParentTaskListTab(
                state = state,
                statusFilter = statusFilter,
                onSelectStatus = { statusFilter = it },
                childFilter = childFilter,
                onSelectChild = { childFilter = it },
                onOpenDetail = { detailTask = it },
                onCreateTask = { showCreateTask = true }
            )
            2 -> ParentApprovalTab(state = state, onDecide = onDecide)
            3 -> ParentChatTab(
                state = state,
                selectedThreadId = selectedChatThreadId,
                onSelectThread = { selectedChatThreadId = it },
                onRefreshUnread = onRefreshChatUnread
            )
            4 -> ParentLockTab(children = state.children, loading = state.loading, onSetLock = onSetLock, onAddChild = onAddChild)
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
    onDeleteChild: (childId: String) -> Unit,
    onResetPin: (childId: String, pin: String) -> Unit,
    onDeleteAccount: (password: String, onWrongPassword: (String) -> Unit) -> Unit,
    onChangePassword: (currentPassword: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onReplayTutorial: () -> Unit,
    onDismiss: () -> Unit
) {
    var childPendingDelete by remember { mutableStateOf<UserDto?>(null) }
    var childPendingResetPin by remember { mutableStateOf<UserDto?>(null) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_settings)) },
        text = {
            // Dialog ini sudah cukup panjang (profil anak + log aktivitas) - digulir supaya
            // tetap muat di layar kecil, konsisten dengan pola LazyColumn di layar lain.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.heading_account), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showChangePasswordDialog = true }) {
                        Text(stringResource(R.string.action_change_password))
                    }
                    // Tur coach-mark Dashboard - lihat TutorialOverlay.kt/dashboardTutorialSteps.
                    // Tombol ini cuma memicu ulang state-nya (di MainActivity), jadi tutup dulu
                    // dialog Pengaturan ini supaya sorotannya tidak ketutup dialog.
                    OutlinedButton(onClick = { onDismiss(); onReplayTutorial() }) {
                        Text(stringResource(R.string.action_replay_tutorial))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Panduan Penggunaan (GuideDialog.kt) - referensi teks lengkap untuk aksi yang
                // tersebar di banyak tab/dialog, beda dari tur coach-mark di atas yang cuma
                // menyorot Dashboard. Dibuka DI ATAS dialog Pengaturan ini (bukan menutupnya
                // dulu), boleh saja karena GuideDialog halaman penuh sendiri, bukan sorotan yang
                // butuh dialog Pengaturan ini tertutup seperti tur coach-mark.
                OutlinedButton(onClick = { showGuide = true }) {
                    Text(stringResource(R.string.action_open_guide))
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.heading_child_profiles), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.desc_child_pin_encrypted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (children.isEmpty()) {
                    Text(
                        stringResource(R.string.empty_no_children),
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
                                Icon(Icons.Default.Key, contentDescription = stringResource(R.string.cd_reset_pin_for, child.name))
                            }
                            IconButton(onClick = { childPendingDelete = child }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_for, child.name), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.hint_add_child_moved),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.heading_activity_log), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (activityLog.isEmpty()) {
                    Text(
                        stringResource(R.string.empty_no_activity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    activityLog.forEach { entry -> ActivityLogRow(entry) }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                UninstallProtectionSection()

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.heading_danger_zone),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(R.string.desc_delete_account),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete_account)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            loading = loading,
            onDismiss = { showDeleteAccountDialog = false },
            onConfirm = { password, onWrongPassword -> onDeleteAccount(password, onWrongPassword) }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            loading = loading,
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { currentPassword, newPassword, onError ->
                onChangePassword(currentPassword, newPassword, { showChangePasswordDialog = false }, onError)
            }
        )
    }

    if (showGuide) {
        GuideDialog(onDismiss = { showGuide = false })
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
            title = { Text(stringResource(R.string.title_delete_child_confirm, deleteTarget.name)) },
            text = {
                Text(stringResource(R.string.body_delete_child_confirm))
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteChild(deleteTarget.id); childPendingDelete = null },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { childPendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

/**
 * Proteksi anti-uninstall lewat Device Admin resmi Android - lihat catatan lengkap di
 * UninstallProtectionAdminReceiver.kt/AndroidManifest.xml kenapa ini AMAN sesuai batasan PRD
 * (transparan, lewat API resmi, tetap bisa dicabut siapa pun yang punya akses Settings sistem -
 * bukan mekanisme tersembunyi). Status dibaca ulang tiap kali dialog Pengaturan ini dibuka
 * (remember di sini, bukan disimpan di AppViewModel) - device admin murni status sistem
 * Android, bukan sesuatu yang perlu disinkronkan ke server.
 */
@Composable
private fun UninstallProtectionSection() {
    val context = LocalContext.current
    val devicePolicyManager = remember {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
    }
    val adminComponent = remember {
        android.content.ComponentName(context, com.keluargakendali.service.UninstallProtectionAdminReceiver::class.java)
    }
    var isActive by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponent)) }

    val explanation = stringResource(R.string.desc_device_admin_explanation)
    val activationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isActive = devicePolicyManager.isAdminActive(adminComponent)
    }

    Text(stringResource(R.string.heading_uninstall_protection), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.desc_uninstall_protection),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    Text(
        if (isActive) stringResource(R.string.status_uninstall_protection_active) else stringResource(R.string.status_uninstall_protection_inactive),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    if (!isActive) {
        Button(onClick = {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
            }
            activationLauncher.launch(intent)
        }) { Text(stringResource(R.string.action_enable_uninstall_protection)) }
    } else {
        Text(
            stringResource(R.string.hint_disable_uninstall_protection),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Cadangan data keluarga terenkripsi, diunduh ke penyimpanan yang orang tua pilih sendiri lewat
 * Storage Access Framework (bukan folder tersembunyi aplikasi) - lihat catatan lengkap enkripsi
 * di PactioApi.createBackup/server.js. Alurnya: password dulu -> panggil API -> baru buka
 * pemilih lokasi simpan (CreateDocument) begitu byte-nya sudah siap ditulis.
 *
 * Cuma satu pemicu: ikon di TopAppBar dashboard (BackupIconButton) - dulu ada juga versi kartu
 * lengkap di dalam dialog Pengaturan, tapi dihapus atas permintaan pengguna (duplikat, ikon
 * TopAppBar sudah cukup). BackupFlow tetap terpisah dari BackupIconButton (bukan digabung
 * langsung) supaya gampang ditambah pemicu lain lagi nanti kalau perlu.
 */
@Composable
fun BackupIconButton(token: String, familyName: String?) {
    BackupFlow(token = token, familyName = familyName) { onClick, loading ->
        IconButton(onClick = onClick, enabled = !loading) {
            Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.action_download_backup_encrypted))
        }
    }
}

@Composable
private fun BackupFlow(token: String, familyName: String?, trigger: @Composable (onClick: () -> Unit, loading: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var backupLoading by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var pendingBackupBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Disiapkan di sini (konteks composable), BUKAN langsung di dalam lambda callback di bawah -
    // stringResource() cuma boleh dipanggil langsung dari fungsi @Composable, sedangkan callback
    // ActivityResultLauncher/coroutine bukan konteks composable.
    val backupSavedMessage = stringResource(R.string.toast_backup_saved)
    val backupSaveFailedMessage = stringResource(R.string.toast_backup_save_failed)
    val backupCreateFailedMessage = stringResource(R.string.error_backup_create_failed)

    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = pendingBackupBytes
        pendingBackupBytes = null
        if (uri != null && bytes != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                .onSuccess { Toast.makeText(context, backupSavedMessage, Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, backupSaveFailedMessage, Toast.LENGTH_SHORT).show() }
        }
    }

    LaunchedEffect(pendingBackupBytes) {
        if (pendingBackupBytes != null) {
            val safeFamilyName = (familyName ?: "timecraft").lowercase().replace(Regex("[^a-z0-9]+"), "-")
            val dateStr = java.time.LocalDate.now().toString()
            createDocumentLauncher.launch("timecraft-backup-$safeFamilyName-$dateStr.json")
        }
    }

    trigger({ showPasswordDialog = true }, backupLoading)

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
                        .onFailure { backupError = it.message ?: backupCreateFailedMessage }
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
        title = { Text(stringResource(R.string.action_download_backup_encrypted)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.desc_backup_password),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(password, { password = it }, stringResource(R.string.label_backup_password), KeyboardType.Password)
                PasswordField(confirm, { confirm = it }, stringResource(R.string.label_repeat_password), KeyboardType.Password)
                if (mismatch) Text(stringResource(R.string.error_password_mismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(password) },
                enabled = !loading && password.length >= 8 && password == confirm
            ) { Text(stringResource(R.string.action_download_backup_short)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun ResetPinDialog(childName: String, loading: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cd_reset_pin_for, childName)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.body_reset_pin, childName),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, stringResource(R.string.label_new_pin), KeyboardType.NumberPassword)
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(pin) }, enabled = !loading && pin.length in 4..8) { Text(stringResource(R.string.action_save_new_pin)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/**
 * Ubah kata sandi - minta kata sandi LAMA (konfirmasi identitas, lihat catatan di
 * AppViewModel.changePassword/server.js) + kata sandi BARU (min 8 karakter, sama syaratnya
 * dengan pendaftaran). Error (kata sandi lama salah, dst) tampil DI DALAM dialog ini lewat
 * onError, sama pola dengan DeleteAccountDialog di bawah.
 */
@Composable
private fun ChangePasswordDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (currentPassword: String, newPassword: String, onError: (String) -> Unit) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_change_password)) },
        text = {
            Column {
                PasswordField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; error = null },
                    label = stringResource(R.string.label_current_password),
                    keyboardType = KeyboardType.Password
                )
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = stringResource(R.string.label_new_password),
                    keyboardType = KeyboardType.Password
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentPassword, newPassword) { message -> error = message } },
                enabled = !loading && currentPassword.isNotBlank() && newPassword.length >= 8
            ) { Text(stringResource(R.string.action_change_password)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/**
 * Konfirmasi hapus akun permanen - minta kata sandi orang tua sekali lagi (lihat catatan di
 * AppViewModel.deleteAccount/server.js kenapa ini wajib), pola errornya sama dengan
 * ChildLogoutDialog di MainActivity.kt & BackupPasswordDialog di atas: error tampil DI DALAM
 * dialog ini lewat callback onWrongPassword, bukan banner error global, supaya tidak
 * mengejutkan/menutup dialog begitu saja saat kata sandinya salah.
 */
@Composable
private fun DeleteAccountDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, onWrongPassword: (String) -> Unit) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_delete_account_confirm)) },
        text = {
            Column {
                Text(stringResource(R.string.body_delete_account_confirm), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = stringResource(R.string.parent_password_label),
                    keyboardType = KeyboardType.Password
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) { message -> error = message } },
                enabled = !loading && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.action_delete_account)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Satu baris Log Aktivitas - lihat activityActionLabel di Models.kt untuk pemetaan kode -> teks. */
@Composable
private fun ActivityLogRow(entry: ActivityLogEntryDto) {
    // actionText & roleLabel dihitung DI LUAR buildString - keduanya lewat stringResource(),
    // yang hanya boleh dipanggil dari konteks @Composable langsung, bukan dari dalam lambda
    // builder biasa seperti buildString { ... }.
    val roleLabel = if (entry.actorRole == "parent") stringResource(R.string.role_parent_suffix) else stringResource(R.string.role_child_suffix)
    val actionText = activityActionLabel(entry.action)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                buildString {
                    append(entry.actorName)
                    append(roleLabel)
                    append(actionText)
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
    onSetLock: (childId: String, enabled: Boolean) -> Unit,
    tutorialState: TutorialCoachMarkState
) {
    // Pop-up review/follow-up cepat saat kartu ringkasan diklik - lihat DashboardStatCard(onClick).
    var cardModal by remember { mutableStateOf<String?>(null) }

    // Tur coach-mark otomatis SEKALI per perangkat, begitu orang tua pertama kali melihat
    // Dashboard - lihat SettingsStore.hasSeenParentTutorial & dashboardTutorialSteps di atas.
    // Ditandai "sudah lihat" LANGSUNG saat mulai (bukan baru setelah selesai/dilewati) supaya
    // tidak muncul berulang tiap kali pindah tab lalu balik lagi ke Dashboard dalam sesi yang
    // sama. Bisa diulang manual lewat "Lihat Tutorial Lagi" di Pengaturan.
    val context = LocalContext.current
    val tutorialSteps = dashboardTutorialSteps()
    LaunchedEffect(Unit) {
        if (!SettingsStore.hasSeenParentTutorial(context)) {
            SettingsStore.setSeenParentTutorial(context, true)
            tutorialState.start(tutorialSteps)
        }
    }

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
                    Text(state.family?.name ?: stringResource(R.string.label_family_fallback), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    if (state.family?.code != null) {
                        Text(
                            state.family.code,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .tutorialTarget("family_code", tutorialState)
                        )
                    }
                    if (state.children.isEmpty()) {
                        Text(
                            stringResource(R.string.hint_no_children_add),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardStatCard(
                    modifier = Modifier.weight(1f), label = stringResource(R.string.stat_pending_approval), value = approvalCount.toString(),
                    icon = Icons.Default.FactCheck, accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = { cardModal = "approval" }
                )
                DashboardStatCard(
                    modifier = Modifier.weight(1f), label = stringResource(R.string.stat_children), value = state.children.size.toString(),
                    icon = Icons.Default.Group, accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { cardModal = "children" }
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardStatCard(
                    modifier = Modifier.weight(1f), label = stringResource(R.string.stat_locked), value = state.children.count { it.lockModeEnabled }.toString(),
                    icon = Icons.Default.Lock, accentColor = MaterialTheme.colorScheme.error,
                    onClick = { cardModal = "locked" }
                )
                DashboardStatCard(
                    modifier = Modifier.weight(1f), label = stringResource(R.string.stat_total_tasks), value = state.tasks.size.toString(),
                    icon = Icons.Default.Checklist, accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { cardModal = "tasks" }
                )
            }

            if (state.children.isNotEmpty()) {
                Box(Modifier.tutorialTarget("incomplete_tasks", tutorialState)) {
                    IncompleteTasksByChildCard(children = state.children, tasks = state.tasks)
                }
                DashboardChatPreviewCard(messages = state.dashboardChatPreview, currentUserId = state.currentUser?.id, children = state.children)
                // FAB (di bawah) menutupi bagian bawah - beri jarak supaya kartu terakhir tidak tertutup.
                Spacer(Modifier.height(64.dp))
            }
        }

        if (state.children.isNotEmpty()) {
            FloatingActionButton(
                onClick = onCreateTask,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).tutorialTarget("create_task_fab", tutorialState)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_create_task))
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
        title = { Text(stringResource(R.string.stat_pending_approval)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tasks.isEmpty()) {
                    Text(stringResource(R.string.empty_no_pending_approval), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tasks.forEach { task ->
                        WaitingTaskCard(task = task, childName = children.find { it.id == task.childId }?.name, token = token, loading = loading, onDecide = onDecide)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun DashboardChildrenDialog(children: List<UserDto>, tasks: List<TaskDto>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stat_children)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (children.isEmpty()) {
                    Text(stringResource(R.string.empty_no_children), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        stringResource(R.string.stat_locked),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    stringResource(R.string.count_pending_tasks, incomplete),
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun DashboardLockedDialog(children: List<UserDto>, loading: Boolean, onSetLock: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_locked_devices)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (children.isEmpty()) {
                    Text(stringResource(R.string.empty_no_locked_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    children.forEach { child ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.label_device_of_child, child.name), fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = { onSetLock(child.id, false) }, enabled = !loading) { Text(stringResource(R.string.action_unlock)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun DashboardTasksDialog(tasks: List<TaskDto>, children: List<UserDto>, token: String?, onDismiss: () -> Unit) {
    // Detail satu tugas dibuka DI ATAS pop-up ini (bukan menutupnya) - state lokal, terpisah
    // dari detailTaskId milik ParentScreen (tab Daftar Tugas), supaya tidak saling mengganggu.
    var detailTask by remember { mutableStateOf<TaskDto?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_all_tasks)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tasks.isEmpty()) {
                    Text(stringResource(R.string.empty_no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tasks.forEach { task ->
                        TaskSummaryCard(task = task, childName = children.find { it.id == task.childId }?.name, onClick = { detailTask = task })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
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
            Text(stringResource(R.string.heading_incomplete_tasks), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            children.forEachIndexed { index, child ->
                val incomplete = tasks.filter { it.childId == child.id && it.status != "approved" }
                if (index > 0) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(child.name, fontWeight = FontWeight.SemiBold)
                    if (incomplete.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.count_pending_short, incomplete.size),
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
                        stringResource(R.string.empty_all_tasks_done),
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
            Text(stringResource(R.string.heading_last_group_chat), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (messages.isEmpty()) {
                Text(
                    stringResource(R.string.empty_no_group_chat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                messages.forEach { message ->
                    val senderLabel = when {
                        message.senderId == currentUserId -> stringResource(R.string.label_you)
                        message.senderRole == "parent" -> stringResource(R.string.label_parent_role)
                        else -> children.find { it.id == message.senderId }?.name ?: stringResource(R.string.label_child_singular)
                    }
                    val preview = if (message.type == "photo") stringResource(R.string.label_photo_preview) else (message.text ?: "")
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

/** Ikon + warna aksen per kartu ringkasan - lebih hidup daripada kartu putih polos sebelumnya, sesuai permintaan "lebih berwarna". */
@Composable
private fun DashboardStatCard(modifier: Modifier = Modifier, label: String, value: String, icon: ImageVector, accentColor: Color, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accentColor)
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
    onOpenDetail: (TaskDto) -> Unit,
    onCreateTask: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
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
                Text(stringResource(R.string.empty_no_filtered_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 72.dp) // FAB (di bawah) tidak menutupi baris terakhir
                ) {
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

        // Sebelumnya cuma ada di Dashboard - dipindah/disalin ke sini juga supaya orang tua bisa
        // langsung buat tugas baru dari mana pun sedang melihat daftar tugas.
        if (state.children.isNotEmpty()) {
            FloatingActionButton(
                onClick = onCreateTask,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_create_task))
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
            Text(stringResource(R.string.empty_no_submitted_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(stringResource(R.string.empty_no_children_for_chat), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val activeThreadId = selectedThreadId ?: FAMILY_CHAT_THREAD_ID
    val options = listOf(FAMILY_CHAT_THREAD_ID to stringResource(R.string.label_all_children)) + state.children.map { it.id to it.name }
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
private fun ParentLockTab(
    children: List<UserDto>,
    loading: Boolean,
    onSetLock: (childId: String, enabled: Boolean) -> Unit,
    onAddChild: (name: String, pin: String) -> Unit
) {
    // Pindah ke sini dari Pengaturan - "Kunci Perangkat" adalah tempat orang tua paling sering
    // berurusan dengan daftar anak sehari-hari, jadi tambah profil anak baru lebih masuk akal di
    // sini. Reset PIN & Hapus profil TETAP di Pengaturan (aksi yang lebih jarang dipakai).
    var showAddChild by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (children.isEmpty()) {
            Text(
                stringResource(R.string.empty_no_children),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else {
            val lockedCount = children.count { it.lockModeEnabled }
            Text(
                if (lockedCount == 0) stringResource(R.string.label_none_locked) else stringResource(R.string.count_locked_of_total, lockedCount, children.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (lockedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(children, key = { it.id }) { child ->
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.label_device_of_child, child.name), fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = child.lockModeEnabled,
                                onCheckedChange = { onSetLock(child.id, it) },
                                enabled = !loading,
                                // Merah (bukan warna aksen oranye standar) saat TERKUNCI - kontras
                                // lebih tinggi & lebih jelas maknanya ("terkunci/dibatasi"), sama
                                // dengan perubahan warna toggle di web/app.css.
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.error,
                                    checkedThumbColor = MaterialTheme.colorScheme.onError,
                                    // Track "off" bawaan (outline pucat, sama dengan garis tepi kartu)
                                    // nyaris tidak kelihatan di atas latar - lihat pactioExtraColors.switchTrackOff.
                                    uncheckedTrackColor = MaterialTheme.pactioExtraColors.switchTrackOff,
                                    uncheckedBorderColor = MaterialTheme.pactioExtraColors.switchTrackOff,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedButton(onClick = { showAddChild = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add_child))
        }
    }

    if (showAddChild) {
        AddChildDialog(
            loading = loading,
            onDismiss = { showAddChild = false },
            onSubmit = { name, pin -> onAddChild(name, pin); showAddChild = false }
        )
    }
}

@Composable
private fun WaitingTaskCard(task: TaskDto, childName: String?, token: String?, loading: Boolean, onDecide: (String, Boolean, String) -> Unit) {
    var showRejectDialog by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(task.title, fontWeight = FontWeight.Bold)
            if (childName != null) Text(stringResource(R.string.label_child_colon, childName), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.label_reward_minutes, task.rewardMinutes), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            if (!task.evidence.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.label_evidence_colon, task.evidence), style = MaterialTheme.typography.bodyMedium)
            }
            if (task.evidenceFiles.isNotEmpty() && token != null) {
                Spacer(Modifier.height(8.dp))
                EvidenceGallery(token = token, task = task)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide(task.id, true, "") }, enabled = !loading, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_approve)) }
                OutlinedButton(onClick = { showRejectDialog = true }, enabled = !loading, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_reject)) }
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
                contentDescription = stringResource(R.string.cd_task_evidence_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            currentBytes != null && !isImage -> Icon(Icons.Default.Description, contentDescription = stringResource(R.string.cd_task_evidence_document))
            failed -> Icon(Icons.Default.ErrorOutline, contentDescription = stringResource(R.string.cd_failed_to_load), tint = MaterialTheme.colorScheme.error)
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
                contentDescription = stringResource(R.string.cd_evidence_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
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
        // Fungsi biasa (bukan @Composable) - stringResource() tidak berlaku di sini, pakai
        // context.getString() langsung.
        Toast.makeText(context, context.getString(R.string.error_no_app_for_file), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RejectTaskDialog(taskTitle: String, loading: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_reject_task, taskTitle)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.desc_reject_task),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    note, { note = it },
                    label = { Text(stringResource(R.string.label_note_for_child)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(note.trim()) }, enabled = !loading) { Text(stringResource(R.string.action_reject_task)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
                    (if (childName != null) "$childName · " else "") + stringResource(R.string.count_minutes_short, task.rewardMinutes),
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
                if (childName != null) Text(stringResource(R.string.label_child_colon, childName), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.label_reward_minutes, task.rewardMinutes), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                if (task.description.isNotBlank()) {
                    Text(task.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (!task.evidence.isNullOrBlank()) {
                    Text(stringResource(R.string.label_evidence_text_colon, task.evidence), style = MaterialTheme.typography.bodyMedium)
                }
                if (task.evidenceFiles.isNotEmpty() && token != null) {
                    Text(stringResource(R.string.label_evidence_files_colon), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    EvidenceGallery(token = token, task = task, thumbnailSize = 88.dp)
                }
                if (task.status == "rejected" && !task.decisionNote.isNullOrBlank()) {
                    Text(stringResource(R.string.label_parent_note_colon, task.decisionNote), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
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
        val allChildrenLabel = stringResource(R.string.label_all_children)
        val allStatusesLabel = stringResource(R.string.label_all_statuses)
        if (children.size > 1) {
            FilterDropdown(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.label_child_singular),
                selectedLabel = children.find { it.id == selectedChildId }?.name ?: allChildrenLabel,
                options = listOf<Pair<String?, String>>(null to allChildrenLabel) + children.map { it.id to it.name },
                onSelect = onSelectChild
            )
        }
        FilterDropdown(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.label_status),
            selectedLabel = selectedStatus?.let { statusLabel(it) } ?: allStatusesLabel,
            options = listOf<Pair<String?, String>>(null to allStatusesLabel) + statuses.map { it to statusLabel(it) },
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
        title = { Text(stringResource(R.string.title_add_child_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_child_name)) }, modifier = Modifier.fillMaxWidth())
                PasswordField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, stringResource(R.string.label_pin_4to8), KeyboardType.NumberPassword)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name.trim(), pin) },
                enabled = !loading && name.isNotBlank() && pin.length in 4..8
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
        title = { Text(stringResource(R.string.title_create_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedChild?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_child_singular)) },
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
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.label_task_title)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.label_description_optional)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    rewardText, { rewardText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.label_reward_minutes_field)) },
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
            ) { Text(stringResource(R.string.action_create_task)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
