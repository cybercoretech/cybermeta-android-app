package dev.cybercore.cybermeta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uniffi.cybermeta_ffi.CybermetaException
import uniffi.cybermeta_ffi.KeepOptions

/**
 * The real Rust engine through the generated bindings, loaded by JNA from
 * the host build (Gradle points jna.library.path at rust/target/release).
 * leaky.jpg was written by exiftool: GPS, serial, owner, XMP creator/city,
 * IPTC by-line, a comment and a motion-photo trailer.
 */
class EngineTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    @Test
    fun auditFindsWhatThePhotoGivesAway() {
        val r = Engine.audit(fixture("leaky.jpg"))
        assertEquals("jpeg", r.format)
        assertEquals("high", r.risk)
        val gps = assertNotNullAnd(r.gps)
        assertEquals(45.5, gps.latitude, 1e-6)
        assertEquals(-122.6, gps.longitude, 1e-6)
        val labels = r.findings.map { it.label }
        for (want in listOf("Camera serial number", "Camera owner", "City", "By-line (creator)", "Comment")) {
            assertTrue("missing $want in $labels", want in labels)
        }
        assertTrue(r.findings.any { it.category == "hidden_data" })
    }

    @Test
    fun shareSafeLeavesOnlyRotation() {
        val c = Engine.scrub(fixture("leaky.jpg"), KeepOptions(icc = true, orientation = true, date = false))
        assertEquals("high", c.before.risk)
        assertNull("after: ${c.after.findings}", c.after.risk)
        assertTrue(c.after.findings.isEmpty())
        assertTrue(c.removed.any { it.kind == "XMP" })
        assertTrue(c.removed.any { it.kind == "data after end of image" })
        val text = String(c.bytes, Charsets.ISO_8859_1)
        for (leak in listOf("Test Person", "Springfield", "R5CX00TEST", "home address", "MotionPhoto")) {
            assertFalse("$leak survived", leak in text)
        }
    }

    @Test
    fun locationOnlyKeepsTheRest() {
        val c = Engine.stripLocation(fixture("leaky.jpg"))
        assertTrue(c.after.findings.none { it.category == "location" })
        assertTrue(c.after.findings.any { it.label == "Camera serial number" })
    }

    @Test
    fun anonymousCameraAndShift() {
        val anon = Engine.anonymousCamera(fixture("leaky.jpg"))
        assertEquals("Generic", anon.after.findings.first { it.label == "Camera make" }.value)
        val seconds = Engine.parseShift("-1d")
        assertEquals(-86_400L, seconds)
        assertEquals("-1d", Engine.humanShift(seconds))
        val shifted = Engine.shiftDates(fixture("leaky.jpg"), seconds)
        assertTrue(shifted.after.findings.first { it.label == "Taken" }.value.contains("09-30"))
    }

    @Test
    fun refusalsCarryAReadableReason() {
        try {
            Engine.scrub(fixture("plain.heic"), KeepOptions(icc = true, orientation = true, date = false))
            fail("HEIC should be refused")
        } catch (e: CybermetaException) {
            assertTrue(e.userMessage(), e.userMessage().contains("High efficiency"))
        }
        assertFalse(Engine.audit(fixture("plain.heic")).scrubbable)
        assertTrue(Engine.version().contains("engine"))
    }

    @Test
    fun outputNamesDontLeakTheCaptureTime() {
        val n = PhotoIO.outputName("IMG_20261019_143022.jpg", "jpeg", anonymous = true)
        assertTrue(n, n.matches(Regex("photo_[0-9a-f]{6}\\.jpg")))
        assertEquals("IMG_20261019_143022_clean.png", PhotoIO.outputName("IMG_20261019_143022.jpg", "png", anonymous = false))
    }

    @Test
    fun palettesAreGenerated() {
        val all = PaletteFamilies.flatMap { it.second }
        assertEquals(82, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(TagRedact, paletteById("nope"))
    }

    private fun <T> assertNotNullAnd(v: T?): T {
        assertNotNull(v)
        return v!!
    }
}
