package com.github.adamantcheese.chan.features.gesture_editor

import android.graphics.Rect
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.EMPTY_JSON
import com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10
import com.github.adamantcheese.chan.utils.Logger
import com.google.gson.Gson

class Android10GesturesExclusionZonesHolder(
        private val gson: Gson,
        private val minScreenSize: Int,
        private val maxScreenSize: Int
) {
    private val exclusionZones = loadZones()

    private fun loadZones(): MutableMap<Int, MutableSet<ExclusionZone>> {
        val zones = mutableMapOf<Int, MutableSet<ExclusionZone>>()

        val json = ChanSettings.androidTenGestureZones.get()
        if (json.isEmpty()) {
            ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
            return zones
        }

        if (json == EMPTY_JSON) {
            return zones
        }

        if (!isAndroid10()) {
            ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
            return zones
        }

        val exclusionZones = gson.fromJson<ExclusionZonesJson>(
                json,
                ExclusionZonesJson::class.java
        )

        // The old settings must belong to a phone with the same screen size as the current one,
        // otherwise something may break
        if (
                exclusionZones.minScreenSize != minScreenSize
                || exclusionZones.maxScreenSize != maxScreenSize
        ) {
            val sizesString = "oldMinScreenSize = ${exclusionZones.minScreenSize}, " +
                    "currentMinScreenSize = ${minScreenSize}, " +
                    "oldMaxScreenSize = ${exclusionZones.maxScreenSize}, " +
                    "currentMaxScreenSize = ${maxScreenSize}"

            Logger.d(TAG, "Screen sizes do not match! $sizesString")
            resetZones()
            return zones
        }

        if (exclusionZones.zones.isEmpty()) {
            return zones
        }

        exclusionZones.zones.forEach { zoneJson ->
            if (!zones.containsKey(zoneJson.screenOrientation)) {
                zones[zoneJson.screenOrientation] = mutableSetOf()
            }

            val zoneRect = ExclusionZone(
                    screenOrientation = zoneJson.screenOrientation,
                    attachSide = AttachSide.fromInt(zoneJson.attachSide),
                    left = zoneJson.left.toInt(),
                    right = zoneJson.right.toInt(),
                    top = zoneJson.top.toInt(),
                    bottom = zoneJson.bottom.toInt()
            )

            zoneRect.checkValid()
            zones[zoneJson.screenOrientation]!!.add(zoneRect)
            Logger.d(TAG, "Loaded zone ${zoneJson}")
        }

        return zones
    }

    fun addZone(orientation: Int, attachSide: AttachSide, zoneRect: Rect) {
        if (!exclusionZones.containsKey(orientation)) {
            exclusionZones[orientation] = mutableSetOf()
        }

        val exclusionZone = ExclusionZone(
                screenOrientation = orientation,
                attachSide = attachSide,
                left = zoneRect.left,
                right = zoneRect.right,
                top = zoneRect.top,
                bottom = zoneRect.bottom
        )
        exclusionZone.checkValid()

        val prevZone = getZoneOrNull(orientation, attachSide)
        if (prevZone != null) {
            Logger.d(TAG, "addZone() Removing previous zone with the same params " +
                    "as the new one, prevZone = ${prevZone}")
            removeZone(prevZone.screenOrientation, prevZone.attachSide)
        }

        if (exclusionZones[orientation]!!.add(exclusionZone)) {
            val newExclusionZones = mutableListOf<ExclusionZoneJson>()

            exclusionZones.forEach { (orientation, zones) ->
                zones.forEach { zone ->
                    newExclusionZones += ExclusionZoneJson(
                            screenOrientation = orientation,
                            attachSide = attachSide.id,
                            left = zone.left.toFloat(),
                            right = zone.right.toFloat(),
                            top = zone.top.toFloat(),
                            bottom = zone.bottom.toFloat()
                    )
                }
            }

            val json = gson.toJson(
                    ExclusionZonesJson(minScreenSize, maxScreenSize, newExclusionZones)
            )
            ChanSettings.androidTenGestureZones.set(json)

            Logger.d(TAG, "Added zone ${zoneRect} for orientation ${orientation}")
        }
    }

    fun removeZone(orientation: Int, attachSide: AttachSide) {
        if (exclusionZones.isEmpty()) {
            return
        }

        if (!exclusionZones.containsKey(orientation)) {
            return
        }

        val exclusionZone = getZoneOrNull(orientation, attachSide)
                ?: return

        if (exclusionZones[orientation]!!.remove(exclusionZone)) {
            val newExclusionZones = mutableListOf<ExclusionZoneJson>()

            exclusionZones.forEach { (orientation, zones) ->
                zones.forEach { zone ->
                    zone.checkValid()

                    newExclusionZones += ExclusionZoneJson(
                            screenOrientation = orientation,
                            attachSide = zone.attachSide.id,
                            left = zone.left.toFloat(),
                            right = zone.right.toFloat(),
                            top = zone.top.toFloat(),
                            bottom = zone.bottom.toFloat()
                    )
                }
            }

            val json = gson.toJson(
                    ExclusionZonesJson(minScreenSize, maxScreenSize, newExclusionZones)
            )
            ChanSettings.androidTenGestureZones.set(json)

            Logger.d(TAG, "Removed zone ${exclusionZone} for orientation ${orientation}")
        }
    }

    fun fillZones(zones: MutableMap<Int, MutableSet<ExclusionZone>>, skipZone: ExclusionZone?) {
        zones.clear()
        zones.putAll(exclusionZones)

        if (skipZone != null) {
            skipZone.checkValid()
            zones[skipZone.screenOrientation]?.remove(skipZone)
        }
    }

    fun getZones(): MutableMap<Int, MutableSet<ExclusionZone>> {
        return exclusionZones
    }

    fun getZoneOrNull(orientation: Int, attachSide: AttachSide): ExclusionZone? {
        val zones = exclusionZones[orientation]
                ?.filter { zone -> zone.attachSide == attachSide }
                ?: emptyList()

        if (zones.isEmpty()) {
            return null
        }

        if (zones.size > 1) {
            val zonesString = zones.joinToString(prefix = "[", postfix = "]", separator = ",")

            throw IllegalStateException("More than one zone exists with the same orientation " +
                    "and attach side! This is not supported! (zones = $zonesString)")
        }

        return zones.first()
                .also { zone -> zone.checkValid() }
    }

    fun resetZones() {
        Logger.d(TAG, "All zones reset")

        if (exclusionZones != null && exclusionZones.isNotEmpty()) {
            exclusionZones.clear()
        }

        ChanSettings.androidTenGestureZones.setSync(EMPTY_JSON)
    }

    companion object {
        private const val TAG = "Android10GesturesExclusionZonesHolder"
    }
}