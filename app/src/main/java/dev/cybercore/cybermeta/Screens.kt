package dev.cybercore.cybermeta

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.cybermeta_ffi.Finding
import uniffi.cybermeta_ffi.KeepOptions

/** What the screens can ask the activity to do. */
interface Actions {
    fun pickPhotos()
    fun pickFiles()
    fun askLocationPermission()
    fun share()
    fun replaceOriginals()
    fun openMap(lat: Double, lon: Double)
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick, modifier.fillMaxWidth().height(54.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.cyan, contentColor = P.bg),
    ) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick, modifier.fillMaxWidth().height(50.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = P.cyan)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = P.fg)
    }
}

// ------------------------------------------------------------------ Home --

@Composable
fun HomeScreen(s: UiState, actions: Actions) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ScrubMark(150.dp)
                Spacer(Modifier.height(4.dp))
                Text("CYBERMETA", style = MaterialTheme.typography.displaySmall, color = P.fg)
                Text(
                    "See what your photos give away.\nThen make it disappear.",
                    color = P.muted, style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("PICK PHOTOS", Icons.Outlined.PhotoLibrary, actions::pickPhotos)
                SecondaryButton("BROWSE FILES", Icons.Outlined.FolderOpen, actions::pickFiles)
            }
        }
        if (!s.canSeeLocation) {
            item {
                Panel(accent = P.orange) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, null, tint = P.orange)
                        Spacer(Modifier.width(10.dp))
                        Text("Let Cybermeta see photo locations", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Android hides GPS from apps unless you allow it, so Cybermeta would report \"no location\" for photos that have one. " +
                            "Cleaned copies are safe either way; this is so the report tells you the truth.",
                        color = P.muted, fontSize = 13.sp,
                    )
                    TextButton(actions::askLocationPermission) { Text("ALLOW", color = P.orange) }
                }
            }
        }
        item { Feature(Icons.Outlined.CloudOff, "No internet permission", "Cybermeta has no network access at all. Your photos can't leave the phone through it.") }
        item { Feature(Icons.Outlined.VerifiedUser, "Verified clean", "Every cleaned copy is re-read by a second, independent parser. If anything is left, you get an error, not a leaky photo.") }
        item { Feature(Icons.Outlined.HighQuality, "No quality loss", "The picture is copied byte for byte. Only the metadata around it goes.") }
        item { Feature(Icons.Outlined.Share, "Works from your gallery", "Share photos to Cybermeta from Gallery or Photos, then share the clean copies on.") }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(P.cyan.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = P.cyan) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = P.fg)
                Text(body, color = P.muted, fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Review --

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(s: UiState, vm: AppModel) {
    Column {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Summary(s) }
            item {
                SectionLabel("Clean mode")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Mode.entries.forEach { m ->
                        FilterChip(
                            selected = s.mode == m, onClick = { vm.setMode(m) },
                            label = { Text(m.title, fontFamily = Mono, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = P.cyan.copy(alpha = .2f), selectedLabelColor = P.cyan),
                        )
                    }
                }
                Text(s.mode.blurb, color = P.muted, fontSize = 13.sp)
                if (s.mode == Mode.ShareSafe) KeepToggles(s.keep, vm::setKeep)
                if (s.mode == Mode.ShiftDates) {
                    OutlinedTextField(
                        s.shift, vm::setShift, Modifier.fillMaxWidth().padding(top = 6.dp),
                        label = { Text("Shift by (e.g. -1d, +3h30m)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                }
            }
            item { SectionLabel("Photos") }
            items(s.items, key = { it.id }) { item -> PhotoRow(item, { vm.showDetail(item.id) }, { vm.remove(item.id) }) }
        }
        Box(Modifier.background(P.bg.copy(alpha = .95f)).padding(16.dp)) {
            val ready = s.loaded.size
            PrimaryButton(
                if (s.busy) "CLEANING…" else "CLEAN $ready PHOTO${if (ready == 1) "" else "S"}",
                Icons.Outlined.VerifiedUser, { vm.cleanAll() }, enabled = !s.busy && ready > 0,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(s: UiState) {
    val findings = s.items.flatMap { it.report?.findings.orEmpty() }
    val loading = s.items.count { it.loading }
    Panel(accent = if (s.highRisk > 0) P.red else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${s.items.size} photo${if (s.items.size == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        loading > 0 -> "Reading $loading…"
                        findings.isEmpty() -> "Nothing found. Already clean."
                        else -> "${findings.size} things they give away"
                    },
                    color = P.muted,
                )
            }
            if (loading > 0) CircularProgressIndicator(Modifier.size(28.dp), color = P.cyan, strokeWidth = 3.dp)
            else RiskBadge(s.items.mapNotNull { it.report?.risk }.maxByOrNull { listOf("low", "medium", "high").indexOf(it) })
        }
        val byCat = findings.groupBy { it.category }
        if (byCat.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categoryOrder.filter { it in byCat }.forEach { cat ->
                    val list = byCat.getValue(cat)
                    CategoryChip(cat, list.size, list.map { it.severity }.maxBy { listOf("low", "medium", "high").indexOf(it) })
                }
            }
        }
        if (s.items.any { it.locationHidden && it.report?.gps == null }) {
            Text("⚠ Android hid GPS from some photos; allow media location for a full report.", color = P.orange, fontSize = 12.sp)
        }
    }
}

@Composable
fun KeepToggles(keep: KeepOptions, onChange: (KeepOptions) -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        ToggleRow("Keep colour profile", "Phones shoot in Display P3; without it colours look washed out.", keep.icc) { onChange(keep.copy(icc = it)) }
        ToggleRow("Keep rotation", "So portrait photos don't turn sideways.", keep.orientation) { onChange(keep.copy(orientation = it)) }
        ToggleRow("Keep capture date", "Off by default: when a photo was taken is personal too.", keep.date) { onChange(keep.copy(date = it)) }
    }
}

@Composable
fun ToggleRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P.fg)
            if (body != null) Text(body, color = P.muted, fontSize = 12.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = P.cyan, checkedThumbColor = P.bg))
    }
}

@Composable
private fun PhotoRow(item: Item, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(P.panel)
            .border(1.dp, P.line, RoundedCornerShape(14.dp))
            .clickable(enabled = item.report != null || item.error != null, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(item, 64)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = P.fg)
            when {
                item.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(P.cyan); Spacer(Modifier.width(6.dp)); Text("reading…", color = P.muted, fontSize = 12.sp)
                }
                item.error != null -> Text(item.error, color = P.red, fontSize = 12.sp, maxLines = 2)
                item.report != null -> {
                    val top = item.report.findings.take(2).joinToString(" · ") { it.label }
                    Text(
                        if (top.isEmpty()) "${item.report.formatName} · nothing found" else "${item.report.formatName} · $top",
                        color = P.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RiskBadge(item.report.risk, short = true)
                        if (item.ghostThumb?.isCroppingLeak == true) {
                            Text(
                                "CROPPED LEAK",
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(P.red.copy(alpha = .16f))
                                    .border(1.dp, P.red.copy(alpha = .8f), RoundedCornerShape(50))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                color = P.red,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        IconButton(onRemove) { Icon(Icons.Outlined.Close, "Remove", tint = P.muted) }
    }
}

@Composable
fun Thumb(item: Item, sizeDp: Int) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(sizeDp.dp).clip(shape).background(P.bg).border(1.dp, P.line, shape), contentAlignment = Alignment.Center) {
        if (item.thumb != null) Image(item.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        else ScrubMark((sizeDp * .8f).dp, animate = item.loading)
    }
}

// ---------------------------------------------------------------- Detail --

@Composable
fun DetailScreen(s: UiState, actions: Actions) {
    val item = s.detailItem ?: return
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(P.panel)) {
                if (item.thumb != null) Image(item.thumb, item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                RiskBadge(item.report?.risk, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
        }
        item {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            item.report?.let { Text("${it.formatName} · ${it.findings.size} findings · ${it.blocks.size} metadata blocks", color = P.muted, fontSize = 13.sp) }
        }
        item.error?.let { e -> item { Panel(accent = P.red) { Text(e, color = P.red) } } }
        item.resultError?.let { e -> item { Panel(accent = P.red) { Text("Not cleaned: $e", color = P.red) } } }
        val report = item.report ?: return@LazyColumn
        if (!report.scrubbable) {
            item {
                Panel(accent = P.orange) {
                    Text("${report.formatName} can't be cleaned losslessly", fontWeight = FontWeight.Bold, color = P.orange)
                    Text(
                        "Cybermeta will convert it to a high-quality JPEG first (Settings → Convert). " +
                            "On Samsung, turn off Camera → Settings → Advanced picture options → High efficiency pictures to shoot JPEG.",
                        color = P.muted, fontSize = 13.sp,
                    )
                }
            }
        }
        if (item.locationHidden && report.gps == null) {
            item { Panel(accent = P.orange) { Text("Android hid this photo's location from Cybermeta. Allow media location on the home screen to see it.", color = P.orange, fontSize = 13.sp) } }
        }
        item.ghostThumb?.let { ghost ->
            item { GhostThumbnailCard(item, ghost) }
        }
        report.gps?.let { g ->
            item {
                Panel(accent = P.red) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(categoryIcon("location"), null, tint = P.red)
                        Spacer(Modifier.width(8.dp))
                        Text("This photo says where you were", fontWeight = FontWeight.Bold, color = P.red)
                    }
                    item.place?.let { place ->
                        Text(place.formatted(), style = MaterialTheme.typography.titleMedium, color = P.fg, modifier = Modifier.padding(top = 4.dp))
                        Text("Near ${place.name} (~${"%.1f".format(place.distanceKm)} km) · Resolved offline without internet", color = P.muted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text("%.6f, %.6f".format(g.latitude, g.longitude), fontFamily = Mono, fontSize = 22.sp, color = P.fg)
                    Text("Anyone you send the original to can put a pin on this spot.", color = P.muted, fontSize = 13.sp)
                    TextButton({ actions.openMap(g.latitude, g.longitude) }) {
                        Icon(Icons.Outlined.Map, null, tint = P.cyan); Spacer(Modifier.width(6.dp)); Text("SHOW ON A MAP", color = P.cyan)
                    }
                }
            }
        }
        if (report.findings.isEmpty()) {
            item { Panel(accent = P.green) { Text("✔ Nothing found. This photo is already clean.", color = P.green) } }
        }
        val grouped = report.findings.groupBy { it.category }
        categoryOrder.filter { it in grouped }.forEach { cat ->
            item(key = cat) { FindingGroup(cat, grouped.getValue(cat)) }
        }
        if (report.blocks.isNotEmpty()) {
            item {
                SectionLabel("What's inside the file")
                Panel {
                    report.blocks.forEach { b ->
                        Row {
                            Text(b.kind, Modifier.weight(1f), color = P.fg, fontSize = 13.sp)
                            Text("${b.label} · ${humanBytes(b.bytes.toLong())}", color = P.muted, fontSize = 12.sp, fontFamily = Mono)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingGroup(category: String, findings: List<Finding>) {
    val worst = findings.maxBy { listOf("low", "medium", "high").indexOf(it.severity) }.severity
    val c = severityColor(P, worst)
    Panel(accent = c.copy(alpha = .6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(category), null, tint = c)
            Spacer(Modifier.width(8.dp))
            Text(categoryTitle(category), fontWeight = FontWeight.Bold, color = P.fg, modifier = Modifier.weight(1f))
            Text("${findings.size}", color = c, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
        findings.forEach { f ->
            Column(Modifier.padding(start = 32.dp)) {
                Row {
                    Text(f.label, color = P.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(f.source, color = P.purple, fontSize = 11.sp, fontFamily = Mono)
                }
                Text(f.value, color = severityColor(P, f.severity).copy(alpha = .9f), fontSize = 13.sp, fontFamily = Mono, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GhostThumbnailCard(item: Item, ghost: GhostThumbnail) {
    val accent = if (ghost.isCroppingLeak) P.red else P.orange
    Panel(accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ghost.isCroppingLeak) Icons.Outlined.Warning else Icons.Outlined.PhotoCamera, null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Text(
                if (ghost.isCroppingLeak) "Cropping leak detected" else "Embedded preview thumbnail",
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (ghost.isCroppingLeak) "LEAK" else "PREVIEW",
                color = accent,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }
        Text(ghost.description, color = P.muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CURRENT CROP", color = P.muted, fontSize = 10.sp, fontFamily = Mono)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(P.bg)
                        .border(1.dp, P.line, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.thumb != null) {
                        Image(item.thumb, "Current image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (ghost.mainWidth > 0) "${ghost.mainWidth}×${ghost.mainHeight}" else "photo",
                    color = P.muted,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EMBEDDED THUMB", color = accent, fontSize = 10.sp, fontFamily = Mono)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(P.bg)
                        .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ghost.bitmap != null) {
                        Image(ghost.bitmap, "Embedded thumbnail", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("${ghost.width}×${ghost.height}", color = accent, fontSize = 11.sp, fontFamily = Mono)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("${ghost.width}×${ghost.height}", color = accent, fontSize = 11.sp, fontFamily = Mono)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Cybermeta strips this hidden preview completely without touching your image pixels.",
            color = P.fg,
            fontSize = 12.sp,
        )
    }
}

// ------------------------------------------------------------------ Done --

@Composable
fun DoneScreen(s: UiState, vm: AppModel, actions: Actions) {
    val cleaned = s.cleaned
    val cleared = cleaned.sumOf { (it.result!!.before.findings.size - it.result.after.findings.size).coerceAtLeast(0) }
    val bytes = cleaned.sumOf { r -> r.result!!.removed.sumOf { it.bytes.toLong() } }
    val pop = remember { Animatable(.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Column {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = P.green, modifier = Modifier.size(84.dp).scale(pop.value))
                    Text("CLEAN", style = MaterialTheme.typography.displaySmall, color = P.green)
                    Text(
                        "$cleared finding${if (cleared == 1) "" else "s"} removed from ${cleaned.size} photo${if (cleaned.size == 1) "" else "s"} · ${humanBytes(bytes)} of metadata",
                        color = P.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            items(s.items, key = { it.id }) { item ->
                AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 3 }) { ResultRow(item, vm) }
            }
        }
        Column(Modifier.background(P.bg.copy(alpha = .95f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("SHARE CLEAN COPIES", Icons.Outlined.Share, actions::share, enabled = !s.busy && cleaned.isNotEmpty())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("SAVE", Icons.Outlined.Save, vm::saveAll, Modifier.weight(1f), enabled = !s.busy)
                if (vm.replaceable().isNotEmpty()) {
                    SecondaryButton("REPLACE ORIGINALS", Icons.Outlined.SwapHoriz, actions::replaceOriginals, Modifier.weight(1.4f), enabled = !s.busy)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(item: Item, vm: AppModel) {
    val r = item.result
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(P.panel)
            .border(1.dp, if (r != null) P.green.copy(alpha = .4f) else P.red.copy(alpha = .5f), RoundedCornerShape(14.dp))
            .clickable { vm.showDetail(item.id) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(item, 48)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RiskBadge(r.before.risk, short = true)
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = P.muted, modifier = Modifier.padding(horizontal = 6.dp).size(16.dp))
                        RiskBadge(r.after.risk, short = true)
                    }
                } else {
                    Text(item.resultError ?: "not cleaned", color = P.red, fontSize = 12.sp, maxLines = 3)
                }
            }
        }
        if (r != null) {
            val removed = r.removed.joinToString(" · ") { it.kind }
            if (removed.isNotEmpty()) Text("removed: $removed", color = P.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            r.after.findings.takeIf { it.isNotEmpty() }?.let { left ->
                Text("kept: " + left.joinToString(" · ") { it.label }, color = P.cyan, fontSize = 12.sp)
            }
            if (item.converted) Text("Converted to JPEG (re-encoded) so it could be cleaned.", color = P.orange, fontSize = 12.sp)
            r.notes.forEach { Text(it, color = P.orange, fontSize = 12.sp) }
            when {
                item.replaced -> Text("✔ original replaced", color = P.green, fontSize = 12.sp)
                item.savedTo != null -> Text("✔ saved to Pictures/Cybermeta", color = P.green, fontSize = 12.sp)
            }
        }
    }
}
