package dev.island.data.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.ContextCompat
import dev.island.R

/**
 * App icons for island events, cached.
 *
 * Drawables are never put into the domain event model; the renderer asks for the icon by package
 * name. Cache is small and bounded because only a handful of apps are on screen at a time.
 */
class AppIconProvider(private val context: Context) {

    private val cache = object : LruCache<String, Drawable>(CACHE_SIZE) {}
    private val missing = HashSet<String>()

    fun iconFor(packageName: String?): Drawable? {
        if (packageName.isNullOrBlank()) return null
        cache.get(packageName)?.let { return it }
        if (packageName in missing) return null
        return runCatching {
            val icon = context.packageManager.getApplicationIcon(packageName)
            cache.put(packageName, icon)
            icon
        }.getOrElse {
            // Package visibility restrictions (Android 11+) or an uninstalled app.
            missing.add(packageName)
            null
        }
    }

    /** Island's own mark, used when an event has no source app. */
    fun fallbackIcon(): Drawable? =
        runCatching { ContextCompat.getDrawable(context, R.drawable.ic_island_status) }.getOrNull()

    fun clear() {
        cache.evictAll()
        missing.clear()
    }

    companion object {
        private const val CACHE_SIZE = 24

        /** Display metrics helper so icon sizes scale with density, never with a fixed pixel value. */
        fun dpToPx(dp: Float, resources: Resources): Int =
            (dp * resources.displayMetrics.density + 0.5f).toInt()
    }
}

/** Tiny helper used by the per-app settings screen. */
fun PackageManager.safeLabel(packageName: String): String? = runCatching {
    getApplicationLabel(getApplicationInfo(packageName, 0)).toString()
}.getOrNull()
