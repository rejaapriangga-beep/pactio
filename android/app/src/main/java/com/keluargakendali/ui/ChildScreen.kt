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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        TabItem("Dashboard", Icons.Default.Dashboard),
        TabItem("Tugas", Icons.Default.Checklist),
        TabItem("Chat", Icons.Default.Chat, badgeCount = state.chatUnreadTotal),
        TabItem("Kunci", Icons.Default.Lock)
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AccessBalanceCard(
            balanceMinutes = state.balanceMinutes,
            approvedTaskCount = state.approvedTaskCount,
            unlockUntil = state.unlockUntil,
            onGunakanWaktu = onGunakanWaktu
        )
        val belumDikirim = state.tasks.count { it.status == "assigned" || it.status == "rejected" }
        val menunggu = state.tasks.count { it.status == "submitted" }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChildStatCard(modifier = Modifier.weight(1f), label = "Belum Dikirim", value = belumDikirim.toString())
            ChildStatCard(modifier = Modifier.weight(1f), label = "Menunggu Approval", value = menunggu.toString())
        }
    }
}

@Composable
private fun ChildStatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChildTaskListTab(state: UiState, onKirim: (TaskDto) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (state.tasks.isEmpty()) {
            Text("Belum ada tugas dari orang tua.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val options = listOf<Pair<String?, String>>(
        FAMILY_CHAT_THREAD_ID to "Keluarga (Semua)",
        myId to "Pribadi dengan Orang Tua"
    )
    Column(Modifier.fillMaxSize()) {
        FilterDropdown(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            label = "Percakapan",
            selectedLabel = options.find { it.first == activeThreadId }?.second ?: "Keluarga (Semua)",
            options = options,
            onSelect = { value -> value?.let(onSelectThread) }
        )
        ChatScreen(state = state, childId = activeThreadId, onRefreshUnread = onRefreshUnread)
    }
}

/** Status izin Mode Kunci - anak cuma bisa MELIHAT status & memberi izin, mengaktifkan/mematikan kuncinya sendiri tetap wewenang orang tua. */
@Composable
private fun ChildLockTab(hasOverlay: Boolean, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Kunci Perangkat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (hasOverlay) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (hasOverlay) "Izin diberikan" else "Izin belum diberikan",
                    fontWeight = FontWeight.Bold,
                    color = if (hasOverlay) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    if (hasOverlay) {
                        "Orang tua bisa mengunci akses ke aplikasi lain kalau perlu. Kamu bisa mencabut izin ini kapan saja lewat Pengaturan HP."
                    } else {
                        "Supaya orang tua bisa mengunci akses aplikasi lain kalau perlu, izinkan akses berikut lewat Pengaturan HP. Bisa dicabut kapan saja."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasOverlay) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (!hasOverlay) {
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Izinkan Tampil di Atas Aplikasi Lain")
                    }
                }
            }
        }
        Text(
            "Kunci/buka kunci perangkat hanya bisa diatur oleh orang tua. Kamu tetap bisa memakai saldo menit hadiah untuk membuka akses sementara lewat tab Dashboard.",
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
        title = { Text("Pengaturan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nama", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.currentUser?.name ?: "-", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Keluarga", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.family?.name ?: "-", fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "Untuk keluar dari akun ini, gunakan tombol \"Keluar\" di pojok kanan atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
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
                Text("Saldo Akses Hadiah", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                "$balanceMinutes menit",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                "dari $approvedTaskCount tugas disetujui",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (unlockActive) {
                val totalSeconds = remainingMs / 1000
                Text(
                    "Akses aktif · sisa %d:%02d".format(totalSeconds / 60, totalSeconds % 60),
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
            ) { Text(if (unlockActive) "Tambah Waktu" else "Gunakan Waktu") }
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
        title = { Text("Gunakan Waktu") },
        text = {
            Column {
                Text(
                    "Saldo kamu $balanceMinutes menit. Berapa menit mau dipakai sekarang? Sisanya tetap tersimpan.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    label = { Text("Menit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { minutes?.let(onConfirm) }, enabled = !loading && valid) { Text("Gunakan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
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
            Text("Hadiah: ${task.rewardMinutes} menit akses", color = MaterialTheme.colorScheme.primary)
            if (task.status == "rejected" && !task.decisionNote.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Catatan orang tua: ${task.decisionNote}", style = MaterialTheme.typography.bodySmall)
            }
            if (task.status == "assigned" || task.status == "rejected") {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onKirim, enabled = !loading, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Kirim sebagai selesai")
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
    val mime = context.contentResolver.getType(this)
    if (mime == null || !EVIDENCE_MIME_EXT.containsKey(mime)) {
        onError("Jenis berkas tidak didukung (harus JPEG, PNG, PDF, Word, Excel, PowerPoint, atau TXT).")
        return null
    }
    val bytes = context.contentResolver.openInputStream(this)?.use { it.readBytes() }
    if (bytes == null || bytes.isEmpty()) {
        onError("Berkas tidak dapat dibaca.")
        return null
    }
    if (bytes.size > MAX_EVIDENCE_FILE_BYTES) {
        onError("Berkas terlalu besar (maksimal 5MB per berkas).")
        return null
    }
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    val isImage = mime == "image/jpeg" || mime == "image/png"
    val bitmap = if (isImage) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
    val label = queryDisplayName(context, this) ?: "Berkas"
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
            attachments = attachments + PendingAttachment(bitmap.toJpegDataUri(), bitmap, "Foto")
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
        if (uris.size > room) error = "Maksimal $MAX_EVIDENCE_FILES berkas bukti."
        attachments = attachments + newAttachments
        pickError = error
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kirim \"${task.title}\" sebagai selesai") },
        text = {
            Column {
                Text(
                    "Ceritakan apa yang sudah kamu lakukan, dan lampirkan foto/dokumen sebagai bukti (opsional, sampai $MAX_EVIDENCE_FILES berkas).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    evidence, { evidence = it },
                    label = { Text("Bukti selesai (teks)") },
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
                        "Maksimal $MAX_EVIDENCE_FILES berkas tercapai.",
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
                        Text("Ambil Foto")
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
                        Text("Pilih Berkas")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(evidence.trim(), attachments.map { it.dataUri }) },
                enabled = !loading
            ) { Text("Kirim") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
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
                contentDescription = "Hapus lampiran",
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
