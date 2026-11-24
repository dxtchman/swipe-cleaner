package com.andy.swipecleaner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** A button press asks the card with this photo id to fly out. */
private data class SwipeRequest(val photoId: Long, val keep: Boolean)

@Composable
fun DeckScreen(vm: CleanerViewModel, title: String, onTrash: () -> Unit) {
    BackHandler { vm.goHome() }
    var request by remember { mutableStateOf<SwipeRequest?>(null) }
    val top = vm.deck.getOrNull(0)
    val next = vm.deck.getOrNull(1)

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::goHome) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${vm.deck.size} left · ${vm.pending.size} marked", color = Muted)
            }
            if (vm.pending.isNotEmpty()) {
                Button(onClick = onTrash) { Text("Delete ${vm.pending.size}") }
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (top == null) {
                DoneContent(vm, onTrash)
            } else {
                if (next != null) {
                    key(next.photo.id) {
                        PhotoCard(
                            next,
                            Modifier.fillMaxSize().graphicsLayer {
                                scaleX = 0.94f
                                scaleY = 0.94f
                                alpha = 0.5f
                            },
                        )
                    }
                }
                key(top.photo.id) {
                    SwipeableCard(item = top, request = request, onSwiped = vm::swipe)
                }
            }
        }

        if (top != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Filled.Close, "Delete", DeleteRed) {
                    request = SwipeRequest(top.photo.id, keep = false)
                }
                TextButton(
                    onClick = {
                        request = null
                        vm.undo()
                    },
                    enabled = vm.undoCount > 0,
                ) { Text("Undo") }
                RoundButton(Icons.Filled.Favorite, "Keep", KeepGreen) {
                    request = SwipeRequest(top.photo.id, keep = true)
                }
            }
        }
    }
}

@Composable
private fun SwipeableCard(item: DeckItem, request: SwipeRequest?, onSwiped: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var flying by remember { mutableStateOf(false) }

    suspend fun flyOut(keep: Boolean) {
        if (flying) return
        flying = true
        val target = if (keep) width * 1.6f else -width * 1.6f
        offsetX.animateTo(target, tween(220))
        onSwiped(keep)
    }

    LaunchedEffect(request) {
        val r = request
        if (r != null && r.photoId == item.photo.id) flyOut(r.keep)
    }

    val swipeProgress = (offsetX.value / (width * 0.28f)).coerceIn(-1f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = (offsetX.value / width) * 18f
            }
            .pointerInput(item.photo.id) {
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        if (!flying) {
                            scope.launch {
                                offsetX.snapTo(offsetX.value + amount.x)
                                offsetY.snapTo(offsetY.value + amount.y)
                            }
                        }
                    },
                    onDragEnd = {
                        val threshold = width * 0.28f
                        scope.launch {
                            when {
                                offsetX.value > threshold -> flyOut(true)
                                offsetX.value < -threshold -> flyOut(false)
                                else -> {
                                    launch { offsetY.animateTo(0f, spring()) }
                                    offsetX.animateTo(0f, spring())
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, spring()) }
                        scope.launch { offsetY.animateTo(0f, spring()) }
                    },
                )
            },
    ) {
        PhotoCard(item, Modifier.fillMaxSize())
        if (swipeProgress > 0.05f) {
            Stamp("KEEP", KeepGreen, swipeProgress, -12f, Modifier.align(Alignment.TopStart))
        }
        if (swipeProgress < -0.05f) {
            Stamp("DELETE", DeleteRed, -swipeProgress, 12f, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color, alpha: Float, rotation: Float, modifier: Modifier) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .padding(28.dp)
            .graphicsLayer {
                this.alpha = alpha
                rotationZ = rotation
            }
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .background(Color(0x99000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun PhotoCard(item: DeckItem, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.photo.uri,
                contentDescription = item.photo.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            item.original?.let { original ->
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 96.dp)
                        .width(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD000000))
                        .padding(6.dp),
                ) {
                    AsyncImage(
                        model = original.uri,
                        contentDescription = "Original",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        "Original",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
                    .padding(start = 18.dp, end = 18.dp, top = 28.dp, bottom = 16.dp),
            ) {
                Text(item.tag, color = Purple, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    item.photo.name,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDate(item.photo.dateTaken)} · ${formatSize(item.photo.size)}",
                    color = Color(0xFFBBBBC4),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = color.copy(alpha = 0.18f),
            contentColor = color,
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun DoneContent(vm: CleanerViewModel, onTrash: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("All done", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (vm.pending.isNotEmpty()) {
            Text(
                "${vm.pending.size} photos marked · ${formatSize(vm.pendingBytes)}",
                color = Muted,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onTrash) { Text("Move ${vm.pending.size} to trash") }
            Spacer(Modifier.height(8.dp))
        } else {
            Text("Nothing left to review here.", color = Muted)
            Spacer(Modifier.height(20.dp))
        }
        OutlinedButton(onClick = vm::goHome) { Text("Back home") }
    }
}
