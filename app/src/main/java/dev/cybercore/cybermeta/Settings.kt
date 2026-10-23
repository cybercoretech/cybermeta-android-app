package dev.cybercore.cybermeta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(s: UiState, vm: AppModel, actions: Actions) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionLabel("Look")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(P.panel)
                    .border(1.dp, P.line, RoundedCornerShape(14.dp))
                    .clickable { vm.go(Screen.Themes) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Palette, null, tint = P.cyan)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("CYBERGRID theme", color = P.fg)
                    Text(s.palette.name, color = P.muted, fontSize = 13.sp)
                }
                Swatch(s.palette)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = P.muted)
            }
        }
        item {
            SectionLabel("Share-safe keeps")
            Panel { KeepToggles(s.keep, vm::setKeep) }
        }
        item {
            SectionLabel("Behaviour")
            Panel {
                ToggleRow(
                    "Quick share",
                    "Photos shared to Cybermeta are cleaned with your current mode (${s.mode.title}) and the share sheet opens straight away.",
                    s.quickShare, vm::setQuickShare,
                )
                ToggleRow(
                    "Random file names",
                    "Camera names like IMG_20261019_143022 give away when a photo was taken. Clean copies get a random name instead.",
                    s.anonymousNames, vm::setAnonymousNames,
                )
                ToggleRow(
                    "Convert HEIC and other formats",
                    "Unsupported formats can't be cleaned losslessly yet. Convert them to a high-quality JPEG when Android can decode them (re-encodes the picture).",
                    s.convertUnsupported, vm::setConvertUnsupported,
                )
            }
        }
        item {
            SectionLabel("Permissions")
            Panel {
                Text(
                    if (s.canSeeLocation) "✔ Cybermeta can see photo locations" else "Android hides photo locations from Cybermeta",
                    color = if (s.canSeeLocation) P.green else P.orange, fontWeight = FontWeight.Bold,
                )
                Text(
                    "Needed only so the report can show you the GPS a photo carries. Cleaned copies never contain it either way.",
                    color = P.muted, fontSize = 13.sp,
                )
                if (!s.canSeeLocation) TextButton(actions::askLocationPermission) { Text("ALLOW", color = P.orange) }
                Text("Internet: none. Cybermeta can't send anything anywhere.", color = P.cyan, fontSize = 13.sp)
            }
        }
        item {
            SectionLabel("About")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScrubMark(48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Cybermeta ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                        Text(Engine.version(), color = P.muted, fontSize = 12.sp, fontFamily = Mono)
                    }
                }
                Text(
                    "Open source (MIT) by Cybercore Tech. The same engine as the cybermeta command-line tool: " +
                        "github.com/cybercoretech/cybermeta-android-app",
                    color = P.muted, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun Swatch(p: Palette, modifier: Modifier = Modifier) {
    Row(modifier.padding(end = 8.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, p.line, RoundedCornerShape(6.dp))) {
        listOf(p.bg, p.cyan, p.pink, p.purple, p.green, p.orange).forEach {
            Box(Modifier.size(width = 10.dp, height = 22.dp).background(it))
        }
    }
}

@Composable
fun ThemesScreen(s: UiState, vm: AppModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PaletteFamilies.forEach { (family, palettes) ->
            item(key = "h-$family") {
                Text(
                    "$family · ${palettes.size}".uppercase(),
                    Modifier.padding(top = 14.dp, bottom = 4.dp),
                    color = P.pink, style = MaterialTheme.typography.labelLarge,
                )
            }
            items(palettes, key = { it.id }) { p ->
                val selected = p.id == s.palette.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.panel)
                        .border(if (selected) 2.dp else 1.dp, if (selected) p.cyan else p.line, RoundedCornerShape(12.dp))
                        .clickable { vm.setPalette(p) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Swatch(p)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = p.fg, fontFamily = Mono, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("kept", color = p.cyan, fontSize = 11.sp, fontFamily = Mono)
                            Text("removed", color = p.pink, fontSize = 11.sp, fontFamily = Mono)
                            Text("risk", color = p.red, fontSize = 11.sp, fontFamily = Mono)
                        }
                    }
                    if (selected) Icon(Icons.Outlined.Check, "Selected", tint = p.cyan)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
