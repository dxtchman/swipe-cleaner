package com.andy.swipecleaner

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeckItem(val photo: Photo, val tag: String, val original: Photo? = null)

sealed interface Screen {
    data object Home : Screen
    data object Scanning : Screen
    data class Deck(val title: String) : Screen
}

class CleanerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("cleaner", Context.MODE_PRIVATE)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var loading by mutableStateOf(true)
        private set
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set

    /** Cards still to swipe, top card first. */
    val deck = mutableStateListOf<DeckItem>()

    /** Photos swiped left, waiting to be moved to the trash. */
    val pending = mutableStateListOf<Photo>()

    var progress by mutableFloatStateOf(0f)
        private set
    var progressText by mutableStateOf("")
        private set
    var undoCount by mutableIntStateOf(0)
        private set
    var lastFreedBytes by mutableLongStateOf(0L)
        private set
    var hasRestorableBatch by mutableStateOf(prefs.contains(KEY_LAST_BATCH))
        private set
    private var keptVersion by mutableIntStateOf(0)

    private val kept: MutableSet<Long> =
        prefs.getStringSet(KEY_KEPT, emptySet())!!.mapNotNull { it.toLongOrNull() }.toMutableSet()
    private val history = ArrayList<Pair<DeckItem, Boolean>>()
    private var duplicateCache: List<DeckItem>? = null
    private var trashing: List<Photo> = emptyList()

    private fun isVisible(p: Photo): Boolean = p.id !in kept && pending.none { it.id == p.id }

    val screenshotCount: Int
        get() {
            keptVersion // re-read when "kept" changes
            return photos.count { it.isScreenshot && isVisible(it) }
        }

    val reviewCount: Int
        get() {
            keptVersion
            return photos.count { isVisible(it) }
        }

    val pendingBytes: Long get() = pending.sumOf { it.size }

    fun load(force: Boolean = false) {
        if (!force && photos.isNotEmpty()) return
        viewModelScope.launch {
            loading = true
            photos = withContext(Dispatchers.IO) { PhotoRepository.loadAll(getApplication()) }
            duplicateCache = null
            loading = false
        }
    }

    fun startScreenshots() {
        openDeck("Screenshots", photos.filter { it.isScreenshot && isVisible(it) }.map { DeckItem(it, "Screenshot") })
    }

    fun startAll() {
        openDeck("All photos", photos.filter { isVisible(it) }.map { DeckItem(it, it.bucket.ifBlank { "Photo" }) })
    }

    fun startDuplicates() {
        val cached = duplicateCache
        if (cached != null) {
            openDeck("Duplicates", cached.filter { isVisible(it.photo) })
            return
        }
        viewModelScope.launch {
            progress = 0f
            progressText = "Getting started…"
            screen = Screen.Scanning
            val snapshot = photos
            val found = withContext(Dispatchers.Default) {
                DuplicateFinder.find(getApplication(), snapshot) { f, text ->
                    progress = f
                    progressText = text
                }
            }
            duplicateCache = found
            openDeck("Duplicates", found.filter { isVisible(it.photo) })
        }
    }

    private fun openDeck(title: String, items: List<DeckItem>) {
        deck.clear()
        deck.addAll(items)
        history.clear()
        undoCount = 0
        screen = Screen.Deck(title)
    }

    fun goHome() {
        screen = Screen.Home
    }

    /** keep = swiped right. */
    fun swipe(keep: Boolean) {
        val item = deck.firstOrNull() ?: return
        deck.removeAt(0)
        history += item to keep
        undoCount = history.size
        if (keep) {
            kept += item.photo.id
            saveKept()
        } else {
            pending += item.photo
        }
    }

    fun undo() {
        if (history.isEmpty()) return
        val (item, keep) = history.removeAt(history.lastIndex)
        undoCount = history.size
        if (keep) {
            kept -= item.photo.id
            saveKept()
        } else {
            pending.removeAll { it.id == item.photo.id }
        }
        deck.add(0, item)
    }

    fun resetKept() {
        kept.clear()
        saveKept()
    }

    private fun saveKept() {
        prefs.edit().putStringSet(KEY_KEPT, kept.map { it.toString() }.toSet()).apply()
        keptVersion++
    }

    // ---- Trash ----

    fun buildTrashRequest(): PendingIntent? {
        if (pending.isEmpty()) return null
        trashing = pending.toList()
        return try {
            MediaStore.createTrashRequest(
                getApplication<Application>().contentResolver,
                trashing.map { it.uri },
                true,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun onTrashConfirmed() {
        val ids = trashing.map { it.id }.toSet()
        prefs.edit().putString(KEY_LAST_BATCH, trashing.joinToString("\n") { it.uri.toString() }).apply()
        hasRestorableBatch = true
        lastFreedBytes = trashing.sumOf { it.size }
        pending.removeAll { it.id in ids }
        deck.removeAll { it.photo.id in ids }
        photos = photos.filterNot { it.id in ids }
        duplicateCache = duplicateCache?.filterNot { it.photo.id in ids }
        history.clear()
        undoCount = 0
        trashing = emptyList()
    }

    fun buildRestoreRequest(): PendingIntent? {
        val saved = prefs.getString(KEY_LAST_BATCH, null) ?: return null
        val uris = saved.split("\n").filter { it.isNotBlank() }.map(Uri::parse)
        return try {
            MediaStore.createTrashRequest(getApplication<Application>().contentResolver, uris, false)
        } catch (e: Exception) {
            // Already emptied from the trash; nothing left to restore.
            clearRestorable()
            null
        }
    }

    fun onRestoreConfirmed() {
        clearRestorable()
        load(force = true)
    }

    private fun clearRestorable() {
        prefs.edit().remove(KEY_LAST_BATCH).apply()
        hasRestorableBatch = false
    }

    companion object {
        private const val KEY_KEPT = "kept_ids"
        private const val KEY_LAST_BATCH = "last_batch"
    }
}
