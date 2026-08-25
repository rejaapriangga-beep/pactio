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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keluargakendali.R

/** Sub-langkah alur masuk orang tua — hanya muncul lewat link kecil di layar landing anak. */
private enum class ParentStep { NONE, MENU, LOGIN, REGISTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    state: UiState,
    onRegisterParent: (familyName: String, name: String, email: String, password: String) -> Unit,
    onLoginParent: (email: String, password: String) -> Unit,
    onLoginChild: (familyCode: String, pin: String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var parentStep by rememberSaveable { mutableStateOf(ParentStep.NONE) }

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
                        LoginParentForm(loading = state.loading, onSubmit = onLoginParent)
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
        Text(stringResource(R.string.greeting_headline), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.child_login_subtitle),
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
                Text(stringResource(R.string.label_family_code), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = familyCode,
                    onValueChange = { familyCode = it.uppercase() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.label_pin), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField(pin, { pin = it }, stringResource(R.string.label_pin), KeyboardType.NumberPassword)
            }

            Button(
                onClick = { onLoginChild(familyCode.trim(), pin) },
                enabled = !state.loading && familyCode.isNotBlank() && pin.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(stringResource(R.string.action_child_login), style = MaterialTheme.typography.labelLarge) }
        }

        Spacer(Modifier.weight(1f))

        Text(
            stringResource(R.string.link_parent_access),
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
            Text(stringResource(R.string.title_parent_access), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.subtitle_parent_access),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ParentMenuOption(
            icon = Icons.Default.Key,
            iconContainer = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            title = stringResource(R.string.option_login_parent_title),
            subtitle = stringResource(R.string.option_login_parent_subtitle),
            onClick = onSelectLogin
        )
        ParentMenuOption(
            icon = Icons.Default.PersonAdd,
            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            title = stringResource(R.string.option_register_title),
            subtitle = stringResource(R.string.option_register_subtitle),
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
        Text(stringResource(R.string.option_register_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(familyName, { familyName = it }, label = { Text(stringResource(R.string.label_family_name)) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_parent_name)) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it }, label = { Text(stringResource(R.string.label_email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(password, { password = it }, stringResource(R.string.label_password_min8), KeyboardType.Password)
        Button(
            onClick = { onSubmit(familyName.trim(), name.trim(), email.trim(), password) },
            enabled = !loading && familyName.isNotBlank() && name.isNotBlank() && email.isNotBlank() && password.length >= 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(stringResource(R.string.action_register_family), style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun LoginParentForm(loading: Boolean, onSubmit: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.option_login_parent_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            email, { email = it }, label = { Text(stringResource(R.string.label_email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(password, { password = it }, stringResource(R.string.label_password), KeyboardType.Password)
        Button(
            onClick = { onSubmit(email.trim(), password) },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(stringResource(R.string.action_login), style = MaterialTheme.typography.labelLarge) }
    }
}
