package dev.cybercore.cybermeta

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.random.Random

/** Biggest photo the app will load (whole file in memory). */
const val MAX_PHOTO_BYTES = 80L * 1024 * 1024

class PhotoTooLarge(size: Long) : Exception("This photo is ${size / (1024 * 1024)} MB; Cybermeta handles up to 80 MB.")

/** A photo as read from a content URI. */
class Loaded(
    val bytes: ByteArray,
    /** Android redacted the GPS before giving us the bytes. */
    val locationHidden: Boolean,
)

/**
 * An embedded preview found inside EXIF IFD1 / JFIF.
 * Frequently contains the full uncropped image even after the user
 * cropped the photo, or reveals unblurred details from the original camera shot.
 */
data class GhostThumbnail(
    val bytes: ByteArray,
    val bitmap: ImageBitmap?,
    val width: Int,
    val height: Int,
    val mainWidth: Int,
    val mainHeight: Int,
    val isCroppingLeak: Boolean,
    val description: String,
)

/**
 * Reading, writing and sharing photos. Everything goes through content
 * URIs: the app has no storage permission and no network permission.
 */
object PhotoIO {
    fun canSeeLocation(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isMedia(uri: Uri) = uri.authority == MediaStore.AUTHORITY

    /**
     * Read a photo. Media-store photos come back with GPS zeroed unless the
     * app holds ACCESS_MEDIA_LOCATION and asks for the original, so try
     * that first and remember when Android hid the location.
     */
    fun read(context: Context, uri: Uri): Loaded {
        val resolver = context.contentResolver
        if (isMedia(uri)) {
            val original = if (canSeeLocation(context)) {
                runCatching { resolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { readCapped(it) } }.getOrNull()
            } else {
                null
            }
            if (original != null) return Loaded(original, locationHidden = false)
        }
        val bytes = resolver.openInputStream(uri)?.use { readCapped(it) } ?: error("Couldn't open the photo.")
        return Loaded(bytes, locationHidden = isMedia(uri))
    }

    private fun readCapped(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_PHOTO_BYTES) throw PhotoTooLarge(total)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "photo"

    /** Parse JPEG dimensions directly from frame headers (SOF0/SOF2). Pure JVM-compatible. */
    fun jpegDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 8 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) { i++; continue }
            val marker = bytes[i + 1].toInt() and 0xFF
            if (marker == 0xD9 || marker == 0xDA) break // EOI or SOS
            val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
            if (len < 2 || i + 2 + len > bytes.size) break
            // SOF0 (0xC0), SOF1 (0xC1), SOF2 (0xC2)
            if (marker in 0xC0..0xC3 && i + 8 < bytes.size) {
                val h = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                val w = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                return Pair(w, h)
            }
            i += 2 + len
        }
        return null
    }

    /**
     * Inspect embedded EXIF / JFIF thumbnails. If the main photo's aspect ratio
     * deviates from the thumbnail's ratio by >8%, the photo was cropped while the
     * embedded thumbnail still exposes the uncropped scene.
     */
    fun findGhostThumbnail(bytes: ByteArray): GhostThumbnail? {
        val thumbBytes = runCatching {
            val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
            if (exif.hasThumbnail()) exif.thumbnailBytes else null
        }.getOrNull() ?: return null

        val thumbDim = jpegDimensions(thumbBytes) ?: return null
        val (thumbW, thumbH) = thumbDim

        val mainDim = jpegDimensions(bytes) ?: runCatching {
            val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            var size: Pair<Int, Int>? = null
            ImageDecoder.decodeBitmap(src) { _, info, _ ->
                size = Pair(info.size.width, info.size.height)
            }
            size
        }.getOrNull()

        val mainW = mainDim?.first ?: 0
        val mainH = mainDim?.second ?: 0

        val isCropLeak = if (mainW > 0 && mainH > 0 && thumbW > 0 && thumbH > 0) {
            val mainMax = maxOf(mainW, mainH).toDouble()
            val mainMin = minOf(mainW, mainH).toDouble()
            val thumbMax = maxOf(thumbW, thumbH).toDouble()
            val thumbMin = minOf(thumbW, thumbH).toDouble()
            val mainRatio = mainMax / mainMin
            val thumbRatio = thumbMax / thumbMin
            kotlin.math.abs(mainRatio - thumbRatio) / mainRatio > 0.08
        } else {
            false
        }

        val desc = if (isCropLeak) {
            "Aspect ratio mismatch (${thumbW}×${thumbH} thumbnail vs ${mainW}×${mainH} photo). This photo was cropped, but the embedded thumbnail still preserves the original uncropped scene."
        } else {
            "Embedded preview (${thumbW}×${thumbH}). May reveal original, unedited, or unblurred camera details."
        }

        return GhostThumbnail(
            bytes = thumbBytes,
            bitmap = thumbnail(thumbBytes),
            width = thumbW,
            height = thumbH,
            mainWidth = mainW,
            mainHeight = mainH,
            isCroppingLeak = isCropLeak,
            description = desc,
        )
    }

    /** A small preview, rotated upright. HEIC decodes too. */
    fun thumbnail(bytes: ByteArray, maxSide: Int = 480): ImageBitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val side = maxOf(info.size.width, info.size.height)
            if (side > maxSide) decoder.setTargetSampleSize(Integer.highestOneBit(side / maxSide).coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    }.getOrNull()

    /**
     * Re-encode a photo the engine can't scrub losslessly (HEIC, AVIF, ...)
     * as a JPEG. The decoder applies the rotation; the new file carries no
     * metadata at all, and the engine still checks it afterwards.
     */
    fun toJpeg(bytes: ByteArray, quality: Int = 95): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    fun mimeFor(format: String): String = when (format) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    fun extFor(format: String): String = when (format) {
        "png" -> "png"
        "webp" -> "webp"
        else -> "jpg"
    }

    /**
     * The name a clean copy goes out under. Camera names like
     * IMG_20261019_143022 give away the capture time, so by default the
     * copy gets a random one.
     */
    fun outputName(original: String, format: String, anonymous: Boolean): String {
        val ext = extFor(format)
        if (anonymous) return "photo_" + Random.nextInt(0x100000, 0xFFFFFF).toString(16) + "." + ext
        val stem = original.substringBeforeLast('.').ifBlank { "photo" }
        return "${stem}_clean.$ext"
    }

    /** Save into Pictures/Cybermeta. Needs no permission on Android 10+. */
    fun saveToPictures(context: Context, bytes: ByteArray, name: String, mime: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Cybermeta")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Android refused to create the file.")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("Couldn't write the file.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** A private copy the share sheet can read, served by the FileProvider. */
    fun shareable(context: Context, bytes: ByteArray, name: String): Uri {
        val dir = File(context.cacheDir, "clean").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    /** Forget copies from earlier sessions. */
    fun clearShared(context: Context) {
        File(context.cacheDir, "clean").listFiles()?.forEach { it.delete() }
    }

    fun canReplace(uri: Uri) = isMedia(uri) && !uri.path.orEmpty().contains("/picker/")

    /** Overwrite an original after Android's own edit prompt granted it. */
    fun replace(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Couldn't write the original.")
    }
}
