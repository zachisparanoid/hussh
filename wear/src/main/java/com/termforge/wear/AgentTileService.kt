package com.termforge.wear

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * Glanceable Tile: at-a-glance fleet status on the watch face. Shows how many agents need you (or are
 * running) and names the most urgent one; tapping opens the app. Reads the last-known fleet from
 * [FleetStore] synchronously (so it works with the app closed); refreshed by [WearDataListenerService]
 * when the phone pushes new data, plus a periodic freshness interval.
 */
class AgentTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(60_000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout()))
            .build()
        return ResolvableFuture.create<TileBuilders.Tile>().apply { set(tile) }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val res = ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
        return ResolvableFuture.create<ResourceBuilders.Resources>().apply { set(res) }
    }

    private fun layout(): LayoutElementBuilders.LayoutElement {
        val fleet = FleetStore.load(this)
        val need = fleet.filter { it.reason == "perm" || it.reason == "input" || it.reason == "done" }
        val top = fleet.minByOrNull { priority(it.reason) }

        val headline = when {
            fleet.isEmpty() -> "No agents"
            need.isNotEmpty() -> "${need.size} need you"
            else -> "${fleet.size} running"
        }
        val sub = when {
            fleet.isEmpty() -> "Open to connect"
            top != null -> "${top.session} · ${reasonPhrase(top.reason)}"
            else -> "Tap to open"
        }
        val accent = if (need.isNotEmpty()) COLOR_WARN else COLOR_OK

        return LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName("com.termforge.wear.WearMainActivity")
                                            .build(),
                                    ).build(),
                            ).build(),
                    ).build(),
            )
            .addContent(text("TermForge", 13f, COLOR_DIM, bold = false))
            .addContent(spacer(6))
            .addContent(text(headline, 26f, accent, bold = true))
            .addContent(spacer(4))
            .addContent(text(sub, 14f, COLOR_FG, bold = false))
            .build()
    }

    private fun text(s: String, sizeSp: Float, color: Int, bold: Boolean): LayoutElementBuilders.Text {
        val fontStyle = LayoutElementBuilders.FontStyle.Builder()
            .setSize(DimensionBuilders.sp(sizeSp))
            .setColor(ColorBuilders.argb(color))
        if (bold) fontStyle.setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
        return LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setMaxLines(2)
            .setFontStyle(fontStyle.build())
            .build()
    }

    private fun spacer(dp: Int): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(dp.toFloat())).build()

    private fun priority(reason: String): Int = when (reason) {
        "perm" -> 0
        "input" -> 1
        "done" -> 2
        else -> 3
    }

    private fun reasonPhrase(reason: String): String = when (reason) {
        "perm" -> "needs permission"
        "input" -> "waiting"
        "done" -> "finished"
        else -> "running"
    }

    private companion object {
        const val RES_VERSION = "1"
        const val COLOR_FG = 0xFFFFFFFF.toInt()
        const val COLOR_DIM = 0xFFB0B0B0.toInt()
        const val COLOR_WARN = 0xFFFFB4A9.toInt() // needs-you (warm)
        const val COLOR_OK = 0xFF9CCC9C.toInt()   // all idle/running (calm green)
    }
}
