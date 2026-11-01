package io.github.eyalshimshi.shidur.playback

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.eyalshimshi.shidur.ShidurApplication
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking

/**
 * Serves station artwork as PNG over content:// URIs. Android Auto and the system media controls
 * only accept artwork they can open themselves, so remote URLs are not used.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val stationId = uri.lastPathSegment ?: return null
        val app = context.applicationContext as? ShidurApplication ?: return null
        val station = runBlocking { app.container.catalogRepository.findStation(stationId) } ?: return null

        val directory = File(context.cacheDir, "artwork").apply { mkdirs() }
        val key = "${station.id}_${station.color}_${station.shortName}".hashCode().toUInt()
        val file = File(directory, "$key.png")
        if (!file.exists()) {
            val bitmap = MonogramRenderer.render(station, ARTWORK_SIZE_PX)
            val temp = File(directory, "$key.tmp")
            FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            temp.renameTo(file)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val ARTWORK_SIZE_PX = 512

        fun uriFor(context: Context, stationId: String): Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority("${context.packageName}.artwork")
            .appendPath("station")
            .appendPath(stationId)
            .build()
    }
}
