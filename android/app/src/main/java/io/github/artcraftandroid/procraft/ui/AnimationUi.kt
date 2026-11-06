package io.github.artcraftandroid.procraft.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.procraft.canvas.CanvasViewModel
import io.github.artcraftandroid.procraft.core.AnimationPlayback
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The bar is 657 wide on a wide screen, 446 upright; the timeline 12 in from each side. */
private val WideBarWidth = 657.dp
private val UprightBarWidth = 446.dp
private val BarHeight = 96.dp
private val HeldFrame = Color(0xFFA3A3A3)
private val BarGap = 25.dp
private val FrameHeight = 40.dp
private val BarFill get() = if (Pc.light) Color(0xDEE4E4E4) else Color(0xDE2A2A2A)
private val TrackFill get() = if (Pc.light) Color(0xFFD0D0D0) else Color(0xFF474747)
private val TrackEdge get() = if (Pc.light) Color(0xFFBDBDBD) else Color(0xFF5C5C5C)
private val BarInk get() = if (Pc.light) Color(0xFF393939) else Color(0xFFC5C5C5)
private const val MAX_ONION = 12

/** Play's and Add Frame's inset from the bar's ends. */
private val BarInset = 26.dp

/**
 * Animation Assist's bar at the bottom: Play, Settings and Add Frame over the timeline of frames,
 * the current one in the middle; with its settings or the current frame's options above it.
 */
@Composable
fun BoxScope.AnimationBar(vm: CanvasViewModel) {
    if (vm.animation == null) return
    val screen = androidx.compose.ui.platform.LocalConfiguration.current
    val barWidth = if (screen.screenWidthDp > screen.screenHeightDp) WideBarWidth else UprightBarWidth
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        // where Settings' middle is along the bar, for the arrow of its popover
        var settingsAt by remember { mutableStateOf(281.5.dp) }
        val density = LocalDensity.current
        Column(Modifier.align(Alignment.BottomCenter)) {
            // Onion skin colors opens a page in the settings' place; closed, they open on settings again
            var colorsPage by remember { mutableStateOf(false) }
            if (!vm.animationSettingsOpen) colorsPage = false
            if (vm.animationSettingsOpen) {
                DownPopover(290.dp, 145.dp, Modifier.offset(x = (settingsAt - 145.dp).coerceIn(0.dp, barWidth - 290.dp))) {
                    if (colorsPage) OnionColorsPage(vm) { colorsPage = false } else AnimationSettingsPage(vm) { colorsPage = true }
                }
            } else if (vm.frameOptionsOpen) {
                DownPopover(260.dp, 130.dp, Modifier.offset(x = barWidth / 2 - 130.dp)) { FrameOptionsPage(vm) }
            }
            // the whole bar takes its touches: none reach the canvas under it (a swipe would paint)
            Box(Modifier.padding(bottom = BarGap).size(barWidth, BarHeight).then(rememberFrost(30.dp)).clip(RoundedCornerShape(30.dp)).background(BarFill).pointerInput(Unit) { detectTapGestures { } }) {
                val pages = vm.animation?.pages == true
                Row(Modifier.fillMaxWidth().padding(start = BarInset, end = BarInset, top = 8.dp).height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (pages) "Page Assist" else if (vm.playingFrame != null) "Pause" else "Play", color = BarInk, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        modifier = if (pages) Modifier else Modifier.clickable { vm.togglePlaying() },
                    )
                    Spacer(Modifier.weight(1f))
                    if (!pages) BarText(
                        "Settings",
                        // measured inside the row's padding
                        Modifier.onGloballyPositioned { c -> settingsAt = BarInset + with(density) { (c.positionInParent().x + c.size.width / 2f).toDp() } },
                    ) {
                        vm.animationSettingsOpen = !vm.animationSettingsOpen
                        vm.frameOptionsOpen = false
                    }
                    Spacer(Modifier.width(23.dp))
                    BarText(if (pages) "New Page" else "Add Frame", Modifier) { vm.addFrame() }
                }
                Timeline(vm, barWidth - 24.dp, Modifier.offset(x = 12.dp, y = 42.dp))
            }
        }
    }
}

@Composable
private fun BarText(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(30.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = BarInk, fontSize = 17.sp, maxLines = 1)
    }
}
