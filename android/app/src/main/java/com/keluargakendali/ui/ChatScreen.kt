package com.keluargakendali.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keluargakendali.data.CHAT_REACTION_EMOJI
import com.keluargakendali.data.ChatMessageDto
import com.keluargakendali.data.ChatPhotoCache
import com.keluargakendali.data.PactioApi
import com.keluargakendali.service.AppForegroundState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId

/** Jaring pengaman untuk kamera/galeri chat, sama alasannya dengan PICKER_LOCK_SUPPRESSION_MS di ChildScreen. */
private const val CHAT_PICKER_LOCK_SUPPRESSION_MS = 120_000L

/**
 * Layar chat satu thread (satu anak, orang tua<->anak). Dipakai baik dari ParentScreen (dengan
 * pemilih anak di atasnya kalau anak lebih dari satu) maupun ChildScreen (childId = akun anak
 * itu sendiri). Isi pesan diambil/dikirim langsung lewat PactioApi (bukan lewat AppViewModel) -
 * sama seperti pola EvidenceFileThumbnail di ParentScreen - supaya AppViewModel tidak perlu tahu
 * detail satu thread yang sedang dibuka; cuma badge total belum-dibaca (chatUnreadTotal) yang
 * dititipkan ke state global lewat onRefreshUnread, karena itu harus tetap terlihat walau tab
 * Chat sedang tidak aktif.
 *
 * Foto TIDAK disimpan permanen di server (lihat catatan di ChatMessageDto/server.js) - begitu
 * terkirim/diterima, byte-nya disalin ke ChatPhotoCache lokal supaya kedua pihak tetap punya
 * salinannya sendiri walau server sudah menghapusnya.
 */
@Composable
fun ChatScreen(state: UiState, childId: String, onRefreshUnread: () -> Unit) {
    val token = state.token
    val myId = state.currentUser?.id
    if (token == null || myId == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember(childId) { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var input by remember(childId) { mutableStateOf("") }
    var sendError by remember(childId) { mutableStateOf<String?>(null) }
    var sending by remember(childId) { mutableStateOf(false) }
    // Pesan yang sedang dibalas (reply) - null kalau tidak sedang membalas apa pun. Lihat
    // renderChatTab/chatReplyTarget di web/app.js untuk pola yang setara.
    var replyTarget by remember(childId) { mutableStateOf<ChatMessageDto?>(null) }
    // Id pesan yang popover pilihan emoji reaksinya sedang terbuka - satu popover aktif dalam satu waktu.
    var reactionPickerMessageId by remember(childId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun sendPhoto(bitmap: Bitmap) {
        sending = true
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        val bytes = output.toByteArray()
        val dataUri = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        runCatching { PactioApi.sendChatPhoto(token, childId, dataUri, replyTarget?.id) }
            .onSuccess { sent ->
                ChatPhotoCache.save(context, sent.id, bytes)
                messages = messages + sent
                sendError = null
                replyTarget = null
            }
            .onFailure { sendError = "Gagal mengirim foto." }
        sending = false
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        AppForegroundState.clearSuppression()
        if (bitmap != null) scope.launch { sendPhoto(bitmap) }
    }
    // Photo Picker resmi Android (PickVisualMedia) - tidak butuh izin penyimpanan runtime,
    // hanya menampilkan galeri sistem, transparan sesuai batasan PRD.
    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        AppForegroundState.clearSuppression()
        if (uri != null) {
            scope.launch {
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bitmap == null) sendError = "Gagal membaca foto yang dipilih." else sendPhoto(bitmap)
            }
        }
    }

    // Poll thread tiap 4 detik selagi layar ini aktif - lebih cepat dari siklus silentRefresh
    // 8 detik global karena chat perlu terasa lebih "hidup". Tandai terbaca segera saat dibuka
    // (bukan menunggu siklus poll pertama), dan lagi setiap kali ada pesan MASUK baru (bukan
    // pesan yang kita kirim sendiri).
    LaunchedEffect(childId, token) {
        runCatching { PactioApi.markChatRead(token, childId) }
        onRefreshUnread()
        while (true) {
            val previousIds = messages.map { it.id }.toSet()
            runCatching { PactioApi.getChatMessages(token, childId) }.onSuccess { fetched ->
                messages = fetched
                val hasNewIncoming = fetched.any { it.id !in previousIds && it.senderId != myId }
                if (hasNewIncoming) {
                    runCatching { PactioApi.markChatRead(token, childId) }
                    onRefreshUnread()
                }
            }
            delay(4000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada percakapan. Kirim pesan pertama!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    // Nama BEDA dari state replyTarget di atas dengan sengaja - kalau sama, akan
                    // membayangi (shadow) state var-nya di dalam lambda ini, dan onReplyRequested
                    // di bawah jadi tidak bisa lagi meng-assign-nya (val vs var).
                    val quotedMessage = message.replyToId?.let { id -> messages.find { it.id == id } }
                    ChatBubble(
                        message = message,
                        isMine = message.senderId == myId,
                        senderName = senderLabelFor(message, state),
                        replyToLabel = quotedMessage?.let { senderLabelFor(it, state) },
                        replyToPreview = quotedMessage?.let { if (it.type == "photo") "📷 Foto" else (it.text ?: "") },
                        myUserId = myId,
                        reactionPickerOpen = reactionPickerMessageId == message.id,
                        context = context,
                        token = token,
                        childId = childId,
                        onReplyRequested = { replyTarget = message },
                        onToggleReactionPicker = {
                            reactionPickerMessageId = if (reactionPickerMessageId == message.id) null else message.id
                        },
                        onReact = { emoji ->
                            reactionPickerMessageId = null
                            scope.launch {
                                runCatching { PactioApi.reactToChatMessage(token, childId, message.id, emoji) }
                                    .onSuccess { updated -> messages = messages.map { if (it.id == updated.id) updated else it } }
                                    .onFailure { sendError = "Gagal memberi reaksi." }
                            }
                        }
                    )
                }
            }
        }

        sendError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        replyTarget?.let { target ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val preview = if (target.type == "photo") "📷 Foto" else (target.text ?: "")
                Text(
                    "Membalas ${senderLabelFor(target, state)}: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { replyTarget = null }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Batal balas")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    AppForegroundState.suppressLockFor(CHAT_PICKER_LOCK_SUPPRESSION_MS)
                    takePicture.launch(null)
                },
                enabled = !sending
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Kirim foto dari kamera")
            }
            IconButton(
                onClick = {
                    AppForegroundState.suppressLockFor(CHAT_PICKER_LOCK_SUPPRESSION_MS)
                    pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !sending
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Kirim foto dari galeri")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Tulis pesan...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isEmpty()) return@IconButton
                    input = ""
                    sending = true
                    val replyToId = replyTarget?.id
                    scope.launch {
                        runCatching { PactioApi.sendChatText(token, childId, text, replyToId) }
                            .onSuccess { sent -> messages = messages + sent; sendError = null; replyTarget = null }
                            .onFailure { sendError = "Gagal mengirim pesan." }
                        sending = false
                    }
                },
                enabled = !sending && input.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Kirim")
            }
        }
    }
}

/**
 * Nama pengirim satu pesan - "Kamu" untuk pesan sendiri (dipakai di kutipan balasan, lihat
 * replyTarget di atas), atau "Orang Tua"/nama anak untuk pesan orang lain (dipakai sebagai label
 * di atas bubble non-mine, penting di thread grup yang bisa lebih dari 2 peserta).
 */
private fun senderLabelFor(message: ChatMessageDto, state: UiState): String {
    if (message.senderId == state.currentUser?.id) return "Kamu"
    if (message.senderRole == "parent") return "Orang Tua"
    return state.children.find { it.id == message.senderId }?.name ?: "Anak"
}

@Composable
private fun ChatBubble(
    message: ChatMessageDto,
    isMine: Boolean,
    senderName: String,
    replyToLabel: String?,
    replyToPreview: String?,
    myUserId: String,
    reactionPickerOpen: Boolean,
    context: Context,
    token: String,
    childId: String,
    onReplyRequested: () -> Unit,
    onToggleReactionPicker: () -> Unit,
    onReact: (String) -> Unit
) {
    val onBubbleColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
        ) {
            if (!isMine) {
                Text(
                    senderName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
            }

            // Kutipan pesan yang dibalas - diam-diam dilewati kalau target tidak ditemukan di
            // riwayat yang sudah dimuat (mis. di luar 200 pesan terakhir), cuma pemanis tampilan.
            if (replyToLabel != null && replyToPreview != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(onBubbleColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(replyToLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = onBubbleColor)
                    Text(replyToPreview, style = MaterialTheme.typography.labelSmall, color = onBubbleColor, maxLines = 2)
                }
            }

            if (message.type == "photo") {
                ChatPhotoContent(context = context, token = token, childId = childId, message = message)
            } else {
                Text(message.text ?: "", color = onBubbleColor)
            }
            Text(
                formatChatTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = onBubbleColor.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
            )

            // Reaksi yang sudah ada, dikelompokkan per emoji - tap pill = toggle reaksi SAYA
            // dengan emoji itu (jalan pintas, sama efeknya dengan pilih dari popover di bawah).
            if (message.reactions.isNotEmpty()) {
                val grouped = message.reactions.groupBy({ it.emoji }, { it.userId })
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    grouped.forEach { (emoji, userIds) ->
                        val mine = userIds.contains(myUserId)
                        AssistChip(
                            onClick = { onReact(emoji) },
                            label = { Text("$emoji ${userIds.size}", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else onBubbleColor.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }

            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReplyRequested, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("↩ Balas", style = MaterialTheme.typography.labelSmall, color = onBubbleColor.copy(alpha = 0.85f))
                }
                TextButton(onClick = onToggleReactionPicker, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("🙂 Reaksi", style = MaterialTheme.typography.labelSmall, color = onBubbleColor.copy(alpha = 0.85f))
                }
            }

            if (reactionPickerOpen) {
                Row(
                    Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CHAT_REACTION_EMOJI.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onReact(emoji) }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Konten satu pesan foto: cek cache lokal dulu (ChatPhotoCache) - kalau sudah ada, tidak perlu
 * ke server sama sekali. Kalau belum ada dan masih photoAvailable, ambil sekali dari server lalu
 * simpan ke cache. Kalau sudah tidak photoAvailable (lewat masa retensi) dan tidak ada di cache
 * lokal (mis. pesan lama dari sebelum instal ulang), tampilkan placeholder yang jujur.
 */
@Composable
private fun ChatPhotoContent(context: Context, token: String, childId: String, message: ChatMessageDto) {
    var bitmap by remember(message.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(message.id) { mutableStateOf(false) }
    var showPreview by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        val cached = ChatPhotoCache.read(context, message.id)
        if (cached != null) {
            bitmap = BitmapFactory.decodeByteArray(cached, 0, cached.size)
            return@LaunchedEffect
        }
        if (!message.photoAvailable) {
            failed = true
            return@LaunchedEffect
        }
        runCatching { PactioApi.getChatPhotoBytes(token, childId, message.id) }
            .onSuccess { bytes ->
                ChatPhotoCache.save(context, message.id, bytes)
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            .onFailure { failed = true }
    }

    val current = bitmap
    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = current != null) { showPreview = true },
        contentAlignment = Alignment.Center
    ) {
        when {
            current != null -> Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Foto chat",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, contentDescription = null)
                Text("Foto tidak tersedia", style = MaterialTheme.typography.labelSmall)
            }
            else -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }

    if (showPreview && current != null) {
        ChatImagePreviewDialog(bitmap = current, onDismiss = { showPreview = false })
    }
}

@Composable
private fun ChatImagePreviewDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Pratinjau foto chat",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

private fun formatChatTime(iso: String): String = try {
    val local = Instant.parse(iso).atZone(ZoneId.systemDefault())
    "%02d:%02d".format(local.hour, local.minute)
} catch (error: Exception) {
    ""
}
