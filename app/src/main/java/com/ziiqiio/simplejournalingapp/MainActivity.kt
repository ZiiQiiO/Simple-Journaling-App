package com.ziiqiio.simplejournalingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziiqiio.simplejournalingapp.data.prefs.ThemeMode
import com.ziiqiio.simplejournalingapp.ui.entry.EntryScreen
import com.ziiqiio.simplejournalingapp.ui.settings.SettingsScreen
import com.ziiqiio.simplejournalingapp.ui.theme.SimpleJournalingAppTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = applicationContext as JournalApp
            val themeMode by app.container.prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SimpleJournalingAppTheme(darkTheme = darkTheme) {
                JournalWithSettingsDrawer()
            }
        }
    }
}

@Composable
private fun JournalWithSettingsDrawer() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val drawerWidth = maxWidth * 0.9f
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        val edgeSwipeWidthPx = with(density) { 48.dp.toPx() }
        val drawerOffset = remember(drawerWidthPx) { Animatable(-drawerWidthPx) }
        var drawerOpen by remember { mutableStateOf(false) }
        var gestureStartedAt by remember { mutableStateOf(Offset.Zero) }

        fun settleDrawer(shouldOpen: Boolean) {
            drawerOpen = shouldOpen
            scope.launch {
                drawerOffset.animateTo(
                    targetValue = if (shouldOpen) 0f else -drawerWidthPx,
                    animationSpec = tween(durationMillis = 240)
                )
            }
        }

        LaunchedEffect(drawerWidthPx) {
            drawerOffset.snapTo(if (drawerOpen) 0f else -drawerWidthPx)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(drawerOpen, drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { gestureStartedAt = it },
                        onHorizontalDrag = { _, dragAmount ->
                            val canDrag = drawerOpen || gestureStartedAt.x <= edgeSwipeWidthPx
                            if (canDrag) {
                                val nextOffset = (drawerOffset.value + dragAmount)
                                    .coerceIn(-drawerWidthPx, 0f)
                                scope.launch { drawerOffset.snapTo(nextOffset) }
                            }
                        },
                        onDragEnd = {
                            val shouldOpen = drawerOffset.value > -drawerWidthPx * 0.55f
                            settleDrawer(shouldOpen)
                        },
                        onDragCancel = { settleDrawer(drawerOpen) }
                    )
                }
        ) {
            EntryScreen(onOpenSettings = { settleDrawer(true) })
        }

        if (drawerOffset.value > -drawerWidthPx) {
            val scrimAlpha = ((drawerOffset.value + drawerWidthPx) / drawerWidthPx) * 0.32f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { settleDrawer(false) })
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { gestureStartedAt = it },
                            onHorizontalDrag = { _, dragAmount ->
                                val nextOffset = (drawerOffset.value + dragAmount)
                                    .coerceIn(-drawerWidthPx, 0f)
                                scope.launch { drawerOffset.snapTo(nextOffset) }
                            },
                            onDragEnd = {
                                val shouldOpen = drawerOffset.value > -drawerWidthPx * 0.55f
                                settleDrawer(shouldOpen)
                            },
                            onDragCancel = { settleDrawer(drawerOpen) }
                        )
                    }
            )
        }

        Box(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .offset {
                    IntOffset(drawerOffset.value.roundToInt(), 0)
                }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(drawerOpen, drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            val nextOffset = (drawerOffset.value + dragAmount)
                                .coerceIn(-drawerWidthPx, 0f)
                            scope.launch { drawerOffset.snapTo(nextOffset) }
                        },
                        onDragEnd = {
                            val shouldOpen = drawerOffset.value > -drawerWidthPx * 0.55f
                            settleDrawer(shouldOpen)
                        },
                        onDragCancel = { settleDrawer(drawerOpen) }
                    )
                }
        ) {
            SettingsScreen(onBack = { settleDrawer(false) })
        }
    }
}
