package com.andy.swipecleaner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme { App() }
        }
    }
}

val Purple = Color(0xFFC77DFF)
val KeepGreen = Color(0xFF4ADE80)
val DeleteRed = Color(0xFFFF5C7A)
val Muted = Color(0xFF9A9AA5)

private val Colors = darkColorScheme(
    primary = Purple,
    onPrimary = Color(0xFF1A0A2A),
    secondary = KeepGreen,
    error = DeleteRed,
    background = Color(0xFF111114),
    surface = Color(0xFF1B1B20),
    surfaceVariant = Color(0xFF26262D),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

@Composable
fun App(vm: CleanerViewModel = viewModel()) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(granted) {
        if (granted) vm.load()
    }

    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) vm.onTrashConfirmed()
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) vm.onRestoreConfirmed()
    }

    val requestTrash: () -> Unit = {
        vm.buildTrashRequest()?.let {
            trashLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }
    val requestRestore: () -> Unit = {
        vm.buildRestoreRequest()?.let {
            restoreLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!granted) {
            PermissionScreen { permissionLauncher.launch(permission) }
        } else {
            when (val s = vm.screen) {
                Screen.Home -> HomeScreen(vm, onTrash = requestTrash, onRestore = requestRestore)
                Screen.Scanning -> ScanningScreen(vm.progress, vm.progressText)
                is Screen.Deck -> DeckScreen(vm, s.title, onTrash = requestTrash)
            }
        }
    }
}
