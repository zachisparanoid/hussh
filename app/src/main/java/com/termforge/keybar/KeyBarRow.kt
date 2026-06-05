package com.termforge.keybar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil

private const val LATCH_MS = 350L
private const val INITIAL_REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 50L
private const val MIN_REPEAT_INTERVAL_MS = 18L
private const val ACCEL_STEP_MS = 4L
private const val FIRST_CHAR_GUARD_MS = 60L

/** Outcome of waiting on a held button: released, cancelled by a scroll, or still held at timeout. */
private enum class PressOutcome { UP, CANCELLED, TIMEOUT }

/**
 * Waits up to [timeoutMillis] for the pointer to lift or the gesture to be cancelled. A parent scroll
 * (the LazyRow) consuming the drag surfaces as cancellation — distinguishing a real hold (TIMEOUT)
 * from the user sliding the bar (CANCELLED), which the old code conflated.
 */
private suspend fun AwaitPointerEventScope.awaitPressOutcome(timeoutMillis: Long): PressOutcome =
    when (withTimeoutOrNull(timeoutMillis) { waitForUpOrCancellation() != null }) {
        true -> PressOutcome.UP
        false -> PressOutcome.CANCELLED
        null -> PressOutcome.TIMEOUT
    }

/**
 * The scrollable keyboard helper bar. A single horizontal row that never wraps — overflow scrolls.
 * Modifiers latch on a ≥350 ms hold and arm one-shot on a tap; arrows/repeatables auto-repeat on hold
 * with acceleration; literals/macros send once, composed with the active modifiers via [KeyBarOutput].
 */
@Composable
fun KeyBarRow(
    layout: KeyBarLayout,
    state: KeyBarState,
    appCursor: () -> Boolean,
    backspaceByte: () -> Byte,
    onSend: (ByteArray) -> Unit,
    rows: Int = 1,
    modifier: Modifier = Modifier,
) {
    val n = rows.coerceIn(1, 3)
    if (n <= 1) {
        SingleKeyRow(layout.buttons, state, appCursor, backspaceByte, onSend, modifier)
    } else {
        // Split the layout's keys across n stacked rows so more are visible without scrolling.
        val perRow = ceil(layout.buttons.size / n.toFloat()).toInt().coerceAtLeast(1)
        Column(modifier) {
            layout.buttons.chunked(perRow).forEach { rowButtons ->
                SingleKeyRow(rowButtons, state, appCursor, backspaceByte, onSend, Modifier.fillMaxWidth())
            }
        }
    }
}

/** One horizontally-scrollable row of keys. Self-sizes to one button-height; the caller owns the
 *  background and insets. */
@Composable
private fun SingleKeyRow(
    buttons: List<KeyBarButtonDef>,
    state: KeyBarState,
    appCursor: () -> Boolean,
    backspaceByte: () -> Byte,
    onSend: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(buttons, key = { it.id }) { button ->
            KeyBarButton(button, state, appCursor, backspaceByte, onSend)
        }
    }
}

@Composable
private fun KeyBarButton(
    button: KeyBarButtonDef,
    state: KeyBarState,
    appCursor: () -> Boolean,
    backspaceByte: () -> Byte,
    onSend: (ByteArray) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val modState = button.modifier?.let { state.stateOf(it) } ?: ModState.OFF

    val fill = when (modState) {
        ModState.LATCHED -> colors.primary
        ModState.ONE_SHOT -> colors.primary.copy(alpha = 0.18f)
        ModState.OFF -> colors.surfaceVariant
    }
    val bg by animateColorAsState(fill, label = "keybarFill")
    val content = when (modState) {
        ModState.LATCHED -> colors.onPrimary
        else -> colors.onSurface
    }
    val scale by animateFloatAsState(if (modState == ModState.LATCHED) 1.06f else 1f, label = "keybarScale")
    val ring: Color = if (modState == ModState.ONE_SHOT) colors.primary else Color.Transparent

    fun sendButton() {
        KeyBarOutput.bytesFor(button, state, appCursor(), backspaceByte())?.let { if (it.isNotEmpty()) onSend(it) }
        state.consumeAfterKey()
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .height(36.dp)
            .scale(scale)
            .defaultMinSize(minWidth = 40.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.5.dp, ring, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp)
            .pointerInput(button.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    when (button.type) {
                        KeyBarButtonType.STICKY -> {
                            val mod = button.modifier
                            if (mod != null) {
                                when (awaitPressOutcome(LATCH_MS)) {
                                    PressOutcome.UP -> state.tap(mod)        // quick tap → one-shot
                                    PressOutcome.CANCELLED -> Unit           // scrolled past → ignore
                                    PressOutcome.TIMEOUT -> {                // held → latch
                                        state.longPress(mod)
                                        waitForUpOrCancellation()
                                    }
                                }
                            } else {
                                waitForUpOrCancellation()
                            }
                        }
                        KeyBarButtonType.REPEATABLE -> {
                            // Brief guard rules out a scroll before the first send, so dragging the
                            // bar by grabbing an arrow never emits a keystroke.
                            when (awaitPressOutcome(FIRST_CHAR_GUARD_MS)) {
                                PressOutcome.CANCELLED -> Unit               // scrolled past → nothing
                                PressOutcome.UP -> sendButton()              // very quick tap → one send
                                PressOutcome.TIMEOUT -> {                    // pressed in place → send + maybe repeat
                                    sendButton()
                                    if (awaitPressOutcome(INITIAL_REPEAT_DELAY_MS - FIRST_CHAR_GUARD_MS)
                                        == PressOutcome.TIMEOUT
                                    ) {
                                        var interval = REPEAT_INTERVAL_MS
                                        while (awaitPressOutcome(interval) == PressOutcome.TIMEOUT) {
                                            sendButton()
                                            interval = (interval - ACCEL_STEP_MS).coerceAtLeast(MIN_REPEAT_INTERVAL_MS)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {                                    // LITERAL / MOMENTARY / MACRO
                            if (waitForUpOrCancellation() != null) sendButton() // send on release, not on a scroll
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = button.label,
            color = content,
            fontSize = 15.sp,
            fontWeight = if (modState == ModState.LATCHED) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
