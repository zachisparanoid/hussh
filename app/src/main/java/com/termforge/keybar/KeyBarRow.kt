package com.termforge.keybar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull

private const val LATCH_MS = 350L
private const val INITIAL_REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 50L
private const val MIN_REPEAT_INTERVAL_MS = 18L
private const val ACCEL_STEP_MS = 4L

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
    modifier: Modifier = Modifier,
) {
    // Self-sizes to one row (button height + content padding). The caller supplies background and
    // insets, so the colored bar is exactly one row tall.
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(layout.buttons, key = { it.id }) { button ->
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
                                val releasedEarly = withTimeoutOrNull(LATCH_MS) { waitForUpOrCancellation() }
                                if (releasedEarly == null) {
                                    state.longPress(mod)            // held → toggle latch
                                    waitForUpOrCancellation()       // consume the release
                                } else {
                                    state.tap(mod)                  // quick tap → one-shot toggle
                                }
                            } else {
                                waitForUpOrCancellation()
                            }
                        }
                        KeyBarButtonType.REPEATABLE -> {
                            sendButton()
                            val released = withTimeoutOrNull(INITIAL_REPEAT_DELAY_MS) { waitForUpOrCancellation() }
                            if (released == null) {
                                var interval = REPEAT_INTERVAL_MS
                                while (true) {
                                    val up = withTimeoutOrNull(interval) { waitForUpOrCancellation() }
                                    if (up != null) break
                                    sendButton()
                                    interval = (interval - ACCEL_STEP_MS).coerceAtLeast(MIN_REPEAT_INTERVAL_MS)
                                }
                            }
                        }
                        else -> {                                    // LITERAL / MOMENTARY / MACRO
                            sendButton()
                            waitForUpOrCancellation()
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
