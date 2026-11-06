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

/**
 * The timeline of frame thumbnails: dragged, it slides through the frames; the middle one stays
 * current, so letting go stops on it.
 */
@Composable
private fun Timeline(vm: CanvasViewModel, width: Dp, modifier: Modifier = Modifier) {
    val a = vm.animation ?: return
    val total = a.count
    if (total == 0) return
    val current = a.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val frameWidth = FrameHeight
    val step = with(density) { (frameWidth + 8.dp).toPx() }
    val middleX = with(density) { (width - frameWidth) / 2f }.toPx()
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    var held by remember { mutableStateOf(false) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var dragOriginOffset by remember { mutableFloatStateOf(0f) }
    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(current) {
        if (!held) {
            val target = -current * step
            if (offset.value != target) offset.animateTo(target, Motion.standard())
        }
    }

    val drag = rememberDraggableState { delta ->
        dragAccumulated += delta
        scope.launch { offset.snapTo(dragOriginOffset + dragAccumulated) }
        val nearest = (-(dragOriginOffset + dragAccumulated) / step).roundToInt().coerceIn(0, total - 1)
        if (nearest != a.current) vm.chooseFrame(nearest)
    }

    Box(
        modifier.size(width, 44.dp).clip(RoundedCornerShape(18.dp)).background(TrackFill).border(1.dp, TrackEdge, RoundedCornerShape(18.dp))
            .draggable(
                drag,
                Orientation.Horizontal,
                onDragStarted = {
                    held = true
                    flingJob?.cancel()
                    dragOriginOffset = offset.value
                    dragAccumulated = 0f
                },
                onDragStopped = { velocity ->
                    held = false
                    flingJob = scope.launch {
                        val decay = exponentialDecay<Float>(frictionMultiplier = 1.2f)
                        val target = decay.calculateTargetValue(offset.value, velocity)
                        val stop = (-(target) / step).roundToInt().coerceIn(0, total - 1)
                        offset.animateTo(-stop * step, Motion.standard())
                        vm.chooseFrame(stop)
                    }
                },
            ),
    ) {
        val o = offset.value
        for (i in 0 until total) {
            val x = with(density) { (middleX + i * step + o).toDp() }
            if (x >= -frameWidth && x <= width) {
                Box(
                    Modifier.offset(x = x, y = 2.dp).size(frameWidth, FrameHeight).clip(RoundedCornerShape(6.dp)).background(if (held) HeldFrame else Color.White)
                        .border(1.dp, Color(0xFF9A9A9A), RoundedCornerShape(6.dp)).clickable { vm.tapFrame(i) },
                ) {
                    val thumb = vm.frameThumbnail(i)
                    if (thumb != null) {
                        Image(thumb, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
    Box(Modifier.offset(x = 12.dp + width / 2 - 12.dp, y = 42.dp + 44.dp + 3.dp).size(24.dp, 3.dp).clip(RoundedCornerShape(1.5.dp)).background(Pc.Accent))
}

/**
 * A popover hanging above the animation bar, with an arrow pointing down at its place on the bar.
 */
@Composable
private fun DownPopover(width: Dp, arrowX: Dp, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val fill = Pc.PopoverFrost
    Box(
        modifier.width(width).then(rememberFrost(24.dp)).padding(bottom = 2.dp).drawBehind {
            val arrowH = Pc.ArrowHeight.toPx()
            val tipX = arrowX.toPx()
            val outline = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height - arrowH, CornerRadius(24.dp.toPx())))
                moveTo(tipX - arrowH, size.height - arrowH - 0.5f)
                lineTo(tipX, size.height)
                lineTo(tipX + arrowH, size.height - arrowH - 0.5f)
                close()
            }
            drawPath(outline, fill)
        }.pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = Pc.ArrowHeight + 8.dp), content = content)
    }
}

/** Animation Assist's settings: playback, frames a second, onion skins, the primary frame. */
@Composable
private fun ColumnScope.AnimationSettingsPage(vm: CanvasViewModel, onColors: () -> Unit) {
    val a = vm.animation ?: return
    Text("Settings", color = Pc.PanelTitle, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 11.dp, bottom = 21.dp))
    Group {
        val modes = listOf(AnimationPlayback.LOOP to "Loop", AnimationPlayback.PING_PONG to "Ping-Pong", AnimationPlayback.ONE_SHOT to "One-Shot")
        for ((i, pair) in modes.withIndex()) {
            val (mode, label) = pair
            val chosen = a.playback == mode
            Row(
                Modifier.fillMaxWidth().height(40.dp).background(if (chosen) Pc.Accent else Color.Transparent).clickable { vm.changeAnimation { it.copy(playback = mode) } }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = if (chosen) Color.White else Pc.Text, fontSize = 17.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
            }
            if (i < modes.size - 1) RowLine()
        }
    }
    Spacer(Modifier.height(14.dp))
    Group {
        SettingSlider("Frames per second", a.fps, 1..60, format = { "$it" }) { vm.changeAnimation { it.copy(fps = it.fps) } }
        RowLine()
        SettingSlider("Onion skin frames", a.onionSkins, 0..MAX_ONION, format = { if (it == 0) "None" else "$it" }) { vm.changeAnimation { it.copy(onionSkins = it.onionSkins) } }
        RowLine()
        SettingSlider("Onion skin opacity", (a.onionOpacity * 100).roundToInt(), 0..100, format = { "$it%" }) { vm.changeAnimation { it.copy(onionOpacity = it / 100f) } }
        RowLine()
        Row(
            Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onColors).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Onion skin colors", color = Pc.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Icon(PcIcons.ChevronRight, contentDescription = null, tint = Pc.TextDim, modifier = Modifier.size(14.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    Group {
        ToggleRow("Blend primary frame", a.blendPrimary) { vm.changeAnimation { it.copy(blendPrimary = !it.blendPrimary) } }
    }
}

/** Frame options: hold duration, duplicate, delete, foreground, background. */
@Composable
private fun ColumnScope.FrameOptionsPage(vm: CanvasViewModel) {
    val a = vm.animation ?: return
    val cur = a.current
    Text("Frame Options", color = Pc.PanelTitle, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 11.dp, bottom = 21.dp))
    Group {
        val hold = a.holdFrames[cur] ?: 0
        SettingSlider("Hold duration", hold, 0..12, format = { if (it == 0) "None" else "$it ${if (it == 1) "frame" else "frames"}" }) {
            vm.setFrameHold(cur, it)
        }
    }
    Spacer(Modifier.height(14.dp))
    Group {
        val isFg = a.foreground == cur
        val isBg = a.background == cur
        ToggleRow("Foreground", isFg) { vm.toggleForeground(cur) }
        RowLine()
        ToggleRow("Background", isBg) { vm.toggleBackground(cur) }
    }
    Spacer(Modifier.height(14.dp))
    Group {
        ActionRow("Duplicate", PcIcons.Duplicate) { vm.duplicateFrame(cur) }
        RowLine()
        ActionRow("Delete", PcIcons.Trash, destructive = true) { vm.deleteFrame(cur) }
    }
}

/** Onion skin colors: backward and forward skin tints. */
@Composable
private fun ColumnScope.OnionColorsPage(vm: CanvasViewModel, onBack: () -> Unit) {
    val a = vm.animation ?: return
    var after by remember { mutableStateOf(false) }
    val (backRgb, forwardRgb) = a.onionBack to a.onionForward
    Row(
        Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onBack).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PcIcons.ChevronLeft, contentDescription = "Back", tint = Pc.TextDim, modifier = Modifier.size(16.dp))
        Text("Settings", color = Pc.Text, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        Text("Colors", color = Pc.PanelTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(44.dp))
    }
    Spacer(Modifier.height(8.dp))
    Group {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (after) "Next frames" else "Previous frames", color = Pc.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, v) in listOf(backRgb, forwardRgb).withIndex()) {
                    Box(
                        Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(7.dp)).background(SwatchRim).padding(1.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Color(0xFF000000.toInt() or rgbOf(v))).clickable { after = i == 1 },
                    )
                }
            }
        }
    }
}

private fun rgbOf(c: Int): Int = c and 0xFFFFFF

private val SwatchRim = Color(0xFF6B6B6B)
private val RowLineFill = Color(0x1FFFFFFF)

private fun RowLine() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(RowLineFill))
}

private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pc.ActionsGroup), content = content)
}

@Composable
private fun SettingSlider(label: String, value: Int, range: IntRange, format: (Int) -> String, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableIntStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Pc.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(format(v), color = Pc.TextDim, fontSize = 17.sp)
        }
        androidx.compose.material3.Slider(
            value = v.toFloat(),
            onValueChange = { v = it.roundToInt(); onChange(v) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Pc.Accent,
                inactiveTrackColor = Color(0xFF4C4C4C),
            ),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onToggle).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Pc.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Pc.Accent,
            ),
        )
    }
}

@Composable
private fun ActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (destructive) Color(0xFFFF453A) else Pc.Text
        Text(label, color = color, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}
