package com.keluargakendali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keluargakendali.ui.AppViewModel
import com.keluargakendali.ui.AuthScreen
import com.keluargakendali.ui.ChildScreen
import com.keluargakendali.ui.ParentScreen

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

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pactio") },
                    actions = {
                        if (state.currentUser != null) {
                            TextButton(onClick = viewModel::logout) { Text("Keluar") }
                        }
                    }
                )
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
                        onAddChild = viewModel::addChild,
                        onCreateTask = viewModel::createTask,
                        onDecide = viewModel::decideTask,
                        onDismissMessage = viewModel::dismissMessages
                    )

                    else -> ChildScreen(
                        state = state,
                        onSubmitTask = viewModel::submitTask,
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
    }
}
