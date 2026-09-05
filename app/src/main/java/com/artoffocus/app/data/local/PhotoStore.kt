package com.artoffocus.app.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The app's own copy of every picture a painting is made from.
 *
 * I12 — a completed painting is never destroyed. A gallery `content://` URI cannot carry that
 * promise: the grant is revocable, it does not survive a reboot, and the user can delete the
 * original from their gallery at any time. So a chosen photo is copied into app-private
 * storage at import, and the painting refers to that copy. Deleting the original afterwards
 * leaves the painting whole.
 *
 * Files live in `filesDir/paintings/`, which is private to the app and removed on uninstall.
 */
class PhotoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * Copies [source] into private storage and returns the reference to store on a painting,
     * or null if the picture could not be read (a revoked grant, a deleted original).
     */
    suspend fun import(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "${System.currentTimeMillis()}-${source.hashCode().toUInt()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target.absolutePath
        }.getOrNull()
    }

    /** Every imported picture, newest first, as absolute paths ready to render. */
    suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.map { it.absolutePath }.orEmpty()
    }

    /** Whether the file behind a stored reference is still present. */
    fun exists(ref: String): Boolean = File(ref).exists()

    /**
     * Width / height of a stored picture, or null if it cannot be read. Decoded with
     * `inJustDecodeBounds`, so this reads the header only and never allocates the bitmap —
     * safe to call while a screen is composing.
     */
    fun aspectRatioOf(ref: String): Float? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ref, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) null
        else options.outWidth.toFloat() / options.outHeight.toFloat()
    }.getOrNull()

    /**
     * Removes a picture from the library.
     *
     * Callers must not delete a picture that a completed painting still refers to — that
     * would destroy the painting's artwork, which I12 forbids. The library screen checks the
     * Museum before offering this.
     */
    suspend fun delete(ref: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(ref).delete() }.getOrDefault(false)
    }

    private companion object {
        const val DIR_NAME = "paintings"
    }
}
