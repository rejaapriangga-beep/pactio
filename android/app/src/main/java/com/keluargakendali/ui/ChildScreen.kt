package com.keluargakendali.ui

import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.keluargakendali.data.TaskDto
import com.keluargakendali.service.AppForegroundState
import com.keluargakendali.service.DeviceLockPermissions
import com.keluargakendali.service.DeviceLockService
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

/** Jaring pengaman kalau hasil kamera tidak pernah kembali - lihat AppForegroundState.suppressLockFor. */
private const val CAMERA_LOCK_SUPPRESSION_MS = 120_000L

@Composable
fun ChildScreen(
    state: UiState,
    onSubmitTask: (taskId: String, evidence: String, evidencePhotoDataUri: String?) -> Unit,
    onRedeemBalance: (minutes: Int) -> Unit,
    onDismissMessage: () -> Unit
) {
    var submittingTask by remember { mutableStateOf<TaskDto?>(null) }
    var showRedeemDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        state.errorMessage?.let {
            ErrorBanner(it, onDismissMessage)
            Spacer(Modifier.height(12.dp))
        }

        DeviceLockController()

        AccessBalanceCard(
            balanceMinutes = state.balanceMinutes,
            approvedTaskCount = state.approvedTaskCount,
            unlockUntil = state.unlockUntil,
            onGunakanWaktu = { showRedeemDialog = true }
        )

        Spacer(Modifier.height(20.dp))
        Text("Tugas Kamu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))

        if (state.tasks.isEmpty()) {
            Text("Belum ada tugas dari orang tua.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.tasks, key = { it.id }) { task ->
                    ChildTaskCard(task = task, loading = state.loading, onKirim = { submittingTask = task })
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
            onSubmit = { evidence, photoDataUri ->
                onSubmitTask(current.id, evidence, photoDataUri)
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

@Composable
private fun SubmitEvidenceDialog(
    task: TaskDto,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (evidence: String, evidencePhotoDataUri: String?) -> Unit
) {
    var evidence by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }

    // Memanggil aplikasi kamera bawaan sistem lewat Activity Result API resmi Android —
    // TIDAK butuh izin CAMERA (didelegasikan ke aplikasi kamera), transparan buat pengguna
    // (dialog kamera asli yang tampil, bukan capture tersembunyi).
    //
    // suppressLockFor/clearSuppression: kalau Mode Kunci aktif, tanpa ini overlay kunci akan
    // ikut menutupi aplikasi Kamera juga begitu ia tampil di depan (karena bukan Pactio) —
    // padahal ini delegasi resmi dari Pactio sendiri, bukan anak membuka aplikasi lain.
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        AppForegroundState.clearSuppression()
        if (bitmap != null) photo = bitmap
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kirim \"${task.title}\" sebagai selesai") },
        text = {
            Column {
                Text(
                    "Ceritakan apa yang sudah kamu lakukan, dan foto sebagai bukti (opsional).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    evidence, { evidence = it },
                    label = { Text("Bukti selesai (teks)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                val currentPhoto = photo
                if (currentPhoto != null) {
                    Image(
                        bitmap = currentPhoto.asImageBitmap(),
                        contentDescription = "Pratinjau foto bukti",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = {
                        AppForegroundState.suppressLockFor(CAMERA_LOCK_SUPPRESSION_MS)
                        takePicture.launch(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (currentPhoto == null) "Ambil Foto" else "Ambil Ulang Foto")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(evidence.trim(), photo?.toJpegDataUri()) },
                enabled = !loading
            ) { Text("Kirim") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Dikompresi jadi JPEG kualitas 80 supaya ukuran data URI tetap wajar untuk dikirim sebagai JSON. */
private fun Bitmap.toJpegDataUri(): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 80, output)
    val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$base64"
}

/**
 * Menyalakan/mematikan DeviceLockService mengikuti status izin "Tampil di atas aplikasi
 * lain", dan menampilkan kartu ajakan izin kalau belum diberikan. Izin dicek ulang tiap 2
 * detik (bukan lewat lifecycle observer) supaya kembali dari halaman Pengaturan langsung
 * terdeteksi tanpa dependency lifecycle-compose tambahan.
 */
@Composable
private fun DeviceLockController() {
    val context = LocalContext.current
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

    if (!hasOverlay) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kontrol Perangkat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "Supaya orang tua bisa mengunci akses aplikasi lain kalau perlu, izinkan akses berikut lewat Pengaturan HP. Bisa dicabut kapan saja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Button(
                    onClick = { context.startActivity(DeviceLockPermissions.overlaySettingsIntent(context)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Izinkan Tampil di Atas Aplikasi Lain") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
