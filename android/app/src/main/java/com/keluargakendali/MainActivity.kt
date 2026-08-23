package com.keluargakendali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import com.keluargakendali.ui.ParentScreen
import com.keluargakendali.ui.PasswordField
import com.keluargakendali.ui.theme.PactioTheme

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
    LaunchedEffect(state.currentUser) {
        if (state.currentUser == null) showChildLogoutDialog = false
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
                            IconButton(onClick = viewModel::refresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "Segarkan data")
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
                        onAddChild = viewModel::addChild,
                        onCreateTask = viewModel::createTask,
                        onDecide = viewModel::decideTask,
                        onSetLock = { childId, enabled -> viewModel.setChildLock(childId, enabled) },
                        onDismissMessage = viewModel::dismissMessages
                    )

                    else -> ChildScreen(
                        state = state,
                        onSubmitTask = { taskId, evidence, photo -> viewModel.submitTask(taskId, evidence, photo) },
                        onDismissMessage = viewModel::dismissMessages
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
