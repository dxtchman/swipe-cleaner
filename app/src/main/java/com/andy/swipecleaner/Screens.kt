package com.andy.swipecleaner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    else -> String.format(Locale.US, "%d KB", bytes / 1000)
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Swipe Cleaner", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "To find screenshots and duplicates, the app needs to see your photos. Nothing leaves your phone.",
            color = Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Allow access to photos") }
    }
}

@Composable
fun HomeScreen(vm: CleanerViewModel, onTrash: () -> Unit, onRestore: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Swipe Cleaner", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            if (vm.loading) "Reading your gallery…" else "${vm.photos.size} photos in your gallery",
            color = Muted,
        )

        if (vm.pending.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeleteRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${vm.pending.size} marked for deletion", fontWeight = FontWeight.SemiBold)
                        Text(formatSize(vm.pendingBytes) + " to free up", color = Muted)
                    }
                    Button(onClick = onTrash) { Text("Delete") }
                }
            }
        }

        ModeCard(
            title = "Screenshots",
            count = if (vm.loading) null else "${vm.screenshotCount} to review",
            subtitle = "Old screenshots you probably don't need anymore",
            enabled = !vm.loading,
            onClick = vm::startScreenshots,
        )
        ModeCard(
            title = "Duplicates & similar",
            count = null,
            subtitle = "Exact copies and near-identical shots. The best copy is shown as \"Original\".",
            enabled = !vm.loading,
            onClick = vm::startDuplicates,
        )
        ModeCard(
            title = "All photos",
            count = if (vm.loading) null else "${vm.reviewCount} to review",
            subtitle = "Go through everything, newest first",
            enabled = !vm.loading,
            onClick = vm::startAll,
        )

        Text(
            "Swipe left to delete, right to keep. Deleted photos go to your phone's trash, so you can still get them back.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )

        if (vm.lastFreedBytes > 0) {
            Text("Last cleanup freed ${formatSize(vm.lastFreedBytes)}.", color = KeepGreen)
        }
        if (vm.hasRestorableBatch) {
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Restore last deleted batch")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = vm::resetKept) { Text("Show kept photos again") }
            TextButton(onClick = { vm.load(force = true) }) { Text("Rescan gallery") }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    count: String?,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (count != null) Text(count, color = Purple, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted)
        }
    }
}

@Composable
fun ScanningScreen(progress: Float, text: String) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}%", color = Muted)
        Spacer(Modifier.height(16.dp))
        Text(
            "This can take a minute or two on a big gallery.",
            color = Muted,
            textAlign = TextAlign.Center,
        )
    }
}
