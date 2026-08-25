package com.keluargakendali

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keluargakendali.data.LocaleHelper
import com.keluargakendali.data.SettingsStore
import com.keluargakendali.ui.AppViewModel
import com.keluargakendali.ui.AuthScreen
import com.keluargakendali.ui.BackupIconButton
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
    // Override locale SEBELUM Activity dibuat, supaya semua string resource (termasuk yang
    // dipakai layar landing) langsung terbit dalam bahasa yang dipilih pengguna - lihat
    // LocaleHelper. Kalau ini dilewatkan, resource yang sudah kadung dibaca sebelum override
    // tidak akan ikut berganti sampai Activity di-recreate ulang.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

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
    val context = LocalContext.current

    // Mode gelap: cukup ganti state lokal, PactioTheme langsung ikut re-render - TIDAK perlu
    // recreate Activity (beda dengan ganti bahasa di bawah, yang harus baca ulang semua
    // string resource). Disimpan ke SettingsStore supaya pilihannya diingat lain kali dibuka.
    var darkMode by remember { mutableStateOf(SettingsStore.isDarkMode(context)) }

    // Ganti bahasa BUTUH recreate Activity - attachBaseContext (lihat MainActivity) cuma
    // dibaca sekali saat Activity dibuat, jadi satu-satunya cara semua string resource yang
    // sudah kadung "ke-resolve" ikut berganti bahasa adalah dengan membuat ulang Activity-nya.
    fun selectLanguage(language: String) {
        SettingsStore.setLanguage(context, language)
        (context as? android.app.Activity)?.recreate()
    }
    var showLanguageMenu by remember { mutableStateOf(false) }

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

    PactioTheme(darkTheme = darkMode) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // Layar landing (belum login) punya header sendiri yang lebih ekspresif —
                // TopAppBar generik ini hanya untuk dashboard orang tua/anak setelah login.
                if (state.currentUser != null) {
                    TopAppBar(
                        title = { Text("TimeCraft") },
                        actions = {
                            // Akses cepat gaya "toolbar ikon" (bahasa, mode gelap, backup) - dipasang
                            // di TopAppBar dashboard, bukan ditumpuk di dalam Pengaturan, terinspirasi
                            // pola aplikasi lain (mis. DompetDigitalKu) yang menaruh toggle tema di
                            // header. Backup hanya relevan untuk orang tua (butuh token akses penuh).
                            Box {
                                IconButton(onClick = { showLanguageMenu = true }) {
                                    Icon(Icons.Default.Translate, contentDescription = "Ganti bahasa / Switch language")
                                }
                                DropdownMenu(expanded = showLanguageMenu, onDismissRequest = { showLanguageMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Bahasa Indonesia") },
                                        onClick = { showLanguageMenu = false; selectLanguage("id") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("English") },
                                        onClick = { showLanguageMenu = false; selectLanguage("en") }
                                    )
                                }
                            }
                            IconButton(onClick = {
                                darkMode = !darkMode
                                SettingsStore.setDarkMode(context, darkMode)
                            }) {
                                Icon(
                                    if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = stringResource(R.string.cd_toggle_dark_mode)
                                )
                            }
                            if (state.currentUser?.role == "parent" && state.token != null) {
                                BackupIconButton(token = state.token!!, familyName = state.family?.name)
                            }
                            // Pengaturan dipindah ke sini (bukan tab lagi - lihat ParentScreen/ChildScreen)
                            // supaya tab utama tetap muat satu baris tanpa digulir. Sengaja di sebelah
                            // kiri "Keluar", bukan tombol refresh manual - data sudah disegarkan otomatis
                            // lewat polling berkala (lihat AppViewModel.silentRefresh di bawah).
                            IconButton(onClick = {
                                showSettings = true
                                // Log Aktivitas dimuat sekali tiap dialog Pengaturan dibuka (bukan
                                // polling) - lihat catatan di AppViewModel.loadActivityLog. Tidak
                                // berefek untuk akun anak (dialognya beda, lihat ChildSettingsDialog).
                                if (state.currentUser?.role == "parent") viewModel.loadActivityLog()
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.app_settings))
                            }
                            TextButton(onClick = {
                                if (state.currentUser?.role == "child") showChildLogoutDialog = true else viewModel.logout()
                            }) { Text(stringResource(R.string.action_logout)) }
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
                        onDismissMessage = viewModel::dismissMessages
                    )

                    state.currentUser?.role == "parent" -> ParentScreen(
                        state = state,
                        onDecide = viewModel::decideTask,
                        onSetLock = { childId, enabled -> viewModel.setChildLock(childId, enabled) },
                        onCreateTask = viewModel::createTask,
                        onAddChild = viewModel::addChild,
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
                    activityLog = state.activityLog,
                    loading = state.loading,
                    onDeleteChild = viewModel::deleteChild,
                    onResetPin = viewModel::resetChildPin,
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
        title = { Text(stringResource(R.string.parent_password_label)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.parent_password_dialog_body),
                    style = MaterialTheme.typography.bodySmall
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = stringResource(R.string.parent_password_label),
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
            ) { Text(stringResource(R.string.action_logout)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
