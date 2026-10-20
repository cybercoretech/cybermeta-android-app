package dev.cybercore.cybermeta

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun App(s: UiState, vm: AppModel, actions: Actions) {
    BackHandler(enabled = s.canGoBack) { vm.back() }
    Box(Modifier.fillMaxSize()) {
        GridBackdrop()
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(s, vm)
            AnimatedContent(s.screen, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(s, actions)
                    Screen.Review -> ReviewScreen(s, vm)
                    Screen.Detail -> DetailScreen(s, actions)
                    Screen.Done -> DoneScreen(s, vm, actions)
                    Screen.Settings -> SettingsScreen(s, vm, actions)
                    Screen.Themes -> ThemesScreen(s, vm)
                }
            }
        }
        s.message?.let { msg ->
            Snackbar(
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                containerColor = P.raised, contentColor = P.fg,
                action = { TextButton(vm::dismissMessage) { Text("OK", color = P.cyan) } },
            ) { Text(msg) }
        }
    }
}

@Composable
private fun TopBar(s: UiState, vm: AppModel) {
    val title = when (s.screen) {
        Screen.Home -> ""
        Screen.Review -> "Review"
        Screen.Detail -> s.detailItem?.name ?: "Photo"
        Screen.Done -> "Done"
        Screen.Settings -> "Settings"
        Screen.Themes -> "Themes"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.canGoBack) IconButton(vm::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = P.fg) }
        else Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (s.screen != Screen.Settings && s.screen != Screen.Themes) {
            IconButton({ vm.go(Screen.Settings) }) { Icon(Icons.Outlined.Settings, "Settings", tint = P.muted) }
        }
    }
}
