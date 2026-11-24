package com.andy.swipecleaner

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Size
import java.security.MessageDigest

/**
 * Finds two kinds of extras:
 *  - exact copies (same bytes, anywhere in the gallery)
 *  - similar shots (near-identical pictures taken within a few minutes, like bursts)
 * For each group, the best copy is kept out of the deck and shown as "Original".
 */
object DuplicateFinder {

    private const val SIMILAR_WINDOW_MS = 10 * 60 * 1000L
    private const val MAX_HASH_DISTANCE = 6

    fun find(
        context: Context,
        photos: List<Photo>,
        onProgress: (fraction: Float, text: String) -> Unit,
    ): List<DeckItem> {
        val resolver = context.contentResolver
        val result = ArrayList<DeckItem>()
        val extras = HashSet<Long>()

        // 1) Exact copies: only files with the same size can be identical, so hash just those.
        val sizeGroups = photos.filter { it.size > 0 }
            .groupBy { it.size }
            .values
            .filter { it.size > 1 }
        val toHash = sizeGroups.sumOf { it.size }.coerceAtLeast(1)
        var hashed = 0

        for (group in sizeGroups) {
            val byHash = HashMap<String, MutableList<Photo>>()
            for (p in group) {
                md5(resolver, p.uri)?.let { byHash.getOrPut(it) { ArrayList() }.add(p) }
                hashed++
                if (hashed % 10 == 0) onProgress(0.3f * hashed / toHash, "Checking for exact copies…")
            }
            for (same in byHash.values) {
                if (same.size < 2) continue
                val original = same.minBy { it.dateTaken }
                for (p in same) {
                    if (p.id == original.id) continue
                    result += DeckItem(p, "Exact copy", original)
                    extras += p.id
                }
            }
        }

        // 2) Similar shots: compare pictures taken close together using a tiny "fingerprint".
        val sorted = photos.filter { it.id !in extras }.sortedBy { it.dateTaken }
        val candidates = sorted.filterIndexed { i, p ->
            (i > 0 && p.dateTaken - sorted[i - 1].dateTaken <= SIMILAR_WINDOW_MS) ||
                (i < sorted.lastIndex && sorted[i + 1].dateTaken - p.dateTaken <= SIMILAR_WINDOW_MS)
        }

        val withHash = ArrayList<Photo>()
        val hashes = ArrayList<Long>()
        candidates.forEachIndexed { i, p ->
            dHash(resolver, p.uri)?.let {
                withHash += p
                hashes += it
            }
            if (i % 10 == 0) {
                onProgress(0.3f + 0.7f * i / candidates.size.coerceAtLeast(1), "Looking for similar photos…")
            }
        }

        val parent = IntArray(withHash.size) { it }
        fun root(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val next = parent[c]
                parent[c] = r
                c = next
            }
            return r
        }

        for (i in withHash.indices) {
            var j = i + 1
            while (j < withHash.size && withHash[j].dateTaken - withHash[i].dateTaken <= SIMILAR_WINDOW_MS) {
                if (java.lang.Long.bitCount(hashes[i] xor hashes[j]) <= MAX_HASH_DISTANCE) {
                    parent[root(j)] = root(i)
                }
                j++
            }
        }

        val groups = withHash.indices.groupBy { root(it) }.values.filter { it.size > 1 }
        for (g in groups) {
            val members = g.map { withHash[it] }
            val original = members.maxWith(compareBy<Photo>({ it.pixels }, { it.size }))
            for (p in members) {
                if (p.id != original.id) result += DeckItem(p, "Similar photo", original)
            }
        }

        onProgress(1f, "Done")
        // Keep each group together, newest groups first.
        return result.sortedWith(
            compareByDescending<DeckItem> { it.original?.dateTaken ?: it.photo.dateTaken }
                .thenBy { it.original?.id ?: it.photo.id }
        )
    }

    private fun md5(resolver: ContentResolver, uri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val input = resolver.openInputStream(uri) ?: return null
            input.use {
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = it.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { b -> "%02x".format(b) }
        } catch (e: Exception) {
            null
        }
    }

    /** 64-bit difference hash of a small thumbnail. Similar pictures have similar hashes. */
    private fun dHash(resolver: ContentResolver, uri: Uri): Long? {
        return try {
            var thumb = resolver.loadThumbnail(uri, Size(96, 96), null)
            if (thumb.config == Bitmap.Config.HARDWARE) {
                thumb = thumb.copy(Bitmap.Config.ARGB_8888, false)
            }
            val small = Bitmap.createScaledBitmap(thumb, 9, 8, true)
            var hash = 0L
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    if (luma(small.getPixel(x, y)) > luma(small.getPixel(x + 1, y))) {
                        hash = hash or (1L shl bit)
                    }
                    bit++
                }
            }
            if (small !== thumb) small.recycle()
            thumb.recycle()
            hash
        } catch (e: Exception) {
            null
        }
    }

    private fun luma(c: Int): Int =
        (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
}
