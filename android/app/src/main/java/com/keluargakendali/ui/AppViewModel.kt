package com.keluargakendali.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keluargakendali.R
import com.keluargakendali.data.ActivityLogEntryDto
import com.keluargakendali.data.ApiException
import com.keluargakendali.data.ChatMessageDto
import com.keluargakendali.data.FAMILY_CHAT_THREAD_ID
import com.keluargakendali.data.FamilyDto
import com.keluargakendali.data.PactioApi
import com.keluargakendali.data.SecureTokenStore
import com.keluargakendali.data.TaskDto
import com.keluargakendali.data.UserDto
import com.keluargakendali.service.LockStatusHint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val token: String? = null,
    val currentUser: UserDto? = null,
    val family: FamilyDto? = null,
    val children: List<UserDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList(),
    val balanceMinutes: Int = 0,
    val approvedTaskCount: Int = 0,
    // Timestamp epoch ms sampai kapan Mode Kunci nonaktif (0 = tidak sedang aktif) - lihat
    // ChildScreen (tombol "Gunakan Waktu") & DeviceLockService.
    val unlockUntil: Long = 0L,
    // Total pesan chat belum dibaca di semua thread - badge tab Chat. Isi pesan sendiri
    // TIDAK disimpan di sini (ChatScreen mengurus thread aktifnya sendiri lewat PactioApi
    // langsung, sama seperti EvidenceFileThumbnail di ParentScreen), cuma angka badge ini
    // yang perlu tetap "hidup" walau tab Chat sedang tidak aktif.
    val chatUnreadTotal: Int = 0,
    // Rincian belum-dibaca PER THREAD (childId thread -> jumlah) - dipakai badge di ChatSubTabs,
    // beda dari chatUnreadTotal yang cuma jumlah total untuk badge tab Chat itu sendiri.
    val chatUnreadByThread: Map<String, Int> = emptyMap(),
    // 4 pesan terakhir grup keluarga - pratinjau ringkas di Dashboard (orang tua & anak),
    // terpisah dari thread aktif ChatScreen sendiri, sama seperti dashboardChatPreview di
    // web/app.js. TIDAK menandai thread sebagai terbaca (murni pratinjau).
    val dashboardChatPreview: List<ChatMessageDto> = emptyList(),
    // Log aktivitas - HANYA terisi untuk orang tua (lihat loadActivityLog), dimuat sesuai
    // permintaan saat Pengaturan dibuka, bukan dipoll berkala (riwayat, bukan real-time).
    val activityLog: List<ActivityLogEntryDto> = emptyList()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = SecureTokenStore(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * ViewModel bukan @Composable - stringResource() tidak berlaku di sini. getApplication()
     * sudah otomatis mengembalikan Context dengan locale yang benar (lewat attachBaseContext di
     * MainActivity yang membungkus Application context sebelum Activity dibuat).
     */
    private fun str(resId: Int): String = getApplication<Application>().getString(resId)
    private fun str(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val token = tokenStore.loadToken()
        val user = tokenStore.loadUser()
        if (token != null && user != null) {
            _state.update { it.copy(token = token, currentUser = user) }
            refreshAll()
        }
    }

    fun registerParent(familyName: String, name: String, email: String, password: String) = launchGuarded {
        val result = PactioApi.registerParent(familyName, name, email, password)
        onAuthSuccess(result.token, result.user)
    }

    fun loginParent(email: String, password: String) = launchGuarded {
        val result = PactioApi.loginParent(email, password)
        onAuthSuccess(result.token, result.user)
    }

    fun loginChild(familyCode: String, pin: String) = launchGuarded {
        val result = PactioApi.loginChild(familyCode, pin)
        onAuthSuccess(result.token, result.user)
    }

    fun logout() {
        tokenStore.clear()
        _state.value = UiState()
    }

    /**
     * Dipanggil dari tombol "Keluar" saat yang login adalah ANAK - memverifikasi kata sandi
     * orang tua ke server dulu sebelum benar-benar logout (lihat verify-parent-password di
     * backend), supaya anak tidak bisa keluar dari akunnya sendiri sendirian (mis. untuk
     * lolos dari Mode Kunci) tanpa sepengetahuan orang tua.
     *
     * Sengaja TIDAK lewat launchGuarded biasa: errornya (kata sandi salah) harus muncul DI
     * DALAM dialog konfirmasi lewat onWrongPassword, bukan sebagai banner global yang bisa
     * bertabrakan dengan state layar di belakangnya.
     */
    fun confirmChildLogout(password: String, onWrongPassword: (String) -> Unit) {
        val token = _state.value.token ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                PactioApi.verifyParentPassword(token, password)
                logout()
            } catch (error: ApiException.Unauthorized) {
                // Sesi ANAK sendiri yang sudah tidak valid (bukan kata sandi orang tua salah -
                // itu dibalas 403 oleh server, ditangkap di cabang ApiException di bawah).
                tokenStore.clear()
                _state.value = UiState(errorMessage = str(R.string.error_session_expired))
            } catch (error: ApiException) {
                onWrongPassword(error.message ?: str(R.string.error_wrong_parent_password))
                _state.update { it.copy(loading = false) }
            } catch (error: Exception) {
                onWrongPassword(str(R.string.error_no_connection))
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun addChild(name: String, pin: String) = requireToken { token ->
        PactioApi.addChild(token, name, pin)
        loadFamily(token)
        _state.update { it.copy(infoMessage = str(R.string.info_child_added)) }
    }

    /** Dipanggil dari Pengaturan - tugas & foto bukti anak ini ikut terhapus di server. */
    fun deleteChild(childId: String) = requireToken { token ->
        PactioApi.deleteChild(token, childId)
        loadFamily(token)
        loadTasks(token)
        _state.update { it.copy(infoMessage = str(R.string.info_child_deleted)) }
    }

    /** Dipanggil dari Pengaturan saat anak lupa PIN - lihat catatan di PactioApi.resetChildPin. */
    fun resetChildPin(childId: String, pin: String) = requireToken { token ->
        PactioApi.resetChildPin(token, childId, pin)
        _state.update { it.copy(infoMessage = str(R.string.info_pin_reset)) }
        loadActivityLog()
    }

    /**
     * Dipanggil UI (ParentSettingsDialog) tiap kali bagian Log Aktivitas dibuka - sekali per
     * pembukaan, bukan polling, konsisten dengan ensureSettingsDataLoaded di web/app.js. Diam-diam
     * seperti silentRefresh - kegagalan jaringan sesekali tidak perlu jadi banner error yang
     * mengganggu Pengaturan.
     */
    fun loadActivityLog() {
        val token = _state.value.token ?: return
        if (_state.value.currentUser?.role != "parent") return
        viewModelScope.launch { runCatching { _state.update { it.copy(activityLog = PactioApi.getActivityLog(token)) } } }
    }

    fun createTask(childId: String, title: String, description: String, rewardMinutes: Int) = requireToken { token ->
        PactioApi.createTask(token, childId, title, description, rewardMinutes)
        loadTasks(token)
        _state.update { it.copy(infoMessage = str(R.string.info_task_created)) }
    }

    fun submitTask(taskId: String, evidence: String, evidenceFiles: List<String> = emptyList()) = requireToken { token ->
        PactioApi.submitTask(token, taskId, evidence, evidenceFiles)
        loadTasks(token)
        loadBalanceIfChild(token)
        _state.update { it.copy(infoMessage = str(R.string.info_task_submitted)) }
    }

    fun decideTask(taskId: String, approved: Boolean, note: String) = requireToken { token ->
        PactioApi.decideTask(token, taskId, approved, note)
        loadTasks(token)
        _state.update { it.copy(infoMessage = str(if (approved) R.string.info_task_approved else R.string.info_task_rejected)) }
    }

    /**
     * Anak menekan "Gunakan Waktu" - menukar sebagian/seluruh saldo menit hadiah jadi
     * jendela waktu Mode Kunci nonaktif. Balasan server (balance terbaru + unlockUntil)
     * langsung dipakai untuk update state, tidak perlu refresh terpisah.
     */
    fun redeemAccessBalance(minutes: Int) = requireToken { token ->
        val balance = PactioApi.redeemAccessBalance(token, minutes)
        // Beri tahu DeviceLockService SEKARANG JUGA (bukan menunggu siklus poll statusnya
        // sendiri yang bisa sampai ~15 detik) - lihat catatan di LockStatusHint.
        LockStatusHint.setUnlockUntil(balance.unlockUntil)
        _state.update {
            it.copy(
                balanceMinutes = balance.minutes,
                approvedTaskCount = balance.approvedTaskCount,
                unlockUntil = balance.unlockUntil,
                infoMessage = str(R.string.info_access_unlocked, minutes)
            )
        }
    }

    fun setChildLock(childId: String, enabled: Boolean) = requireToken { token ->
        PactioApi.setChildLock(token, childId, enabled)
        loadFamily(token)
        _state.update { it.copy(infoMessage = str(if (enabled) R.string.info_device_locked else R.string.info_device_unlocked)) }
    }

    /**
     * Dipanggil ChatScreen setelah menandai thread terbaca atau mengirim/menerima pesan,
     * supaya badge tab Chat langsung ter-update tanpa menunggu siklus silentRefresh 8 detik
     * berikutnya. Diam-diam sama seperti silentRefresh - tidak menyentuh loading/error.
     */
    fun refreshChatUnread() {
        val token = _state.value.token ?: return
        viewModelScope.launch { runCatching { loadChatUnread(token) } }
    }

    /**
     * Poll berkala di background (dipanggil dari MainActivity selama ada yang login) -
     * supaya tugas baru, status disetujui/ditolak, dsb dari PERANGKAT LAIN (bukan HP yang
     * sama) muncul otomatis tanpa perlu tekan refresh manual. Beda dengan LockStatusHint
     * yang cuma bisa "membisikkan" info instan DALAM proses yang sama di satu HP - untuk
     * info dari HP lain, satu-satunya cara adalah tanya ke server, makanya di-poll begini.
     *
     * Sengaja diam-diam: TIDAK menyentuh loading/errorMessage/infoMessage, supaya tidak
     * mengganggu kalau pengguna lagi mengisi form, dan kegagalan sesekali (mis. jaringan
     * hiccup) tidak perlu ditampilkan sebagai error - otomatis dicoba lagi siklus berikutnya.
     */
    fun silentRefresh() {
        val token = _state.value.token ?: return
        viewModelScope.launch {
            runCatching {
                loadFamily(token)
                loadTasks(token)
                loadBalanceIfChild(token)
                loadChatUnread(token)
                loadDashboardChatPreview(token)
            }
        }
    }

    fun dismissMessages() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun onAuthSuccess(token: String, user: UserDto) {
        tokenStore.save(token, user)
        _state.update { it.copy(token = token, currentUser = user) }
        refreshAll()
    }

    /**
     * Dipakai setelah login/register dan saat memulihkan sesi dari token tersimpan.
     * Sengaja lewat launchGuarded (bukan viewModelScope.launch polos) supaya kalau token
     * ternyata sudah tidak valid lagi di backend (mis. dihapus manual, atau akunnya dihapus)
     * errornya ditangani rapi (logout otomatis), bukan bikin aplikasi crash saat baru dibuka.
     */
    private fun refreshAll() = launchGuarded {
        val token = _state.value.token ?: return@launchGuarded
        loadFamily(token)
        loadTasks(token)
        loadBalanceIfChild(token)
        loadChatUnread(token)
        loadDashboardChatPreview(token)
    }

    private suspend fun loadFamily(token: String) {
        val result = PactioApi.getFamily(token)
        _state.update { it.copy(family = result.family, children = result.children) }
    }

    private suspend fun loadTasks(token: String) {
        val tasks = PactioApi.getTasks(token)
        _state.update { it.copy(tasks = tasks) }
    }

    private suspend fun loadBalanceIfChild(token: String) {
        if (_state.value.currentUser?.role != "child") return
        val balance = PactioApi.getAccessBalance(token)
        _state.update {
            it.copy(balanceMinutes = balance.minutes, approvedTaskCount = balance.approvedTaskCount, unlockUntil = balance.unlockUntil)
        }
    }

    private suspend fun loadChatUnread(token: String) {
        val summary = PactioApi.getChatUnreadSummary(token)
        _state.update {
            it.copy(
                chatUnreadTotal = summary.total,
                chatUnreadByThread = summary.threads.associate { thread -> thread.childId to thread.unreadCount }
            )
        }
    }

    /** 4 pesan terakhir grup keluarga, untuk pratinjau Dashboard - lihat komentar dashboardChatPreview di UiState. */
    private suspend fun loadDashboardChatPreview(token: String) {
        val messages = PactioApi.getChatMessages(token, FAMILY_CHAT_THREAD_ID)
        _state.update { it.copy(dashboardChatPreview = messages.takeLast(4)) }
    }

    /** Menjalankan aksi yang butuh token; tidak melakukan apa pun kalau belum login. */
    private fun requireToken(block: suspend (String) -> Unit) = launchGuarded {
        val token = _state.value.token ?: return@launchGuarded
        block(token)
    }

    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null, infoMessage = null) }
            try {
                block()
            } catch (error: ApiException.Unauthorized) {
                // Token ditolak backend (kedaluwarsa/tidak valid) -> paksa kembali ke layar login.
                tokenStore.clear()
                _state.value = UiState(errorMessage = str(R.string.error_session_expired))
                return@launch
            } catch (error: ApiException) {
                _state.update { it.copy(errorMessage = error.message ?: str(R.string.error_generic)) }
            } catch (error: Exception) {
                _state.update { it.copy(errorMessage = str(R.string.error_no_connection)) }
            }
            _state.update { it.copy(loading = false) }
        }
    }
}
