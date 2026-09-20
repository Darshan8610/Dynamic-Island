package dev.island.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import dev.island.core.logging.IslandLogger

/**
 * Album art lives outside the domain model: the event carries an opaque [token] and the renderer
 * asks this cache for the bitmap. That keeps bitmaps out of the event queue (no large objects
 * churned through state diffs) and makes exactly one decode per track.
 *
 * Bounded: one entry is enough for the currently playing track plus the previous one during a
 * crossfade; anything older is recycled by the GC because we only hold soft references.
 */
class ArtworkCache(private val logger: IslandLogger) {

    private val entries = LinkedHashMap<String, java.lang.ref.SoftReference<Bitmap>>(MAX_ENTRIES, 0.75f, true)

    /** `package:mediaId-hash` — stable for a track, changes when the artwork changes. */
    fun tokenFor(controller: MediaController, metadata: MediaMetadata?): String? {
        val pkg = controller.packageName ?: return null
        val id = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?: metadata?.description?.mediaId
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: return null
        return "$pkg:${id.hashCode()}"
    }

    /** Decodes (once) and caches the artwork for [token]. Returns null when there is none. */
    fun artworkFor(token: String, source: () -> Bitmap?): Bitmap? {
        entries[token]?.get()?.let { return it }
        val bitmap = runCatching { source() }
            .onFailure { logger.w(TAG, "artwork decode failed", it) }
            .getOrNull() ?: return null
        val scaled = scaleDown(bitmap)
        entries[token] = java.lang.ref.SoftReference(scaled)
        evictIfNeeded()
        return scaled
    }

    fun peek(token: String?): Bitmap? = token?.let { entries[it]?.get() }

    fun clear() {
        entries.clear()
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_DIMENSION_PX && bitmap.height <= MAX_DIMENSION_PX) return bitmap
        val ratio = minOf(MAX_DIMENSION_PX.toFloat() / bitmap.width, MAX_DIMENSION_PX.toFloat() / bitmap.height)
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(bitmap, width, height, true) }.getOrDefault(bitmap)
    }

    private fun evictIfNeeded() {
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    companion object {
        private const val TAG = "ArtworkCache"
        private const val MAX_ENTRIES = 4

        /** 256px is plenty for a pill-sized island and keeps memory flat. */
        private const val MAX_DIMENSION_PX = 256
    }
}

/** Helpers for reading artwork out of [MediaMetadata] in the documented precedence order. */
object ArtworkReader {
    fun from(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
        val bytes = metadata.getByteArray(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getByteArray(MediaMetadata.METADATA_KEY_ART)
        if (bytes != null && bytes.isNotEmpty()) {
            return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
        return null
    }
}
