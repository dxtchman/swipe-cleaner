package com.andy.swipecleaner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val bucket: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateTaken: Long,
) {
    val isScreenshot: Boolean
        get() = path.contains("screenshot", ignoreCase = true) ||
            bucket.contains("screenshot", ignoreCase = true) ||
            name.startsWith("screenshot", ignoreCase = true)

    val pixels: Long get() = width.toLong() * height.toLong()
}

object PhotoRepository {

    /** Every image in the gallery, newest first. */
    fun loadAll(context: Context): List<Photo> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val photos = ArrayList<Photo>()

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000L)
                photos += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(iName) ?: "",
                    path = c.getString(iPath) ?: "",
                    bucket = c.getString(iBucket) ?: "",
                    size = c.getLong(iSize),
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    dateTaken = taken,
                )
            }
        }
        return photos
    }
}
