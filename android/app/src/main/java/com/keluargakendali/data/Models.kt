package com.keluargakendali.data

/**
 * Model data yang mengikuti persis field JSON yang dikembalikan backend (server.js).
 * Jangan menambah/menghapus field tanpa mengecek ulang backend terlebih dahulu.
 */

data class UserDto(
    val id: String,
    val role: String, // "parent" atau "child"
    val name: String,
    val familyId: String,
    // Hanya berarti untuk role "child" - backend selalu mengirim false/tidak ada untuk orang tua.
    val lockModeEnabled: Boolean = false
)

data class FamilyDto(
    val id: String,
    val name: String,
    // Hanya terisi kalau yang meminta adalah orang tua; backend mengirim undefined untuk anak.
    val code: String? = null
)

/** Satu berkas bukti (foto atau dokumen) - byte-nya diambil terpisah lewat GET /tasks/:id/evidence/:fileId. */
data class EvidenceFileDto(
    val id: String,
    val mime: String // lihat EVIDENCE_MIME_EXT untuk daftar lengkap jenis yang didukung
)

/**
 * Jenis berkas bukti tugas yang didukung, dan ekstensi berkasnya - HARUS SAMA PERSIS dengan
 * EVIDENCE_MIME_EXT di server.js. Dipakai untuk: filter mime pemilih dokumen (SubmitEvidenceDialog
 * di ChildScreen), validasi jenis di klien sebelum kirim, dan nama berkas cache saat membuka
 * dokumen lewat aplikasi eksternal (openDocumentExternally di ParentScreen).
 */
val EVIDENCE_MIME_EXT: Map<String, String> = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "application/pdf" to "pdf",
    "application/msword" to "doc",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
    "application/vnd.ms-excel" to "xls",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
    "application/vnd.ms-powerpoint" to "ppt",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
    "text/plain" to "txt"
)

data class TaskDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val title: String,
    val description: String,
    val rewardMinutes: Int,
    val status: String, // "assigned" | "submitted" | "approved" | "rejected"
    val createdAt: String,
    val evidence: String? = null,
    val evidenceFiles: List<EvidenceFileDto> = emptyList(), // bisa beberapa foto/dokumen sekaligus
    val submittedAt: String? = null,
    val decisionNote: String? = null,
    val decidedAt: String? = null
)

data class AuthResult(
    val token: String,
    val user: UserDto,
    val family: FamilyDto? = null // hanya ada pada respons register-parent
)

data class AddChildResult(
    val child: UserDto,
    val familyCode: String
)

data class FamilyResult(
    val family: FamilyDto,
    val children: List<UserDto>
)

data class BalanceResult(
    val minutes: Int,
    val approvedTaskCount: Int,
    // Timestamp epoch ms sampai kapan Mode Kunci nonaktif (0 = tidak sedang aktif) - hasil
    // menukar saldo lewat POST /access-balance/redeem. Lihat DeviceLockService/ChildScreen.
    val unlockUntil: Long = 0L
)

/**
 * Thread key khusus untuk grup obrolan bersama SEMUA anggota keluarga (orang tua + semua
 * anak), selain thread privat satu-lawan-satu per anak (thread key-nya id anak itu sendiri).
 * Harus SAMA PERSIS dengan FAMILY_THREAD_KEY di server.js.
 */
const val FAMILY_CHAT_THREAD_ID = "family"

/**
 * Satu pesan chat. childId di sini sebenarnya "thread key" - bisa FAMILY_CHAT_THREAD_ID
 * (grup) atau id anak tertentu (privat orang tua<->anak itu saja). Byte foto TIDAK ikut di
 * sini, diambil terpisah lewat GET /chat/:childId/messages/:id/photo selagi photoAvailable
 * masih true (server cuma meneruskan sementara, lihat catatan CHAT_PHOTO_RETENTION_MS di
 * server.js - salinan permanennya ada di ChatPhotoCache lokal masing-masing perangkat).
 */
data class ChatMessageDto(
    val id: String,
    val childId: String,
    val senderId: String,
    val senderRole: String, // "parent" atau "child"
    val type: String, // "text" atau "photo"
    val text: String?,
    val photoMime: String?,
    val photoAvailable: Boolean,
    val createdAt: String
)

data class ChatThreadUnread(val childId: String, val unreadCount: Int)
data class ChatUnreadSummary(val total: Int, val threads: List<ChatThreadUnread>)

fun statusLabel(status: String): String = when (status) {
    "assigned" -> "Belum dikirim"
    "submitted" -> "Menunggu persetujuan"
    "approved" -> "Disetujui"
    "rejected" -> "Ditolak"
    else -> status
}

/**
 * Satu entri log aktivitas (lihat GET /activity-log & logActivity() di server.js) - riwayat
 * transparan tindakan orang tua & anak, dilihat orang tua lewat Pengaturan. `action` adalah
 * kode mentah dari server (lihat activityActionLabel di bawah), bukan teks siap-tampil.
 */
data class ActivityLogEntryDto(
    val id: String,
    val actorId: String,
    val actorRole: String, // "parent" atau "child"
    val actorName: String,
    val action: String,
    val detail: String,
    val createdAt: String
)

/** Label Indonesia untuk tiap kode `action` - HARUS sinkron dengan ACTIVITY_ACTION_LABEL di web/app.js. */
fun activityActionLabel(action: String): String = when (action) {
    "login" -> "Masuk ke akun"
    "child_added" -> "Menambahkan profil anak"
    "child_removed" -> "Menghapus profil anak"
    "child_pin_reset" -> "Mengatur ulang PIN anak"
    "device_locked" -> "Mengunci perangkat anak"
    "device_unlocked" -> "Membuka kunci perangkat anak"
    "task_created" -> "Membuat tugas baru"
    "task_submitted" -> "Mengirim bukti tugas"
    "task_approved" -> "Menyetujui tugas"
    "task_rejected" -> "Menolak tugas"
    "access_redeemed" -> "Menukar saldo menit jadi waktu akses"
    else -> action
}
