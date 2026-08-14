package app.kaup.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.kaup.core.data.entities.UserEntity
import app.kaup.shared.domain.auth.PinPolicy
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    onUserSelected: (String) -> Unit,
    viewModel: LockScreenViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsState()
    var selectedUser by remember { mutableStateOf<UserEntity?>(null) }

    if (selectedUser == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Select Profile",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (users.isEmpty()) {
                CircularProgressIndicator()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(0.8f) // limits width on very wide tablets
                ) {
                    items(users) { user ->
                        UserCard(user = user, onClick = { selectedUser = user })
                    }
                }
            }
        }
    } else {
        PinEntryScreen(
            user = selectedUser!!,
            onCancel = { selectedUser = null },
            onSuccess = { 
                viewModel.login(selectedUser!!)
                onUserSelected(selectedUser!!.id) 
            }
        )
    }
}

@Composable
fun PinEntryScreen(
    user: UserEntity,
    onCancel: () -> Unit,
    onSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    // Evaluation is triggered by the confirm action, never by PIN length.
    // Submitting at a fixed length made every PIN longer than that length
    // impossible to enter, which locked the owner out of the app.
    LaunchedEffect(submitted) {
        if (!submitted) return@LaunchedEffect
        // TODO(#158): compare against a hashed credential, in a repository.
        if (pin == user.pinHash) {
            onSuccess()
        } else {
            isError = true
            delay(500)
            pin = ""
            isError = false
        }
        submitted = false
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // User Avatar
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = user.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(user.name, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(32.dp))

        // PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (i in 0 until PinPolicy.MAX_ENTRY_LENGTH) {
                val isFilled = i < pin.length
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isError) MaterialTheme.colorScheme.error
                            else if (isFilled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        if (isError) {
            Text(
                text = "Incorrect PIN",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(28.dp)) // Maintain spacing
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Numpad
        val keys = listOf(
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "", "0", "back"
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.width(280.dp)
        ) {
            items(keys) { key ->
                if (key == "") {
                    Spacer(modifier = Modifier.size(72.dp))
                } else if (key == "back") {
                    IconButton(
                        onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable {
                                if (PinPolicy.canAppend(pin)) pin += key
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { submitted = true },
            enabled = PinPolicy.isSubmittable(pin) && !submitted,
            modifier = Modifier
                .width(280.dp)
                .height(56.dp)
        ) {
            Text("Unlock", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun UserCard(user: UserEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(140.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = user.role.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
