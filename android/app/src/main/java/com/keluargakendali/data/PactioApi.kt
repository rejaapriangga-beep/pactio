package com.keluargakendali.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Klien HTTP sederhana untuk API TimeCraft. Sengaja tidak memakai Retrofit/OkHttp/Gson —
 * backend tidak memakai dependency eksternal, jadi Android juga dijaga tetap ringan
 * memakai HttpsURLConnection (bawaan platform) dan org.json (bawaan Android SDK).
 *
 * PENTING: base URL wajib HTTPS produksi. Jangan pernah mengarahkan ke http://, localhost,
 * 127.0.0.1, atau 10.0.2.2 di sini.
 */
object PactioApi {

    private const val BASE_URL = "https://timecraft.my.id"
    private const val TIMEOUT_MS = 15_000

    suspend fun health(): Boolean = runCatching {
        val json = request("GET", "/health", token = null, body = null)
        json.optBoolean("ok", false)
    }.getOrDefault(false)

    suspend fun registerParent(familyName: String, name: String, email: String, password: String): AuthResult {
        val body = JSONObject()
            .put("familyName", familyName)
            .put("name", name)
            .put("email", email)
            .put("password", password)
        val json = request("POST", "/auth/register-parent", token = null, body = body)
        return AuthResult(
            token = json.getString("token"),
            user = json.getJSONObject("user").toUserDto(),
            family = json.optJSONObject("family")?.toFamilyDto()
        )
    }

    suspend fun loginParent(email: String, password: String): AuthResult {
        val body = JSONObject().put("email", email).put("password", password)
        val json = request("POST", "/auth/login-parent", token = null, body = body)
        return AuthResult(token = json.getString("token"), user = json.getJSONObject("user").toUserDto())
    }

    suspend fun loginChild(familyCode: String, pin: String): AuthResult {
        val body = JSONObject().put("familyCode", familyCode).put("pin", pin)
        val json = request("POST", "/auth/login-child", token = null, body = body)
        return AuthResult(token = json.getString("token"), user = json.getJSONObject("user").toUserDto())
    }

    suspend fun addChild(token: String, name: String, pin: String): AddChildResult {
        val body = JSONObject().put("name", name).put("pin", pin)
        val json = request("POST", "/family/children", token = token, body = body)
        return AddChildResult(
            child = json.getJSONObject("child").toUserDto(),
            familyCode = json.getString("familyCode")
        )
    }

    /** Dipanggil dari Pengaturan orang tua - lihat catatan di server.js soal apa saja yang ikut dibersihkan. */
    suspend fun deleteChild(token: String, childId: String) {
        request("DELETE", "/family/children/$childId", token = token, body = null)
    }

    /**
     * Reset PIN anak (mis. anak lupa PIN) - PIN lama (di-hash, tidak pernah bisa dibaca
     * ulang oleh siapa pun termasuk orang tua sendiri) langsung ditimpa PIN baru, dan semua
     * sesi login anak ini dicabut server. Lihat komentar di server.js.
     */
    suspend fun resetChildPin(token: String, childId: String, pin: String) {
        val body = JSONObject().put("pin", pin)
        request("POST", "/family/children/$childId/reset-pin", token = token, body = body)
    }

    suspend fun getFamily(token: String): FamilyResult {
        val json = request("GET", "/family", token = token, body = null)
        val childrenArray = json.getJSONArray("children")
        val children = (0 until childrenArray.length()).map { childrenArray.getJSONObject(it).toUserDto() }
        return FamilyResult(family = json.getJSONObject("family").toFamilyDto(), children = children)
    }

    suspend fun getTasks(token: String): List<TaskDto> {
        val json = request("GET", "/tasks", token = token, body = null)
        val array = json.getJSONArray("tasks")
        return (0 until array.length()).map { array.getJSONObject(it).toTaskDto() }
    }

    suspend fun createTask(token: String, childId: String, title: String, description: String, rewardMinutes: Int): TaskDto {
        val body = JSONObject()
            .put("childId", childId)
            .put("title", title)
            .put("description", description)
            .put("rewardMinutes", rewardMinutes)
        val json = request("POST", "/tasks", token = token, body = body)
        return json.getJSONObject("task").toTaskDto()
    }

    /**
     * evidenceFiles (opsional): daftar data URI base64, mis. "data:image/jpeg;base64,....",
     * "data:image/png;base64,....", atau "data:application/pdf;base64,....". Backend
     * memvalidasi isi tiap berkas lewat magic bytes, bukan cuma percaya string mime ini,
     * dan membatasi jumlah maksimal berkas per pengiriman.
     */
    suspend fun submitTask(token: String, taskId: String, evidence: String, evidenceFiles: List<String> = emptyList()): TaskDto {
        val body = JSONObject().put("evidence", evidence)
        if (evidenceFiles.isNotEmpty()) {
            val array = org.json.JSONArray()
            evidenceFiles.forEach { array.put(it) }
            body.put("evidenceFiles", array)
        }
        val json = request("POST", "/tasks/${taskId}/submit", token = token, body = body)
        return json.getJSONObject("task").toTaskDto()
    }

    /** Mengambil byte mentah satu berkas bukti tugas (foto/dokumen) lewat id-nya. */
    suspend fun getEvidenceFileBytes(token: String, taskId: String, fileId: String): ByteArray =
        getBytes(token, "/tasks/$taskId/evidence/$fileId", "Gagal memuat berkas bukti")

    /** Mengambil byte mentah satu foto chat - lihat catatan retensi di ChatMessageDto/server.js. */
    suspend fun getChatPhotoBytes(token: String, childId: String, messageId: String): ByteArray =
        getBytes(token, "/chat/$childId/messages/$messageId/photo", "Gagal memuat foto chat")

    /** Isi thread chat satu anak, terurut lama -> baru (maks 200 pesan terakhir - lihat server.js). */
    suspend fun getChatMessages(token: String, childId: String): List<ChatMessageDto> {
        val json = request("GET", "/chat/$childId/messages", token = token, body = null)
        val array = json.getJSONArray("messages")
        return (0 until array.length()).map { array.getJSONObject(it).toChatMessageDto() }
    }

    /** replyToId (opsional): id pesan lain di thread yang sama yang sedang dibalas - lihat ChatScreen.kt. */
    suspend fun sendChatText(token: String, childId: String, text: String, replyToId: String? = null): ChatMessageDto {
        val body = JSONObject().put("type", "text").put("text", text)
        if (replyToId != null) body.put("replyToId", replyToId)
        val json = request("POST", "/chat/$childId/messages", token = token, body = body)
        return json.getJSONObject("message").toChatMessageDto()
    }

    /** photoDataUri: "data:image/jpeg;base64,..." atau "data:image/png;base64,...". replyToId: lihat sendChatText. */
    suspend fun sendChatPhoto(token: String, childId: String, photoDataUri: String, replyToId: String? = null): ChatMessageDto {
        val body = JSONObject().put("type", "photo").put("photo", photoDataUri)
        if (replyToId != null) body.put("replyToId", replyToId)
        val json = request("POST", "/chat/$childId/messages", token = token, body = body)
        return json.getJSONObject("message").toChatMessageDto()
    }

    /**
     * Toggle reaksi SAYA dengan emoji ini pada satu pesan - satu pengguna cuma boleh punya satu
     * reaksi aktif per pesan (lihat komentar lengkap di server.js). emoji harus salah satu dari
     * CHAT_REACTION_EMOJI di Models.kt, kalau tidak server menolak dengan 400.
     */
    suspend fun reactToChatMessage(token: String, childId: String, messageId: String, emoji: String): ChatMessageDto {
        val body = JSONObject().put("emoji", emoji)
        val json = request("POST", "/chat/$childId/messages/$messageId/react", token = token, body = body)
        return json.getJSONObject("message").toChatMessageDto()
    }

    /** Menandai thread ini sudah dibaca sampai sekarang - lihat badge unread di tab Chat. */
    suspend fun markChatRead(token: String, childId: String) {
        request("POST", "/chat/$childId/read", token = token, body = JSONObject())
    }

    /** Log aktivitas keluarga - HANYA bisa dipanggil orang tua (backend menolak anak). Terbaru dulu. */
    suspend fun getActivityLog(token: String): List<ActivityLogEntryDto> {
        val json = request("GET", "/activity-log", token = token, body = null)
        val array = json.getJSONArray("entries")
        return (0 until array.length()).map { array.getJSONObject(it).toActivityLogEntryDto() }
    }

    suspend fun getChatUnreadSummary(token: String): ChatUnreadSummary {
        val json = request("GET", "/chat/unread-summary", token = token, body = null)
        val array = json.getJSONArray("threads")
        val threads = (0 until array.length()).map {
            val thread = array.getJSONObject(it)
            ChatThreadUnread(childId = thread.getString("childId"), unreadCount = thread.getInt("unreadCount"))
        }
        return ChatUnreadSummary(total = json.getInt("total"), threads = threads)
    }

    /** Dipakai getEvidenceFileBytes & getChatPhotoBytes - dua endpoint biner yang sama-sama butuh Authorization tapi bukan JSON. */
    private suspend fun getBytes(token: String, path: String, errorLabel: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = try {
            URL(BASE_URL + path).openConnection() as HttpsURLConnection
        } catch (error: IOException) {
            throw ApiException.Network("Tidak dapat menghubungi server: ${error.message}")
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = readStream(connection.errorStream)
                val json = if (errorBody.isBlank()) JSONObject() else parseJsonObject(errorBody)
                if (status == 401) throw ApiException.Unauthorized(json.optString("error", "Sesi tidak valid, silakan masuk kembali."))
                throw ApiException.Http(status, json.optString("error", "$errorLabel (HTTP $status)."))
            }
            connection.inputStream.use { it.readBytes() }
        } catch (error: ApiException) {
            throw error
        } catch (error: IOException) {
            throw ApiException.Network("Koneksi ke server gagal: ${error.message}")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun decideTask(token: String, taskId: String, approved: Boolean, note: String): TaskDto {
        val body = JSONObject().put("approved", approved).put("note", note)
        val json = request("POST", "/tasks/${taskId}/decision", token = token, body = body)
        return json.getJSONObject("task").toTaskDto()
    }

    suspend fun getAccessBalance(token: String): BalanceResult {
        val json = request("GET", "/access-balance", token = token, body = null)
        return json.toBalanceResult()
    }

    /**
     * Anak menukar sebagian/seluruh saldo menit hadiah jadi jendela waktu Mode Kunci
     * nonaktif ("Gunakan Waktu" di ChildScreen). Server yang jadi sumber kebenaran waktu
     * (unlockUntil) - lihat catatan di server.js kenapa dihitung wall-clock, bukan usage time.
     */
    suspend fun redeemAccessBalance(token: String, minutes: Int): BalanceResult {
        val body = JSONObject().put("minutes", minutes)
        val json = request("POST", "/access-balance/redeem", token = token, body = body)
        return json.toBalanceResult()
    }

    /** Dipoll berkala oleh DeviceLockService di perangkat anak. */
    suspend fun getLockStatus(token: String): Boolean {
        val json = request("GET", "/lock-status", token = token, body = null)
        return json.optBoolean("enabled", false)
    }

    /** Dipanggil orang tua lewat saklar Kontrol Perangkat di dashboard. */
    suspend fun setChildLock(token: String, childId: String, enabled: Boolean): UserDto {
        val body = JSONObject().put("enabled", enabled)
        val json = request("POST", "/children/$childId/lock", token = token, body = body)
        return json.getJSONObject("child").toUserDto()
    }

    /**
     * Dipanggil dari perangkat anak sebelum tombol "Keluar" benar-benar memproses logout —
     * lihat AppViewModel.confirmChildLogout & MainActivity.ChildLogoutDialog. Melempar
     * ApiException.Http (bukan Unauthorized) kalau kata sandinya salah, supaya TIDAK memicu
     * logout paksa "sesi berakhir" (lihat catatan di server.js kenapa endpoint ini sengaja
     * balas 403, bukan 401).
     */
    suspend fun verifyParentPassword(token: String, password: String) {
        val body = JSONObject().put("password", password)
        request("POST", "/children/verify-parent-password", token = token, body = body)
    }

    /**
     * Membuat backup terenkripsi (server yang mengenkripsi pakai `password` ini - lihat catatan
     * lengkap di server.js) dan mengembalikannya sebagai JSON mentah untuk disimpan apa adanya
     * ke penyimpanan yang dipilih orang tua lewat Storage Access Framework. `password` TIDAK
     * pernah dikirim ulang atau disimpan di Android - hanya dipakai sesaat untuk request ini.
     */
    suspend fun createBackup(token: String, password: String): BackupResult {
        val body = JSONObject().put("password", password)
        val json = request("POST", "/backup/create", token = token, body = body)
        return json.toBackupResult()
    }

    /**
     * Hapus SELURUH akun keluarga (orang tua + semua anak, tugas, chat, log aktivitas) secara
     * permanen - hanya bisa dipanggil orang tua, dan server minta konfirmasi ulang kata sandi
     * (lihat catatan lengkap di server.js). Setelah ini berhasil, token sesi ini sendiri sudah
     * tidak valid lagi - pemanggil (AppViewModel) harus langsung logout lokal, jangan coba
     * panggil endpoint lain dengan token yang sama.
     */
    suspend fun deleteAccount(token: String, password: String) {
        val body = JSONObject().put("password", password)
        request("DELETE", "/account", token = token, body = body)
    }

    private suspend fun request(method: String, path: String, token: String?, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = try {
                URL(BASE_URL + path).openConnection() as HttpsURLConnection
            } catch (error: IOException) {
                throw ApiException.Network("Tidak dapat menghubungi server: ${error.message}")
            }
            try {
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")

                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                }

                val status = connection.responseCode
                val rawBody = readStream(if (status in 200..299) connection.inputStream else connection.errorStream)
                val json = if (rawBody.isBlank()) JSONObject() else parseJsonObject(rawBody)

                when {
                    status == 401 -> throw ApiException.Unauthorized(json.optString("error", "Sesi tidak valid, silakan masuk kembali."))
                    status !in 200..299 -> throw ApiException.Http(status, json.optString("error", "Permintaan gagal (HTTP $status)."))
                    else -> json
                }
            } catch (error: ApiException) {
                throw error
            } catch (error: IOException) {
                throw ApiException.Network("Koneksi ke server gagal: ${error.message}")
            } finally {
                connection.disconnect()
            }
        }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun parseJsonObject(text: String): JSONObject = try {
        JSONObject(text)
    } catch (error: Exception) {
        throw ApiException.InvalidResponse("Respons server tidak dikenali.")
    }

    private fun JSONObject.toUserDto() = UserDto(
        id = getString("id"),
        role = getString("role"),
        name = getString("name"),
        familyId = getString("familyId"),
        lockModeEnabled = optBoolean("lockModeEnabled", false)
    )

    private fun JSONObject.toFamilyDto() = FamilyDto(
        id = getString("id"),
        name = getString("name"),
        code = if (has("code") && !isNull("code")) getString("code") else null
    )

    private fun JSONObject.toTaskDto() = TaskDto(
        id = getString("id"),
        familyId = getString("familyId"),
        childId = getString("childId"),
        title = getString("title"),
        description = optString("description", ""),
        rewardMinutes = getInt("rewardMinutes"),
        status = getString("status"),
        createdAt = getString("createdAt"),
        evidence = if (has("evidence") && !isNull("evidence")) getString("evidence") else null,
        evidenceFiles = optJSONArray("evidenceFiles")?.let { array ->
            (0 until array.length()).map { index ->
                val file = array.getJSONObject(index)
                EvidenceFileDto(id = file.getString("id"), mime = file.getString("mime"))
            }
        } ?: emptyList(),
        submittedAt = if (has("submittedAt") && !isNull("submittedAt")) getString("submittedAt") else null,
        decisionNote = if (has("decisionNote") && !isNull("decisionNote")) getString("decisionNote") else null,
        decidedAt = if (has("decidedAt") && !isNull("decidedAt")) getString("decidedAt") else null
    )

    private fun JSONObject.toChatMessageDto() = ChatMessageDto(
        id = getString("id"),
        childId = getString("childId"),
        senderId = getString("senderId"),
        senderRole = getString("senderRole"),
        type = getString("type"),
        text = if (has("text") && !isNull("text")) getString("text") else null,
        photoMime = if (has("photoMime") && !isNull("photoMime")) getString("photoMime") else null,
        photoAvailable = optBoolean("photoAvailable", false),
        replyToId = if (has("replyToId") && !isNull("replyToId")) getString("replyToId") else null,
        reactions = optJSONArray("reactions")?.let { array ->
            (0 until array.length()).map { index ->
                val reaction = array.getJSONObject(index)
                ChatReactionDto(userId = reaction.getString("userId"), emoji = reaction.getString("emoji"))
            }
        } ?: emptyList(),
        createdAt = getString("createdAt")
    )

    private fun JSONObject.toBackupResult() = BackupResult(
        format = getString("format"),
        salt = getString("salt"),
        iv = getString("iv"),
        tag = getString("tag"),
        ciphertext = getString("ciphertext")
    )

    private fun JSONObject.toActivityLogEntryDto() = ActivityLogEntryDto(
        id = getString("id"),
        actorId = getString("actorId"),
        actorRole = getString("actorRole"),
        actorName = getString("actorName"),
        action = getString("action"),
        detail = optString("detail", ""),
        createdAt = getString("createdAt")
    )

    private fun JSONObject.toBalanceResult() = BalanceResult(
        minutes = getInt("minutes"),
        approvedTaskCount = getInt("approvedTaskCount"),
        unlockUntil = optLong("unlockUntil", 0L)
    )
}
