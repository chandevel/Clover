package com.github.adamantcheese.chan.features.gesture_editor

import android.graphics.Rect
import com.google.gson.annotations.SerializedName

data class ExclusionZonesJson(
        @SerializedName("min_screen_size")
        val minScreenSize: Int,
        @SerializedName("max_screen_size")
        val maxScreenSize: Int,
        @SerializedName("exclusion_zone")
        val zones: List<ExclusionZoneJson>
)

data class ExclusionZone(
        val screenOrientation: Int,
        val attachSide: AttachSide,
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int
) {
    private val _zoneRect = Rect(left, top, right, bottom)
    val zoneRect: Rect
        get() = _zoneRect

    fun checkValid() {
        check(left <= right) { "right (${right}) > left (${left})" }
        check(top <= bottom) { "bottom (${bottom}) > top (${top})"}
    }
}

data class ExclusionZoneJson(
        @SerializedName("screen_orientation")
        val screenOrientation: Int,
        @SerializedName("attach_side")
        val attachSide: Int,
        @SerializedName("zone_left")
        val left: Float,
        @SerializedName("zone_right")
        val right: Float,
        @SerializedName("zone_top")
        val top: Float,
        @SerializedName("zone_bottom")
        val bottom: Float
)