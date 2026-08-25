package com.keluargakendali.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keluargakendali.R
import com.keluargakendali.data.EVIDENCE_MIME_EXT
import com.keluargakendali.data.FAMILY_CHAT_THREAD_ID
import com.keluargakendali.data.TaskDto
import com.keluargakendali.service.AppForegroundState
import com.keluargakendali.service.DeviceLockPermissions
import com.keluargakendali.service.DeviceLockService
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

/** Jaring pengaman kalau kamera/pemilih berkas somehow tidak pernah kembali hasilnya - lihat AppForegroundState.suppressLockFor. */
private const val PICKER_LOCK_SUPPRESSION_MS = 120_000L

/** Sama dengan MAX_EVIDENCE_FILES di backend (server.js) - dicek juga di sini supaya anak dapat pesan langsung, bukan menunggu server menolak. */
private const val MAX_EVIDENCE_FILES = 5

/** Sama dengan MAX_EVIDENCE_FILE_BYTES di backend. */
private const val MAX_EVIDENCE_FILE_BYTES = 5 * 1024 * 1024

@Composable
fun ChildScreen(
    state: UiState,
    onSubmitTask: (taskId: String, evidence: String, evidenceFiles: List<String>) -> Unit,
    onRedeemBalance: (minutes: Int) -> Unit,
    onDismissMessage: () -> Unit,
    onRefreshChatUnread: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var submittingTask by remember { mutableStateOf<TaskDto?>(null) }
    var showRedeemDialog by remember { mutableStateOf(false) }
    var selectedChatThreadId by remember { mutableStateOf<String?>(null) }

    // Kontrol Perangkat: nyala/matikan DeviceLockService mengikuti status izin "Tampil di atas
    // aplikasi lain" - dihoist ke SINI (bukan di dalam konten tab "Kunci Perangkat") supaya
    // layanannya tetap berjalan terlepas dari tab mana yang sedang aktif; kalau ini ditaruh di
    // dalam badan tab, pindah ke tab lain akan ikut mematikan Mode Kunci tanpa sepengetahuan
    // siapa pun - jelas bertentangan dengan tujuan fitur ini.
    var hasOverlay by remember { mutableStateOf(DeviceLockPermissions.hasOverlayPermission(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            hasOverlay = DeviceLockPermissions.hasOverlayPermission(context)
            delay(2000)
        }
    }
    LaunchedEffect(hasOverlay) {
        val intent = Intent(context, DeviceLockService::class.java)
        if (hasOverlay) context.startForegroundService(intent) else context.stopService(intent)
    }
    DisposableEffect(Unit) {
        onDispose { context.stopService(Intent(context, DeviceLockService::class.java)) }
    }

    // Pengaturan sengaja TIDAK ikut sebagai tab - dipindah jadi ikon gerigi di TopAppBar
    // (lihat MainActivity), tepat di sebelah kiri "Keluar", supaya 4 tab ini muat satu baris.
    val tabs = listOf(
        TabItem(stringResource(R.string.tab_dashboard), Icons.Default.Dashboard),
        TabItem(stringResource(R.string.tab_tasks), Icons.Default.Checklist),
        TabItem(stringResource(R.string.tab_chat), Icons.Default.Chat, badgeCount = state.chatUnreadTotal),
        TabItem(stringResource(R.string.tab_lock), Icons.Default.Lock)
    )

    Column(Modifier.fillMaxSize()) {
        state.errorMessage?.let {
            Box(Modifier.padding(16.dp)) { ErrorBanner(it, onDismissMessage) }
        }

        PactioTabRow(items = tabs, selectedIndex = selectedTab, onSelect = { selectedTab = it })

        when (selectedTab) {
            0 -> ChildDashboardTab(state = state, onGunakanWaktu = { showRedeemDialog = true })
            1 -> ChildTaskListTab(state = state, onKirim = { submittingTask = it })
            2 -> ChildChatTab(
                state = state,
                selectedThreadId = selectedChatThreadId,
                onSelectThread = { selectedChatThreadId = it },
                onRefreshUnread = onRefreshChatUnread
            )
            3 -> ChildLockTab(hasOverlay = hasOverlay, onOpenSettings = { context.startActivity(DeviceLockPermissions.overlaySettingsIntent(context)) })
        }
    }

    val current = submittingTask
    if (current != null) {
        SubmitEvidenceDialog(
            task = current,
            loading = state.loading,
            onDismiss = { submittingTask = null },
            onSubmit = { evidence, files ->
                onSubmitTask(current.id, evidence, files)
                submittingTask = null
            }
        )
    }

    if (showRedeemDialog) {
        RedeemMinutesDialog(
            balanceMinutes = state.balanceMinutes,
            loading = state.loading,
            onDismiss = { showRedeemDialog = false },
            onConfirm = { minutes ->
                onRedeemBalance(minutes)
                showRedeemDialog = false
            }
        )
    }
}

/** Saldo akses + ringkasan singkat jumlah tugas per status, supaya anak langsung tahu status keseluruhannya begitu buka aplikasi. */
@Composable
private fun ChildDashboardTab(state: UiState, onGunakanWaktu: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AccessBalanceCard(
            balanceMinutes = state.balanceMinutes,
            approvedTaskCount = state.approvedTaskCount,
            unlockUntil = state.unlockUntil,
            onGunakanWaktu = onGunakanWaktu
        )
        val belumDikirim = state.tasks.count { it.status == "assigned" || it.status == "rejected" }
        val menunggu = state.tasks.count { it.status == "submitted" }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChildStatCard(
                modifier = Modifier.weight(1f), label = stringResource(R.string.stat_not_submitted), value = belumDikirim.toString(),
                icon = Icons.Default.Checklist, accentColor = MaterialTheme.colorScheme.primary
            )
            ChildStatCard(
                modifier = Modifier.weight(1f), label = stringResource(R.string.stat_pending_approval), value = menunggu.toString(),
                icon = Icons.Default.HourglassTop, accentColor = MaterialTheme.colorScheme.secondary
            )
        }

        // state.tasks (anak) sudah otomatis hanya berisi tugas milik anak ini sendiri (lihat
        // taskForUser di server.js), jadi tidak perlu filter per-anak lagi di sini seperti
        // versi orang tua (IncompleteTasksByChildCard).
        val incomplete = state.tasks.filter { it.status != "approved" }
        ChildIncompleteTasksCard(incomplete)
        DashboardChatPreviewCard(messages = state.dashboardChatPreview, currentUserId = state.currentUser?.id, children = state.children)
    }
}

/** "Tugas belum selesai" milik anak ini sendiri - lihat renderDashboardIncompleteTasks di web/app.js (untuk orang tua, kode yang setara). */
@Composable
private fun ChildIncompleteTasksCard(incomplete: List<TaskDto>) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.heading_incomplete_tasks), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (incomplete.isEmpty()) {
                Text(
                    stringResource(R.string.empty_all_tasks_done_child),
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

/** Ikon + warna aksen - sama pola dengan DashboardStatCard di ParentScreen.kt (lebih hidup daripada kartu putih polos). */
@Composable
private fun ChildStatCard(modifier: Modifier = Modifier, label: String, value: String, icon: ImageVector, accentColor: Color) {
    Card(
        modifier = modifier,
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

@Composable
private fun ChildTaskListTab(state: UiState, onKirim: (TaskDto) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (state.tasks.isEmpty()) {
            Text(stringResource(R.string.empty_no_tasks_from_parent), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.tasks, key = { it.id }) { task ->
                    ChildTaskCard(task = task, loading = state.loading, onKirim = { onKirim(task) })
                }
            }
        }
    }
}

/**
 * Pemilih thread: grup "Keluarga (Semua)" bersama orang tua + semua saudara, atau thread
 * privat cuma dengan orang tua. Selalu ditampilkan (dua pilihan tetap ada walau anak ini anak
 * tunggal, karena grup & privat tetap dua riwayat berbeda).
 */
@Composable
private fun ChildChatTab(
    state: UiState,
    selectedThreadId: String?,
    onSelectThread: (String) -> Unit,
    onRefreshUnread: () -> Unit
) {
    val myId = state.currentUser?.id ?: return
    val activeThreadId = selectedThreadId ?: FAMILY_CHAT_THREAD_ID
    val options = listOf(
        FAMILY_CHAT_THREAD_ID to stringResource(R.string.label_family_all),
        myId to stringResource(R.string.label_private_with_parent)
    )
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

/** Status izin Mode Kunci - anak cuma bisa MELIHAT status & memberi izin, mengaktifkan/mematikan kuncinya sendiri tetap wewenang orang tua. */
@Composable
private fun ChildLockTab(hasOverlay: Boolean, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.heading_device_lock), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (hasOverlay) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (hasOverlay) stringResource(R.string.label_permission_granted) else stringResource(R.string.label_permission_not_granted),
                    fontWeight = FontWeight.Bold,
                    color = if (hasOverlay) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    if (hasOverlay) stringResource(R.string.desc_lock_permission_granted) else stringResource(R.string.desc_lock_permission_not_granted),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasOverlay) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (!hasOverlay) {
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_allow_overlay))
                    }
                }
            }
        }
        Text(
            stringResource(R.string.desc_lock_child_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Info akun singkat - tidak banyak yang bisa diubah anak sendiri, sebagian besar pengaturan
 * ada di tangan orang tua. Dipanggil dari MainActivity lewat ikon gerigi di TopAppBar (bukan
 * tab lagi - lihat catatan di ChildScreen), jadi dialog ini PUBLIC dan berdiri sendiri.
 */
@Composable
fun ChildSettingsDialog(state: UiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.label_name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.currentUser?.name ?: "-", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.label_family_fallback), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.family?.name ?: "-", fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    stringResource(R.string.desc_logout_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

/**
 * Kartu saldo + tombol "Gunakan Waktu" untuk menukar saldo jadi jendela Mode Kunci
 * nonaktif (lihat AppViewModel.redeemAccessBalance & POST /access-balance/redeem).
 * Kalau sedang ada jendela aktif (unlockUntil di masa depan), tampilkan hitung mundurnya -
 * dihitung ulang tiap detik dari wall-clock lokal, server tetap sumber kebenarannya (dicek
 * ulang tiap kali layar dibuka/disegarkan lewat GET /access-balance & /lock-status).
 */
@Composable
private fun AccessBalanceCard(
    balanceMinutes: Int,
    approvedTaskCount: Int,
    unlockUntil: Long,
    onGunakanWaktu: () -> Unit
) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(unlockUntil) {
        while (unlockUntil > System.currentTimeMillis()) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
        nowMillis = System.currentTimeMillis()
    }
    val remainingMs = (unlockUntil - nowMillis).coerceAtLeast(0)
    val unlockActive = remainingMs > 0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.heading_access_balance), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                stringResource(R.string.count_minutes_short, balanceMinutes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                stringResource(R.string.label_from_approved_tasks, approvedTaskCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (unlockActive) {
                val totalSeconds = remainingMs / 1000
                val timeText = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
                Text(
                    stringResource(R.string.label_access_active_remaining, timeText),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onGunakanWaktu,
                enabled = balanceMinutes > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (unlockActive) stringResource(R.string.action_add_time) else stringResource(R.string.action_use_time)) }
        }
    }
}

/** Dialog pilih jumlah menit yang mau ditukar dari saldo - lihat AccessBalanceCard. */
@Composable
private fun RedeemMinutesDialog(
    balanceMinutes: Int,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit
) {
    var input by remember { mutableStateOf(balanceMinutes.toString()) }
    val minutes = input.toIntOrNull()
    val valid = minutes != null && minutes in 1..balanceMinutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_use_time)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.desc_redeem_balance, balanceMinutes),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.label_minutes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { minutes?.let(onConfirm) }, enabled = !loading && valid) { Text(stringResource(R.string.action_use)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun ChildTaskCard(task: TaskDto, loading: Boolean, onKirim: () -> Unit) {
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
            Text(stringResource(R.string.label_reward_minutes, task.rewardMinutes), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            if (task.status == "rejected" && !task.decisionNote.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.label_parent_note_colon, task.decisionNote), style = MaterialTheme.typography.bodySmall)
            }
            if (task.status == "assigned" || task.status == "rejected") {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onKirim, enabled = !loading, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_submit_as_done))
                }
            }
        }
    }
}

/** Satu lampiran yang sudah dibaca ke memori & siap dikirim (foto dari kamera atau berkas dari pemilih dokumen). */
private data class PendingAttachment(
    val dataUri: String,
    val previewBitmap: Bitmap?, // null untuk PDF - ditampilkan dengan ikon dokumen, bukan pratinjau gambar
    val label: String
)

/** Nama tampilan berkas dari content:// URI (mis. "laporan.pdf") - dipakai untuk label lampiran non-foto. */
private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}

/**
 * Membaca satu berkas yang dipilih lewat Storage Access Framework (OpenMultipleDocuments) jadi
 * PendingAttachment siap-kirim. Mengembalikan null (dan memanggil [onError]) kalau jenis
 * berkasnya tidak didukung atau ukurannya melebihi batas - supaya anak dapat pesan yang jelas,
 * bukan gagal diam-diam atau baru ditolak di server.
 */
private fun Uri.readAsAttachment(context: Context, onError: (String) -> Unit): PendingAttachment? {
    // Fungsi biasa (bukan @Composable) - stringResource() tidak berlaku, pakai context.getString().
    val mime = context.contentResolver.getType(this)
    if (mime == null || !EVIDENCE_MIME_EXT.containsKey(mime)) {
        onError(context.getString(R.string.error_unsupported_file_type))
        return null
    }
    val bytes = context.contentResolver.openInputStream(this)?.use { it.readBytes() }
    if (bytes == null || bytes.isEmpty()) {
        onError(context.getString(R.string.error_file_unreadable))
        return null
    }
    if (bytes.size > MAX_EVIDENCE_FILE_BYTES) {
        onError(context.getString(R.string.error_file_too_large))
        return null
    }
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    val isImage = mime == "image/jpeg" || mime == "image/png"
    val bitmap = if (isImage) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
    val label = queryDisplayName(context, this) ?: context.getString(R.string.label_file_fallback)
    return PendingAttachment(dataUri = "data:$mime;base64,$base64", previewBitmap = bitmap, label = label)
}

@Composable
private fun SubmitEvidenceDialog(
    task: TaskDto,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (evidence: String, evidenceFiles: List<String>) -> Unit
) {
    val context = LocalContext.current
    var evidence by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<PendingAttachment>()) }
    var pickError by remember { mutableStateOf<String?>(null) }
    val atLimit = attachments.size >= MAX_EVIDENCE_FILES

    // Memanggil aplikasi kamera bawaan sistem lewat Activity Result API resmi Android —
    // TIDAK butuh izin CAMERA (didelegasikan ke aplikasi kamera), transparan buat pengguna
    // (dialog kamera asli yang tampil, bukan capture tersembunyi).
    //
    // suppressLockFor/clearSuppression: kalau Mode Kunci aktif, tanpa ini overlay kunci akan
    // ikut menutupi aplikasi Kamera juga begitu ia tampil di depan (karena bukan Pactio) —
    // padahal ini delegasi resmi dari Pactio sendiri, bukan anak membuka aplikasi lain.
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        AppForegroundState.clearSuppression()
        if (bitmap != null && attachments.size < MAX_EVIDENCE_FILES) {
            // Callback ActivityResultLauncher, bukan konteks @Composable - pakai context.getString().
            attachments = attachments + PendingAttachment(bitmap.toJpegDataUri(), bitmap, context.getString(R.string.label_photo_camera))
            pickError = null
        }
    }

    // Pemilih dokumen resmi Android (Storage Access Framework) — bisa pilih beberapa berkas
    // sekaligus tanpa butuh izin penyimpanan runtime apa pun, dan anak yang secara eksplisit
    // memilih berkasnya sendiri lewat UI sistem (bukan Pactio mengakses penyimpanan diam-diam).
    val pickDocuments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        AppForegroundState.clearSuppression()
        val room = MAX_EVIDENCE_FILES - attachments.size
        val toRead = uris.take(room)
        val newAttachments = mutableListOf<PendingAttachment>()
        var error: String? = null
        for (uri in toRead) {
            uri.readAsAttachment(context) { message -> error = message }?.let { newAttachments.add(it) }
        }
        if (uris.size > room) error = context.getString(R.string.error_max_evidence_files, MAX_EVIDENCE_FILES)
        attachments = attachments + newAttachments
        pickError = error
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_submit_task, task.title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.desc_submit_evidence, MAX_EVIDENCE_FILES),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    evidence, { evidence = it },
                    label = { Text(stringResource(R.string.label_evidence_text)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                if (attachments.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachments.forEachIndexed { index, attachment ->
                            AttachmentThumbnail(attachment) {
                                attachments = attachments.toMutableList().also { it.removeAt(index) }
                                pickError = null
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                pickError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                if (atLimit) {
                    Text(
                        stringResource(R.string.hint_max_evidence_reached, MAX_EVIDENCE_FILES),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            AppForegroundState.suppressLockFor(PICKER_LOCK_SUPPRESSION_MS)
                            takePicture.launch(null)
                        },
                        enabled = !atLimit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_take_photo))
                    }
                    OutlinedButton(
                        onClick = {
                            AppForegroundState.suppressLockFor(PICKER_LOCK_SUPPRESSION_MS)
                            pickDocuments.launch(EVIDENCE_MIME_EXT.keys.toTypedArray())
                        },
                        enabled = !atLimit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_pick_file))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(evidence.trim(), attachments.map { it.dataUri }) },
                enabled = !loading
            ) { Text(stringResource(R.string.action_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Satu kotak pratinjau kecil di strip lampiran, dengan tombol hapus bulat di pojok kanan atas. */
@Composable
private fun AttachmentThumbnail(attachment: PendingAttachment, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(72.dp)) {
        val bitmap = attachment.previewBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = attachment.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Description, contentDescription = attachment.label)
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(22.dp).align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_attachment),
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Dikompresi jadi JPEG kualitas 80 supaya ukuran data URI tetap wajar untuk dikirim sebagai JSON. */
private fun Bitmap.toJpegDataUri(): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 80, output)
    val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$base64"
}
