package com.keluargakendali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keluargakendali.ui.AppViewModel
import com.keluargakendali.ui.AuthScreen
import com.keluargakendali.ui.ChildScreen
import com.keluargakendali.ui.ChildSettingsDialog
import com.keluargakendali.ui.ParentScreen
import com.keluargakendali.ui.ParentSettingsDialog
import com.keluargakendali.ui.PasswordField
import com.keluargakendali.ui.theme.PactioTheme
import kotlinx.coroutines.delay

/** Jarak antar poll otomatis di background - lihat AppViewModel.silentRefresh. */
private const val AUTO_REFRESH_MS = 8_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PactioApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PactioApp() {
    val viewModel: AppViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Notifikasi sukses (bukan cuma error) untuk aksi seperti buat tugas, approve, dsb.
    LaunchedEffect(state.infoMessage) {
        val message = state.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMessages()
    }

    // Anak butuh kata sandi orang tua dulu sebelum tombol "Keluar" diproses - lihat
    // ChildLogoutDialog & AppViewModel.confirmChildLogout. Dialognya ditutup otomatis kalau
    // logout benar-benar terjadi (currentUser jadi null).
    var showChildLogoutDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentUser) {
        if (state.currentUser == null) {
            showChildLogoutDialog = false
            showSettings = false
        }
    }

    // Poll berkala selagi ada yang login - supaya tugas baru dari orang tua, atau tugas
    // yang baru dikirim/disetujui/ditolak dari HP LAIN, muncul otomatis tanpa perlu tekan
    // refresh manual. Lihat catatan lengkap di AppViewModel.silentRefresh.
    LaunchedEffect(state.currentUser?.id) {
        if (state.currentUser == null) return@LaunchedEffect
        while (true) {
            delay(AUTO_REFRESH_MS)
            viewModel.silentRefresh()
        }
    }

    PactioTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // Layar landing (belum login) punya header sendiri yang lebih ekspresif —
                // TopAppBar generik ini hanya untuk dashboard orang tua/anak setelah login.
                if (state.currentUser != null) {
                    TopAppBar(
                        title = { Text("Pactio") },
                        actions = {
                            // Pengaturan dipindah ke sini (bukan tab lagi - lihat ParentScreen/ChildScreen)
                            // supaya tab utama tetap muat satu baris tanpa digulir. Sengaja di sebelah
                            // kiri "Keluar", bukan tombol refresh manual - data sudah disegarkan otomatis
                            // lewat polling berkala (lihat AppViewModel.silentRefresh di bawah).
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                            }
                            TextButton(onClick = {
                                if (state.currentUser?.role == "child") showChildLogoutDialog = true else viewModel.logout()
                            }) { Text("Keluar") }
                        }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxWidth().padding(padding)) {
                when {
                    state.currentUser == null -> AuthScreen(
                        state = state,
                        onRegisterParent = viewModel::registerParent,
                        onLoginParent = viewModel::loginParent,
                        onLoginChild = viewModel::loginChild,
                        onLoginGoogle = { idToken -> viewModel.loginWithGoogle(idToken) },
                        onGoogleError = viewModel::reportError,
                        onDismissMessage = viewModel::dismissMessages
                    )

                    state.currentUser?.role == "parent" -> ParentScreen(
                        state = state,
                        onDecide = viewModel::decideTask,
                        onSetLock = { childId, enabled -> viewModel.setChildLock(childId, enabled) },
                        onCreateTask = viewModel::createTask,
                        onDismissMessage = viewModel::dismissMessages,
                        onRefreshChatUnread = viewModel::refreshChatUnread
                    )

                    else -> ChildScreen(
                        state = state,
                        onSubmitTask = { taskId, evidence, files -> viewModel.submitTask(taskId, evidence, files) },
                        onRedeemBalance = { minutes -> viewModel.redeemAccessBalance(minutes) },
                        onDismissMessage = viewModel::dismissMessages,
                        onRefreshChatUnread = viewModel::refreshChatUnread
                    )
                }

                if (state.loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }
            }
        }

        if (showChildLogoutDialog) {
            ChildLogoutDialog(
                loading = state.loading,
                onDismiss = { showChildLogoutDialog = false },
                onConfirm = { password, onWrongPassword -> viewModel.confirmChildLogout(password, onWrongPassword) }
            )
        }

        if (showSettings) {
            when (state.currentUser?.role) {
                "parent" -> ParentSettingsDialog(
                    children = state.children,
                    loading = state.loading,
                    onAddChild = viewModel::addChild,
                    onDeleteChild = viewModel::deleteChild,
                    onDismiss = { showSettings = false }
                )
                "child" -> ChildSettingsDialog(state = state, onDismiss = { showSettings = false })
            }
        }
    }
}

/** Gerbang "Keluar" khusus akun anak — lihat AppViewModel.confirmChildLogout. */
@Composable
private fun ChildLogoutDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, onWrongPassword: (String) -> Unit) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kata sandi orang tua") },
        text = {
            Column {
                Text(
                    "Minta orang tua memasukkan kata sandi akunnya untuk keluar dari HP ini.",
                    style = MaterialTheme.typography.bodySmall
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Kata sandi orang tua",
                    keyboardType = KeyboardType.Password
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) { message -> error = message } },
                enabled = !loading && password.isNotBlank()
            ) { Text("Keluar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
