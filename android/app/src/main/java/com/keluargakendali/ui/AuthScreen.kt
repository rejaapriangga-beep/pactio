package com.keluargakendali.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    state: UiState,
    onRegisterParent: (familyName: String, name: String, email: String, password: String) -> Unit,
    onLoginParent: (email: String, password: String) -> Unit,
    onLoginChild: (familyCode: String, pin: String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Selamat datang di Pactio", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Kesepakatan tugas dan hadiah akses antara orang tua dan anak.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        state.errorMessage?.let {
            ErrorBanner(it, onDismissMessage)
            Spacer(Modifier.height(12.dp))
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0; onDismissMessage() }, text = { Text("Daftar Ortu") })
            Tab(selected = tab == 1, onClick = { tab = 1; onDismissMessage() }, text = { Text("Masuk Ortu") })
            Tab(selected = tab == 2, onClick = { tab = 2; onDismissMessage() }, text = { Text("Masuk Anak") })
        }
        Spacer(Modifier.height(16.dp))

        when (tab) {
            0 -> RegisterParentForm(loading = state.loading, onSubmit = onRegisterParent)
            1 -> LoginParentForm(loading = state.loading, onSubmit = onLoginParent)
            else -> LoginChildForm(loading = state.loading, onSubmit = onLoginChild)
        }
    }
}

@Composable
private fun RegisterParentForm(loading: Boolean, onSubmit: (String, String, String, String) -> Unit) {
    var familyName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(familyName, { familyName = it }, label = { Text("Nama keluarga") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Nama orang tua") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Kata sandi (minimal 8 karakter)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSubmit(familyName.trim(), name.trim(), email.trim(), password) },
            enabled = !loading && familyName.isNotBlank() && name.isNotBlank() && email.isNotBlank() && password.length >= 8,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Daftarkan Keluarga") }
    }
}

@Composable
private fun LoginParentForm(loading: Boolean, onSubmit: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Kata sandi") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSubmit(email.trim(), password) },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Masuk sebagai Orang Tua") }
    }
}

@Composable
private fun LoginChildForm(loading: Boolean, onSubmit: (String, String) -> Unit) {
    var familyCode by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Minta kode keluarga ke orang tuamu.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(familyCode, { familyCode = it }, label = { Text("Kode keluarga") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            pin, { pin = it }, label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSubmit(familyCode.trim(), pin) },
            enabled = !loading && familyCode.isNotBlank() && pin.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Masuk sebagai Anak") }
    }
}
