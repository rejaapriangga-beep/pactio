package com.keluargakendali.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keluargakendali.data.ApiException
import com.keluargakendali.data.FamilyDto
import com.keluargakendali.data.PactioApi
import com.keluargakendali.data.SecureTokenStore
import com.keluargakendali.data.TaskDto
import com.keluargakendali.data.UserDto
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
    val approvedTaskCount: Int = 0
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = SecureTokenStore(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

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

    /** Satu aksi untuk login MAUPUN daftar otomatis — backend yang memutuskan mana yang cocok. */
    fun loginWithGoogle(idToken: String) = launchGuarded {
        val result = PactioApi.loginWithGoogle(idToken)
        onAuthSuccess(result.token, result.user)
    }

    /** Dipakai saat pengambilan akun Google gagal di lapisan UI (Credential Manager), sebelum sempat memanggil backend. */
    fun reportError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun logout() {
        tokenStore.clear()
        _state.value = UiState()
    }

    fun addChild(name: String, pin: String) = requireToken { token ->
        PactioApi.addChild(token, name, pin)
        loadFamily(token)
        _state.update { it.copy(infoMessage = "Profil anak berhasil ditambahkan.") }
    }

    fun createTask(childId: String, title: String, description: String, rewardMinutes: Int) = requireToken { token ->
        PactioApi.createTask(token, childId, title, description, rewardMinutes)
        loadTasks(token)
        _state.update { it.copy(infoMessage = "Tugas berhasil dibuat.") }
    }

    fun submitTask(taskId: String, evidence: String, evidencePhotoDataUri: String? = null) = requireToken { token ->
        PactioApi.submitTask(token, taskId, evidence, evidencePhotoDataUri)
        loadTasks(token)
        loadBalanceIfChild(token)
        _state.update { it.copy(infoMessage = "Tugas berhasil dikirim, menunggu persetujuan orang tua.") }
    }

    fun decideTask(taskId: String, approved: Boolean, note: String) = requireToken { token ->
        PactioApi.decideTask(token, taskId, approved, note)
        loadTasks(token)
        _state.update { it.copy(infoMessage = if (approved) "Tugas disetujui." else "Tugas ditolak.") }
    }

    fun setChildLock(childId: String, enabled: Boolean) = requireToken { token ->
        PactioApi.setChildLock(token, childId, enabled)
        loadFamily(token)
        _state.update { it.copy(infoMessage = if (enabled) "Perangkat anak dikunci." else "Kunci perangkat dibuka.") }
    }

    fun refresh() = requireToken { token ->
        loadFamily(token)
        loadTasks(token)
        loadBalanceIfChild(token)
        _state.update { it.copy(infoMessage = "Data diperbarui.") }
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
     * ternyata sudah tidak valid lagi di backend (mis. server di-restart, sesi in-memory
     * hilang - lihat catatan keamanan backend) errornya ditangani rapi (logout otomatis),
     * bukan bikin aplikasi crash saat baru dibuka.
     */
    private fun refreshAll() = launchGuarded {
        val token = _state.value.token ?: return@launchGuarded
        loadFamily(token)
        loadTasks(token)
        loadBalanceIfChild(token)
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
        _state.update { it.copy(balanceMinutes = balance.minutes, approvedTaskCount = balance.approvedTaskCount) }
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
                _state.value = UiState(errorMessage = "Sesi berakhir, silakan masuk kembali.")
                return@launch
            } catch (error: ApiException) {
                _state.update { it.copy(errorMessage = error.message ?: "Terjadi kesalahan.") }
            } catch (error: Exception) {
                _state.update { it.copy(errorMessage = "Tidak dapat terhubung ke server. Periksa koneksi internet.") }
            }
            _state.update { it.copy(loading = false) }
        }
    }
}
