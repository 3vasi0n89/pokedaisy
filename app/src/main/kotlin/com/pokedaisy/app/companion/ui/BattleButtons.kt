package com.pokedaisy.app.companion.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.BattleInput
import com.pokedaisy.app.companion.FieldHudLook
import com.pokedaisy.app.companion.data.BATTLE_INPUT_ACTION_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_MOVE_SELECT
import com.pokedaisy.app.companion.data.MonView
import com.pokedaisy.app.companion.data.MoveView
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.theme.AppColors

/*
 * BATTLE BUTTONS (SETTINGS, on by default): with one screen and no companion beside the game, FIGHT, POKéMON and RUN
 * at the top of the room right of the game's picture - EXPAND's rebuilt background beside the battle's middle 3:2,
 * ORIGINAL's black bar - reaching a little into the picture's top-right corner where that room is narrow. They're the
 * companion's battle buttons ([PlatinumButton]), and FIGHT / POKéMON open the companion's own move grid ([MoveGrid])
 * and party picker ([PartyPicker]) in a window top right: a move is FIGHT + that move in one go
 * ([BattleInput.selectMove]), a Pokémon switches in ([BattleInput.switchTo]); RUN is the action menu's (wild battles
 * only).
 *
 * PokeDaisyActivity shows it in single battles of games with touch battle input, on one screen held sideways.
 * BattleButtonsScreenshotTest renders it.
 */

/** What the battle buttons have open over the game. */
enum class BattleButtonsWindow { MOVES, PARTY }

/**
 * The battle buttons in the room right of [core] (px: where the GBA's own picture is in this box; null = its middle
 * at the box's height, EXPAND's battle). [shown] false fades them out. No room for them (STRETCH, a docked side
 * panel, a narrow screen) = nothing. [clearTop] / [clearBottom] (px): the touch pad's rows, kept clear.
 * [initialWindow]: a window already open (screenshot tests can't tap).
 */
@Composable
fun BattleButtons(
    snapshot: SnapshotView,
    shown: Boolean,
    battleInput: BattleInput?,
    showHints: Boolean,
    modifier: Modifier = Modifier,
    core: Rect? = null,
    clearTop: Int = 0,
    clearBottom: Int = 0,
    /** A window opened / closed (one screen: the side panel's edge tab steps aside for it). */
    onWindow: (Boolean) -> Unit = {},
    initialWindow: BattleButtonsWindow? = null,
) {
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 220 else 120), label = "battle-buttons")
    var window by remember { mutableStateOf(initialWindow) }
    val state = snapshot.battleInputState
    // A window closes with the action menu (a pick ran, the turn moved on) and with the buttons.
    LaunchedEffect(shown, state) {
        if (!shown || state != BATTLE_INPUT_ACTION_SELECT && !(window == BattleButtonsWindow.MOVES && state == BATTLE_INPUT_MOVE_SELECT)) window = null
    }
    LaunchedEffect(window) { onWindow(window != null) }
    if (alpha == 0f && !shown) return
    val busy = rememberBattleInputBusy(battleInput)
    val m = rememberGbaTextMetrics(1f)
    val u = m.u
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().alpha(alpha)) {
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val right = core?.right ?: ((boxW + boxH * 1.5f) / 2)
        val room = with(density) { (boxW - right).toDp() }
        if (room < u * MIN_ROOM) return@BoxWithConstraints
        val gap = u * 5
        val top = with(density) { clearTop.toDp() } + u * 3
        val actions = state == BATTLE_INPUT_ACTION_SELECT
        val enabled = battleInput != null && !busy
        val active = snapshot.battlePlayer.getOrNull(0)
        val moves = active?.moves.orEmpty().take(4)
        val wild = snapshot.enemyParty.isEmpty()
        val columnW = maxOf(u * MIN_WIDTH, minOf(room, u * MAX_WIDTH))
        BoxWithConstraints(
            // At least MIN_WIDTH: past a 16:9 screen's narrow margin into the picture's top-right corner, where a
            // battle has only its background (the user's call: larger buttons over smaller room).
            Modifier.align(Alignment.TopEnd).width(columnW).fillMaxHeight()
                .padding(top = top, bottom = with(density) { clearBottom.toDp() } + u * 3)
                .padding(horizontal = u * 2),
        ) {
            val h = minOf(u * MAX_BUTTON, (maxHeight - gap * 2) / 3)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                // FIGHT, with the battling Pokémon's icon (like the companion's): our move grid from the moves already
                // read - none yet (a launch's first moments) = it waits, rather than pressing the game's FIGHT blind.
                ActionButton(
                    tr("FIGHT"), { HudSpecies(active?.iconAsset, u * 24) }, FightRed, FightRedDark, m, h,
                    enabled = enabled && moves.isNotEmpty() && (actions || state == BATTLE_INPUT_MOVE_SELECT), pressesGame = false,
                ) { window = BattleButtonsWindow.MOVES }
                ActionButton(
                    tr("POKéMON"), { PixelArt(PixelIcons.pokeBall, POKE_BALL, Modifier.size(u * 16)) }, MonGreen, MonGreenDark, m, h,
                    enabled = enabled && actions && battleInput?.canSwitch == true && snapshot.party.size > 1,
                    pressesGame = false,
                ) { window = BattleButtonsWindow.PARTY }
                ActionButton(
                    tr("RUN"), { PixelArt(RUN_ICON, RUN_COLORS, Modifier.size(u * 16)) }, RunBlue, RunBlueDark, m, h,
                    enabled = enabled && actions && wild,
                ) { battleInput?.selectAction(3) }
            }
        }
        val close = { window = null }
        when (window) {
            BattleButtonsWindow.MOVES -> BattleWindow(u, top, close, widthShare = 0.62f, height = u * 230) {
                MoveGrid(moves, battleInput, busy, showHints, Modifier.fillMaxSize(), onCancel = close, compact = true)
            }
            BattleButtonsWindow.PARTY -> BattleWindow(u, top, close, widthShare = 0.56f, height = maxHeight - top - u * 6) {
                PartyPicker(
                    snapshot.party, snapshot.battleOpponent.getOrNull(0), snapshot.battlePlayer, battleInput, busy, showHints,
                    onBack = close,
                )
            }
            null -> Unit
        }
    }
}

/** Below this much room (GBA pixels) right of the picture, no buttons. */
private const val MIN_ROOM = 40

/** The narrowest the column gets (GBA pixels), however little room is beside the picture: POKéMON at full width. */
private const val MIN_WIDTH = 64

/** The widest the column gets (GBA pixels), by the screen's edge: a 20:9 phone has twice a 16:9 screen's room. */
private const val MAX_WIDTH = 76

/** The tallest a button gets (GBA pixels). */
private const val MAX_BUTTON = 56

/** FIGHT / POKéMON / RUN: the companion's battle button in [fill] / [frame], an icon over the word. */
@Composable
private fun ActionButton(
    label: String, icon: @Composable () -> Unit, fill: Color, frame: Color, m: GbaTextMetrics, h: Dp,
    enabled: Boolean, pressesGame: Boolean = true, onClick: () -> Unit,
) {
    val u = m.u
    PlatinumButton(
        fill = fill, frame = frame, modifier = Modifier.fillMaxWidth().height(h),
        enabled = enabled, pressesGame = pressesGame, outline = u + 1.dp,
        // Clear of the bevel's side bands.
        contentPadding = PaddingValues(horizontal = u * 3, vertical = u * 2), onClick = onClick,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            FittedGbaText(label, buttonContent, if (buttonContent == Color.White) ButtonShadow else Color.Transparent, m)
        }
    }
}

private val ButtonShadow = Color(0xFF383838)

/**
 * A window over the game, top right below the buttons' top edge: the companion's battle panes inside the game's
 * title window, [widthShare] of the screen wide and [height] tall. A tap outside closes it, as BACK does.
 */
@Composable
private fun BattleWindow(
    u: Dp, top: Dp, onClose: () -> Unit, widthShare: Float, height: Dp, content: @Composable () -> Unit,
) {
    HudPanelScrim(onClose, Alignment.TopEnd) { window ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier.padding(top = top, end = u * 2).align(Alignment.TopEnd)
                    .size(maxWidth * widthShare, height.coerceAtMost(maxHeight - top))
                    .then(window)
                    .drawBehind {
                        val px = u.toPx()
                        drawLayeredBox(OptionColors.titleLayers.inPx(px), OptionColors.titleFill, radius = 4 * px)
                    }
                    .padding(u * 6),
            ) { content() }
        }
    }
}

/**
 * [text] on one line at [m]'s height, its letters narrowed as far as it needs (3 screen pixels a font pixel wide, then
 * 2, 1.5, 1 - the height stays, so every label reads as one size), then the smaller size, then cut. A 16:9 screen's
 * margin is ~22 GBA pixels: the longest move names need the narrowest.
 */
@Composable
private fun FittedGbaText(text: String, color: Color, shadow: Color, m: GbaTextMetrics, modifier: Modifier = Modifier) {
    val small = rememberGbaTextMetrics(0.75f)
    val measurer = rememberTextMeasurer()
    val font = com.pokedaisy.app.companion.ui.theme.pixelFontFamily()
    val game = gameFontScaleX(m)
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        fun widths(mm: GbaTextMetrics) = listOf(mm.fontPixel, mm.fontPixel - 1f, 1.5f, 1f).filter { it in 1f..mm.fontPixel }.distinct().sortedDescending()
        val candidates = listOf(m, small).flatMap { mm -> widths(mm).map { w -> mm to (w / mm.fontPixel) * game } }
        // Both ways: layoutlib's measurer leaves scaleX out of the width, a device's rounds glyphs its own way - and
        // a font pixel spare, or the device's layout cuts it with "...".
        val (metrics, scaleX) = candidates.firstOrNull { (mm, sx) ->
            val plain = measurer.measure(text, gbaTextStyle(font, mm), maxLines = 1, softWrap = false).size.width * sx
            val scaled = measurer.measure(text, gbaTextStyle(font, mm, sx), maxLines = 1, softWrap = false).size.width.toFloat()
            maxOf(plain, scaled) + mm.fontPixel <= width
        } ?: candidates.last()
        GbaText(text, color, shadow, metrics, scaleX = scaleX)
    }
}

// ---- Pixel art ----------------------------------------------------------------------------------

private val POKE_BALL = mapOf('K' to Color(0xFF202020), 'R' to Color(0xFFE83028), 'W' to Color.White, 'H' to Color(0xFFF8A8A0))

/** A running figure: K outline, B its body. */
private val RUN_ICON = listOf(
    "........KKK.....",
    ".......KBBBK....",
    ".......KBBBK....",
    "........KKK.....",
    "......KKBBK.....",
    "....KKBBBBBKK...",
    "...KBK.KBBK.KBK.",
    "...KK..KBBK..KK.",
    ".......KBBBK....",
    "......KBBKBBK...",
    ".....KBBK.KBBK..",
    "....KBBK...KBK..",
    "..KKBBK....KBK..",
    "..KBBK.....KBBK.",
    "...KK.......KK..",
    "................",
)
private val RUN_COLORS = mapOf('K' to Color(0xFF1C2A44), 'B' to Color.White)
