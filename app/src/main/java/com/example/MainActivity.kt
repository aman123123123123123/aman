package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.signOut
import com.example.data.DeviceRole
import com.example.data.FamilyGuardRepository
import com.example.data.PreferencesRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChildDashboardScreen
import com.example.ui.screens.ParentDashboardScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.theme.FamilyGuardTheme
import com.example.viewmodel.ChildViewModel
import com.example.viewmodel.ParentViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FamilyGuardTheme {
                    FamilyGuardApp()
                }
            }
        }
    }
}

@Composable
fun FamilyGuardApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val preferences = remember(context) { PreferencesRepository(context) }
    val role by preferences.observeDeviceRole().collectAsStateWithLifecycle(initialValue = DeviceRole.UNSET)

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val handleSignOut: () -> Unit = {
        signOut(credentialManager = credentialManager, scope = scope)
    }

    val handleSwitchRole: () -> Unit = {
        scope.launch {
            preferences.setDeviceRole(DeviceRole.UNSET)
        }
    }

    val user = currentUser
    when {
        role == DeviceRole.UNSET -> {
            RoleSelectionScreen(
                userEmail = user?.email,
                onSelectRole = { chosen ->
                    scope.launch { preferences.setDeviceRole(chosen) }
                },
                onSignOut = if (user != null) handleSignOut else null
            )
        }

        user == null -> {
            BackHandler { handleSwitchRole() }
            AuthScreen(
                selectedRole = role,
                onChangeRole = handleSwitchRole,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        }

        else -> {
            key(user.uid) {
                AuthenticatedFamilyRouter(
                    uid = user.uid,
                    role = role,
                    onSwitchRole = handleSwitchRole,
                    onSignOut = handleSignOut
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedFamilyRouter(
    uid: String,
    role: DeviceRole,
    onSwitchRole: () -> Unit,
    onSignOut: () -> Unit
) {
    when (role) {
        DeviceRole.UNSET -> Unit

        DeviceRole.PARENT -> {
            BackHandler { onSwitchRole() }
            val parentVm: ParentViewModel = viewModel(
                key = "parent_$uid",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        ParentViewModel(FamilyGuardRepository(db), uid)
                    }
                }
            )
            ParentDashboardScreen(
                viewModel = parentVm,
                onSwitchRole = onSwitchRole,
                onSignOut = onSignOut
            )
        }

        DeviceRole.CHILD -> {
            BackHandler { onSwitchRole() }
            val childVm: ChildViewModel = viewModel(
                key = "child_$uid",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        ChildViewModel(
                            repository = FamilyGuardRepository(db),
                            preferences = PreferencesRepository(app),
                            currentUserId = uid
                        )
                    }
                }
            )
            ChildDashboardScreen(
                viewModel = childVm,
                onSwitchRole = onSwitchRole
            )
        }
    }
}
