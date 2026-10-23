package dev.cybercore.cybermeta

import android.content.Context
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A geographic locality resolved entirely on-device from embedded coordinates.
 */
data class PlaceMatch(
    val name: String,
    val admin: String,
    val countryCode: String,
    val countryName: String,
    val distanceKm: Double,
) {
    fun formatted(): String {
        val parts = mutableListOf<String>()
        parts.add(name)
        if (admin.isNotBlank() && admin != name) parts.add(admin)
        if (countryName.isNotBlank()) parts.add(countryName) else if (countryCode.isNotBlank()) parts.add(countryCode)
        return parts.joinToString(", ")
    }
}

/**
 * High-speed offline reverse geocoder. Scans ~34,000 world cities from
 * assets/places.bin in ~1-2 milliseconds with zero network requests.
 */
object OfflineGeocoder {
    private var buffer: ByteBuffer? = null
    private var cityCount = 0
    private var stringTableOffset = 0

    @Synchronized
    fun init(stream: InputStream) {
        if (buffer != null) return
        val bytes = stream.readBytes()
        loadBytes(bytes)
    }

    fun init(context: Context) {
        if (buffer != null) return
        runCatching {
            context.assets.open("places.bin").use { init(it) }
        }
    }

    private fun loadBytes(bytes: ByteArray) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buf.get(magic)
        if (String(magic) != "CYPL") return
        val version = buf.short.toInt()
        if (version != 1) return
        cityCount = buf.int
        stringTableOffset = 4 + 2 + 4 + cityCount * 12
        buffer = buf
    }

    fun findNearest(lat: Double, lon: Double): PlaceMatch? {
        val buf = buffer ?: return null
        if (cityCount == 0) return null

        val cosLat = cos(Math.toRadians(lat)).toFloat()
        val targetLat = lat.toFloat()
        val targetLon = lon.toFloat()

        var bestDistSq = Float.MAX_VALUE
        var bestStrOffset = -1

        val recordsBase = 4 + 2 + 4
        for (i in 0 until cityCount) {
            val offset = recordsBase + i * 12
            val cLat = buf.getFloat(offset)
            val cLon = buf.getFloat(offset + 4)

            val dLat = cLat - targetLat
            val dLon = (cLon - targetLon) * cosLat
            val distSq = dLat * dLat + dLon * dLon

            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestStrOffset = buf.getInt(offset + 8)
            }
        }

        if (bestStrOffset < 0) return null

        val raw = buf.array()
        val start = stringTableOffset + bestStrOffset
        var end = start
        while (end < raw.size && raw[end] != 0.toByte()) {
            end++
        }
        val text = String(raw, start, end - start, Charsets.UTF_8)
        val parts = text.split("\t")
        val name = parts.getOrNull(0) ?: ""
        val admin = parts.getOrNull(1) ?: ""
        val country = parts.getOrNull(2) ?: ""
        val countryName = runCatching { Locale.Builder().setRegion(country).build().displayCountry }.getOrDefault(country)
        val distKm = sqrt(bestDistSq.toDouble()) * 111.0

        return PlaceMatch(
            name = name,
            admin = admin,
            countryCode = country,
            countryName = countryName,
            distanceKm = distKm,
        )
    }
}
