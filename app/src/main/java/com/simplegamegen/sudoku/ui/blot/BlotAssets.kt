package com.simplegamegen.sudoku.ui.blot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/** The player's own pictures for themes: copied in from the photo picker, shrunk, and kept inside the app. */
object BlotAssets {
    private const val DIR = "blot_assets"
    /** Longest side of a stored picture, in pixels: plenty for a square or a creature. */
    private const val MAX_SIDE = 512
    private val cache = LruCache<String, ImageBitmap>(24)

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    /** Copies the picture at [uri] into the app, returning its stored name, or null if it couldn't be read. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(context, uri) ?: return@runCatching null
            val scale = minOf(1f, MAX_SIDE / max(bitmap.width, bitmap.height).toFloat())
            val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
            val name = "a" + UUID.randomUUID().toString().replace("-", "").take(16) + ".png"
            File(dir(context), name).outputStream().use { sized.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name
        }.getOrNull()
    }

    /** Decodes with the photo's own rotation applied and without loading a huge image whole. */
    private fun decode(context: Context, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE * 2) {
                    val f = MAX_SIDE * 2f / longest
                    decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }

    fun load(context: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        val file = File(dir(context), name)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()?.also { cache.put(name, it) }
    }

    /** Deletes stored pictures that no saved theme uses (for example, ones added to a theme that was never saved). */
    suspend fun sweep(context: Context, themes: List<BlotTheme>) = withContext(Dispatchers.IO) {
        val keep = themes.flatMap { it.assets.values }.toSet()
        dir(context).listFiles()?.forEach { if (it.name !in keep) { it.delete(); cache.remove(it.name) } }
    }
}

/** The picture a theme uses for [slot], loaded once; null when the theme draws that part itself. */
@Composable
fun rememberAsset(theme: BlotTheme, slot: AssetSlot): ImageBitmap? {
    val context = LocalContext.current
    val name = theme.assets[slot]
    return remember(name) { name?.let { BlotAssets.load(context, it) } }
}
