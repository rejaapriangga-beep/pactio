package com.keluargakendali.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.keluargakendali.data.GoogleAuthHelper
import kotlinx.coroutines.launch

/** Sub-langkah alur masuk orang tua — hanya muncul lewat link kecil di layar landing anak. */
private enum class ParentStep { NONE, MENU, LOGIN, REGISTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    state: UiState,
    onRegisterParent: (familyName: String, name: String, email: String, password: String) -> Unit,
    onLoginParent: (email: String, password: String) -> Unit,
    onLoginChild: (familyCode: String, pin: String) -> Unit,
    onLoginGoogle: (idToken: String) -> Unit,
    onGoogleError: (String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var parentStep by rememberSaveable { mutableStateOf(ParentStep.NONE) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Memanggil Credential Manager (dialog akun bawaan sistem), lalu meneruskan token ID
    // mentahnya ke backend untuk diverifikasi — Android sendiri tidak pernah memutuskan
    // login berhasil atau tidak.
    fun launchGoogleSignIn() {
        scope.launch {
            try {
                val idToken = GoogleAuthHelper.requestIdToken(context)
                onLoginGoogle(idToken)
            } catch (error: GetCredentialCancellationException) {
                // Pengguna membatalkan sendiri lewat dialog pemilih akun — bukan error.
            } catch (error: NoCredentialException) {
                onGoogleError("Tidak ada akun Google di HP ini. Tambahkan akun Google lewat Pengaturan terlebih dahulu.")
            } catch (error: Exception) {
                onGoogleError(error.message ?: "Gagal masuk dengan Google.")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ChildLandingScreen(
            state = state,
            onLoginChild = onLoginChild,
            onDismissMessage = onDismissMessage,
            onOpenParentMenu = { parentStep = ParentStep.MENU }
        )

        if (parentStep != ParentStep.NONE) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { parentStep = ParentStep.NONE; onDismissMessage() },
                sheetState = sheetState
            ) {
                when (parentStep) {
                    ParentStep.MENU -> ParentMenuSheet(
                        onSelectLogin = { parentStep = ParentStep.LOGIN },
                        onSelectRegister = { parentStep = ParentStep.REGISTER }
                    )
                    ParentStep.LOGIN -> ParentSheetForm(state = state, onDismissMessage = onDismissMessage) {
                        LoginParentForm(loading = state.loading, onSubmit = onLoginParent, onGoogleClick = ::launchGoogleSignIn)
                    }
                    ParentStep.REGISTER -> ParentSheetForm(state = state, onDismissMessage = onDismissMessage) {
                        RegisterParentForm(loading = state.loading, onSubmit = onRegisterParent)
                    }
                    ParentStep.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun ChildLandingScreen(
    state: UiState,
    onLoginChild: (familyCode: String, pin: String) -> Unit,
    onDismissMessage: () -> Unit,
    onOpenParentMenu: () -> Unit
) {
    var familyCode by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text("TimeCraft", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(26.dp))
        Text("Halo!\nSiap kerjakan tugas hari ini?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Masuk pakai kode keluarga dan PIN kamu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        if (state.errorMessage != null) {
            ErrorBanner(state.errorMessage, onDismissMessage)
            Spacer(Modifier.height(12.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Kode Keluarga", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = familyCode,
                    onValueChange = { familyCode = it.uppercase() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("PIN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField(pin, { pin = it }, "PIN", KeyboardType.NumberPassword)
            }

            Button(
                onClick = { onLoginChild(familyCode.trim(), pin) },
                enabled = !state.loading && familyCode.isNotBlank() && pin.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Masuk & Lihat Tugas", style = MaterialTheme.typography.labelLarge) }
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Orang tua? Masuk atau daftar di sini →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenParentMenu)
                .padding(vertical = 12.dp)
        )
    }
}

@Composable
private fun ParentMenuSheet(onSelectLogin: () -> Unit, onSelectRegister: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            Text("Akses Orang Tua", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Kelola tugas dan pantau progres anak.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ParentMenuOption(
            icon = Icons.Default.Key,
            iconContainer = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            title = "Masuk sebagai Orang Tua",
            subtitle = "Sudah punya akun keluarga",
            onClick = onSelectLogin
        )
        ParentMenuOption(
            icon = Icons.Default.PersonAdd,
            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            title = "Daftar Keluarga Baru",
            subtitle = "Buat akun keluarga pertama kamu",
            onClick = onSelectRegister
        )
    }
}

@Composable
private fun ParentMenuOption(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(iconContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = iconTint) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ParentSheetForm(state: UiState, onDismissMessage: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.errorMessage?.let {
            ErrorBanner(it, onDismissMessage)
        }
        content()
    }
}

@Composable
private fun RegisterParentForm(loading: Boolean, onSubmit: (String, String, String, String) -> Unit) {
    var familyName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Daftar Keluarga Baru", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(familyName, { familyName = it }, label = { Text("Nama keluarga") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Nama orang tua") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(password, { password = it }, "Kata sandi (minimal 8 karakter)", KeyboardType.Password)
        Button(
            onClick = { onSubmit(familyName.trim(), name.trim(), email.trim(), password) },
            enabled = !loading && familyName.isNotBlank() && name.isNotBlank() && email.isNotBlank() && password.length >= 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Daftarkan Keluarga", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun LoginParentForm(loading: Boolean, onSubmit: (String, String) -> Unit, onGoogleClick: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Masuk sebagai Orang Tua", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(password, { password = it }, "Kata sandi", KeyboardType.Password)
        Button(
            onClick = { onSubmit(email.trim(), password) },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Masuk", style = MaterialTheme.typography.labelLarge) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(Modifier.weight(1f))
            Text("atau", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f))
        }

        OutlinedButton(
            onClick = onGoogleClick,
            enabled = !loading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Lanjut dengan Google", style = MaterialTheme.typography.labelLarge)
        }
    }
}
