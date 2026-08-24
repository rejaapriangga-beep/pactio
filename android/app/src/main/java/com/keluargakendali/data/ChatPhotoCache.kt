package com.keluargakendali.data

import android.content.Context
import java.io.File

/**
 * Penyimpanan lokal permanen untuk foto chat - server HANYA meneruskan (relay) foto chat
 * sementara (lihat CHAT_PHOTO_RETENTION_MS di server.js), tidak menyimpannya selamanya.
 * Supaya pengirim & penerima tetap punya salinannya, ChatScreen menyimpan ke sini:
 * - pengirim: langsung dari byte yang baru diambil dari kamera/galeri, saat berhasil kirim.
 * - penerima: begitu berhasil mengambil sekali dari server (selagi photoAvailable masih true).
 *
 * Disimpan di penyimpanan privat aplikasi (filesDir) - bukan penyimpanan publik/galeri HP -
 * supaya tidak butuh izin penyimpanan tambahan dan otomatis terhapus kalau Pactio di-uninstall.
 */
object ChatPhotoCache {
    private fun dir(context: Context): File = File(context.filesDir, "chat_photos").apply { mkdirs() }
    private fun file(context: Context, messageId: String): File = File(dir(context), messageId)

    fun has(context: Context, messageId: String): Boolean = file(context, messageId).exists()

    fun read(context: Context, messageId: String): ByteArray? {
        val target = file(context, messageId)
        return if (target.exists()) target.readBytes() else null
    }

    fun save(context: Context, messageId: String, bytes: ByteArray) {
        file(context, messageId).writeBytes(bytes)
    }
}
