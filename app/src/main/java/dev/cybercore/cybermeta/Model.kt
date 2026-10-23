package dev.cybercore.cybermeta

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import uniffi.cybermeta_ffi.Cleaned
import uniffi.cybermeta_ffi.KeepOptions
import uniffi.cybermeta_ffi.Report

enum class Screen { Home, Review, Detail, Done, Settings, Themes }

/** What "Clean" does. */
enum class Mode(val title: String, val blurb: String) {
    ShareSafe("Share-safe", "Everything goes. Colours and rotation stay."),
    LocationOnly("Location only", "Removes GPS and named places. Everything else stays."),
    Anonymous("Anonymous camera", "Everything goes, then the camera reads \"Generic Camera\"."),
    PixelsOnly("Pixels only", "Nothing but the picture: no colour profile, no rotation."),
    ShiftDates("Shift dates", "Moves every timestamp, so the time it was taken stays private."),
}

data class Item(
    val id: Long,
    val uri: Uri,
    val name: String,
    val report: Report? = null,
    val thumb: ImageBitmap? = null,
    val ghostThumb: GhostThumbnail? = null,
    val place: PlaceMatch? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** Android hid GPS from us (no media-location permission). */
    val locationHidden: Boolean = false,
    val result: Cleaned? = null,
    val resultError: String? = null,
    /** The photo had to be re-encoded (HEIC and friends). */
    val converted: Boolean = false,
    val savedTo: Uri? = null,
    val replaced: Boolean = false,
)

data class UiState(
    val screen: Screen = Screen.Home,
    val items: List<Item> = emptyList(),
    val detail: Long? = null,
    val mode: Mode = Mode.ShareSafe,
    val keep: KeepOptions = KeepOptions(icc = true, orientation = true, date = false),
    val shift: String = "-1d",
    val busy: Boolean = false,
    val message: String? = null,
    val palette: Palette = TagRedact,
    /** Shared in from another app: clean with the default mode and share straight back. */
    val quickShare: Boolean = false,
    val anonymousNames: Boolean = true,
    val convertUnsupported: Boolean = true,
    val canSeeLocation: Boolean = false,
    /** The current batch arrived through the share sheet. */
    val fromShare: Boolean = false,
) {
    val detailItem get() = items.firstOrNull { it.id == detail }
    val loaded get() = items.filter { it.report != null }
    val cleaned get() = items.filter { it.result != null }
    val highRisk get() = items.count { it.report?.risk == "high" }
    val canGoBack get() = screen != Screen.Home
}

fun Throwable.userMessage(): String = when (this) {
    is uniffi.cybermeta_ffi.CybermetaException.Refused -> reason
    else -> message ?: javaClass.simpleName
}

class AppModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("cybermeta", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(
            palette = paletteById(prefs.getString("palette", null)),
            mode = runCatching { Mode.valueOf(prefs.getString("mode", "") ?: "") }.getOrDefault(Mode.ShareSafe),
            keep = KeepOptions(
                icc = prefs.getBoolean("keep_icc", true),
                orientation = prefs.getBoolean("keep_orientation", true),
                date = prefs.getBoolean("keep_date", false),
            ),
            quickShare = prefs.getBoolean("quick_share", false),
            anonymousNames = prefs.getBoolean("anonymous_names", true),
            convertUnsupported = prefs.getBoolean("convert_unsupported", true),
            canSeeLocation = PhotoIO.canSeeLocation(app),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The bytes behind each item, kept out of the UI state. */
    private val originals = mutableMapOf<Long, ByteArray>()
    private var nextId = 1L
    /** Decoding thumbnails of 50 photos at once would run the phone out of memory. */
    private val gate = Semaphore(3)

    /** Set when quick share finished and the share sheet should open. */
    val pendingShare = MutableStateFlow<List<Uri>?>(null)

    private val ctx: Context get() = getApplication()

    fun refreshPermission() = _state.update { it.copy(canSeeLocation = PhotoIO.canSeeLocation(ctx)) }

    fun go(screen: Screen) = _state.update { it.copy(screen = screen, message = null) }

    /** System back: one level up. */
    fun back() = _state.update {
        it.copy(
            screen = when (it.screen) {
                Screen.Detail, Screen.Done -> Screen.Review
                Screen.Themes -> Screen.Settings
                else -> Screen.Home
            },
            message = null,
        )
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun open(uris: List<Uri>, fromShare: Boolean) {
        if (uris.isEmpty()) return
        PhotoIO.clearShared(ctx)
        originals.clear()
        val items = uris.distinct().take(100).map { Item(id = nextId++, uri = it, name = PhotoIO.displayName(ctx, it)) }
        _state.update { it.copy(items = items, screen = Screen.Review, fromShare = fromShare, detail = null, message = null) }
        viewModelScope.launch {
            items.map { item -> launch { load(item) } }.forEach { it.join() }
            if (fromShare && _state.value.quickShare) cleanAll(thenShare = true)
        }
    }

    private suspend fun load(item: Item) = gate.withPermit {
        val update = withContext(Dispatchers.IO) {
            runCatching {
                val loaded = PhotoIO.read(ctx, item.uri)
                originals[item.id] = loaded.bytes
                val baseReport = Engine.audit(loaded.bytes)
                val ghost = PhotoIO.findGhostThumbnail(loaded.bytes)
                val report = if (ghost?.isCroppingLeak == true) {
                    val findings = baseReport.findings.map { f ->
                        if (f.category == "thumbnail") {
                            uniffi.cybermeta_ffi.Finding(
                                severity = "high",
                                category = "thumbnail",
                                source = f.source,
                                label = "Ghost thumbnail (cropping leak)",
                                value = ghost.description,
                            )
                        } else f
                    }.let { list ->
                        if (list.none { it.category == "thumbnail" }) {
                            list + uniffi.cybermeta_ffi.Finding(
                                severity = "high",
                                category = "thumbnail",
                                source = "EXIF",
                                label = "Ghost thumbnail (cropping leak)",
                                value = ghost.description,
                            )
                        } else list
                    }
                    uniffi.cybermeta_ffi.Report(
                        format = baseReport.format,
                        formatName = baseReport.formatName,
                        risk = "high",
                        findings = findings,
                        blocks = baseReport.blocks,
                        gps = baseReport.gps,
                        scrubbable = baseReport.scrubbable,
                        damaged = baseReport.damaged,
                    )
                } else {
                    baseReport
                }
                OfflineGeocoder.init(ctx)
                val place = report.gps?.let { g -> OfflineGeocoder.findNearest(g.latitude, g.longitude) }
                val finalReport = if (place != null) {
                    val locationFinding = uniffi.cybermeta_ffi.Finding(
                        severity = "high",
                        category = "location",
                        source = "GPS",
                        label = "Resolved location (offline)",
                        value = "${place.formatted()} (~${"%.1f".format(place.distanceKm)} km)",
                    )
                    uniffi.cybermeta_ffi.Report(
                        format = report.format,
                        formatName = report.formatName,
                        risk = report.risk ?: "high",
                        findings = listOf(locationFinding) + report.findings,
                        blocks = report.blocks,
                        gps = report.gps,
                        scrubbable = report.scrubbable,
                        damaged = report.damaged,
                    )
                } else {
                    report
                }
                item.copy(
                    report = finalReport,
                    thumb = PhotoIO.thumbnail(loaded.bytes),
                    ghostThumb = ghost,
                    place = place,
                    loading = false,
                    locationHidden = loaded.locationHidden,
                )
            }.getOrElse { item.copy(loading = false, error = it.userMessage()) }
        }
        replace(update)
    }

    private fun replace(item: Item) = _state.update { s -> s.copy(items = s.items.map { if (it.id == item.id) item else it }) }

    fun remove(id: Long) {
        originals.remove(id)
        _state.update { s ->
            val items = s.items.filterNot { it.id == id }
            s.copy(items = items, screen = if (items.isEmpty()) Screen.Home else s.screen)
        }
    }

    fun showDetail(id: Long) = _state.update { it.copy(detail = id, screen = Screen.Detail) }

    fun setMode(mode: Mode) {
        prefs.edit { putString("mode", mode.name) }
        _state.update { it.copy(mode = mode) }
    }

    fun setKeep(keep: KeepOptions) {
        prefs.edit {
            putBoolean("keep_icc", keep.icc)
            putBoolean("keep_orientation", keep.orientation)
            putBoolean("keep_date", keep.date)
        }
        _state.update { it.copy(keep = keep) }
    }

    fun setShift(text: String) = _state.update { it.copy(shift = text) }

    fun setPalette(p: Palette) {
        prefs.edit { putString("palette", p.id) }
        _state.update { it.copy(palette = p) }
    }

    fun setQuickShare(on: Boolean) {
        prefs.edit { putBoolean("quick_share", on) }
        _state.update { it.copy(quickShare = on) }
    }

    fun setAnonymousNames(on: Boolean) {
        prefs.edit { putBoolean("anonymous_names", on) }
        _state.update { it.copy(anonymousNames = on) }
    }

    fun setConvertUnsupported(on: Boolean) {
        prefs.edit { putBoolean("convert_unsupported", on) }
        _state.update { it.copy(convertUnsupported = on) }
    }

    /** Clean every loaded photo with the current mode. */
    fun cleanAll(thenShare: Boolean = false) {
        val s = _state.value
        val shiftSeconds = if (s.mode == Mode.ShiftDates) {
            runCatching { Engine.parseShift(s.shift) }.getOrElse {
                _state.update { st -> st.copy(message = it.userMessage()) }
                return
            }
        } else {
            0L
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            for (item in s.loaded) {
                val bytes = originals[item.id] ?: continue
                val update = withContext(Dispatchers.Default) { clean(item, bytes, s, shiftSeconds) }
                replace(update)
            }
            val done = _state.value.cleaned
            val failed = _state.value.items.count { it.resultError != null }
            _state.update {
                it.copy(
                    busy = false,
                    screen = if (done.isEmpty()) it.screen else Screen.Done,
                    message = if (failed > 0) "$failed photo(s) couldn't be cleaned; tap them for the reason." else null,
                )
            }
            if (thenShare && done.isNotEmpty()) share()
        }
    }

    private fun clean(item: Item, original: ByteArray, s: UiState, shiftSeconds: Long): Item {
        var bytes = original
        var converted = false
        if (item.report?.scrubbable == false) {
            if (!s.convertUnsupported) {
                return item.copy(result = null, resultError = "${item.report.formatName} can't be cleaned without re-encoding. Turn on \"Convert HEIC and other formats\" in Settings.")
            }
            bytes = runCatching { PhotoIO.toJpeg(bytes) }.getOrElse {
                return item.copy(result = null, resultError = "Couldn't convert ${item.report.formatName}: ${it.userMessage()}")
            }
            converted = true
        }
        return runCatching {
            val result = when (s.mode) {
                Mode.ShareSafe -> Engine.scrub(bytes, s.keep)
                Mode.LocationOnly -> Engine.stripLocation(bytes)
                Mode.Anonymous -> Engine.anonymousCamera(bytes)
                Mode.PixelsOnly -> Engine.scrub(bytes, KeepOptions(icc = false, orientation = false, date = false))
                Mode.ShiftDates -> Engine.shiftDates(bytes, shiftSeconds)
            }
            item.copy(result = result, resultError = null, converted = converted, savedTo = null, replaced = false)
        }.getOrElse { item.copy(result = null, resultError = it.userMessage(), converted = converted) }
    }

    private fun outputName(item: Item): String =
        PhotoIO.outputName(item.name, item.result?.after?.format ?: "jpeg", _state.value.anonymousNames)

    /** Clean copies, ready for the share sheet. */
    fun shareUris(): List<Uri> = _state.value.cleaned.mapNotNull { item ->
        val r = item.result ?: return@mapNotNull null
        runCatching { PhotoIO.shareable(ctx, r.bytes, outputName(item)) }.getOrNull()
    }

    fun share() {
        pendingShare.value = shareUris()
    }

    fun saveAll() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            var saved = 0
            var failed: String? = null
            for (item in _state.value.cleaned) {
                val r = item.result ?: continue
                val uri = withContext(Dispatchers.IO) {
                    runCatching { PhotoIO.saveToPictures(ctx, r.bytes, outputName(item), PhotoIO.mimeFor(r.after.format)) }
                }
                uri.onSuccess { replace(item.copy(savedTo = it)); saved++ }.onFailure { failed = it.userMessage() }
            }
            _state.update {
                it.copy(busy = false, message = failed?.let { f -> "Saved $saved; one failed: $f" } ?: "Saved $saved to Pictures/Cybermeta")
            }
        }
    }

    /** Originals that can be overwritten (media-store photos, not picker copies). */
    fun replaceable(): List<Uri> = _state.value.cleaned.filter { PhotoIO.canReplace(it.uri) && !it.converted }.map { it.uri }

    /** Called once Android's own "allow editing" prompt was accepted. */
    fun replaceGranted() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            var done = 0
            var failed: String? = null
            for (item in _state.value.cleaned.filter { PhotoIO.canReplace(it.uri) && !it.converted }) {
                val r = item.result ?: continue
                withContext(Dispatchers.IO) { runCatching { PhotoIO.replace(ctx, item.uri, r.bytes) } }
                    .onSuccess { replace(item.copy(replaced = true)); done++ }
                    .onFailure { failed = it.userMessage() }
            }
            _state.update {
                it.copy(busy = false, message = failed?.let { f -> "Replaced $done; one failed: $f" } ?: "Replaced $done original(s)")
            }
        }
    }

    fun note(message: String) = _state.update { it.copy(message = message) }
}

/** The Rust engine, behind one seam so tests and the UI share it. */
object Engine {
    fun audit(bytes: ByteArray): Report = uniffi.cybermeta_ffi.auditPhoto(bytes)
    fun scrub(bytes: ByteArray, keep: KeepOptions): Cleaned = uniffi.cybermeta_ffi.scrubPhoto(bytes, keep)
    fun stripLocation(bytes: ByteArray): Cleaned = uniffi.cybermeta_ffi.stripLocation(bytes)
    fun anonymousCamera(bytes: ByteArray): Cleaned = uniffi.cybermeta_ffi.anonymousCamera(bytes)
    fun shiftDates(bytes: ByteArray, seconds: Long): Cleaned = uniffi.cybermeta_ffi.shiftDates(bytes, seconds)
    fun parseShift(text: String): Long = uniffi.cybermeta_ffi.parseShift(text)
    fun humanShift(seconds: Long): String = uniffi.cybermeta_ffi.humanShift(seconds)
    fun version(): String = uniffi.cybermeta_ffi.engineVersion()
}
