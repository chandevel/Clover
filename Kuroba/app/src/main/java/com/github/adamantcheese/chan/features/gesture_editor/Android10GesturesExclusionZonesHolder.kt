package com.github.adamantcheese.chan.features.gesture_editor

import android.graphics.Rect
import com.github.adamantcheese.chan.core.di.AppModule
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.EMPTY_JSON
import com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10
import com.github.adamantcheese.chan.utils.Logger

class Android10GesturesExclusionZonesHolder(
        private val minScreenSize: Int,
        private val maxScreenSize: Int
) {
    private val exclusionZones: MutableMap<Int, MutableSet<ExclusionZone>> = loadZones()

    private fun loadZones(): MutableMap<Int, MutableSet<ExclusionZone>> {
        val zones = mutableMapOf<Int, MutableSet<ExclusionZone>>()

        val json = ChanSettings.androidTenGestureZones.get()
        if (json.isEmpty() || json == EMPTY_JSON) {
            Logger.d(TAG, "Json setting string is empty, reset.")
            ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
            return zones
        }

        if (!isAndroid10()) {
            Logger.d(TAG, "Not android 10, reset.")
            ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
            return zones
        }

        val exclusionZones = try {
            AppModule.gson.fromJson(
                    json,
                    ExclusionZonesJson::class.java
            )
        } catch (error: Throwable) {
            Logger.e(TAG, "Error while trying to parse zones json, reset.", error)
            ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
            return zones
        }

        // The old settings must belong to a phone with the same screen size as the current one,
        // otherwise something may break
        if (
                exclusionZones.minScreenSize != minScreenSize
                || exclusionZones.maxScreenSize != maxScreenSize
        ) {
            val sizesString = "oldMinScreenSize = ${exclusionZones.minScreenSize}, " +
                    "currentMinScreenSize = ${minScreenSize}, " +
                    "oldMaxScreenSize = ${exclusionZones.maxScreenSize}, " +
                    "currentMaxScreenSize = $maxScreenSize"

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
                    left = zoneJson.left,
                    right = zoneJson.right,
                    top = zoneJson.top,
                    bottom = zoneJson.bottom
            )

            zoneRect.checkValid()
            zones[zoneJson.screenOrientation]!!.add(zoneRect)
            Logger.d(TAG, "Loaded zone $zoneJson")
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
                    "as the new one, prevZone = $prevZone")
            removeZone(prevZone.screenOrientation, prevZone.attachSide)
        }

        if (exclusionZones[orientation]!!.add(exclusionZone)) {
            val newExclusionZones = mutableListOf<ExclusionZoneJson>()

            exclusionZones.forEach { (orientation, zones) ->
                zones.forEach { (_, attachSide1, left, right, top, bottom) ->
                    newExclusionZones += ExclusionZoneJson(
                            screenOrientation = orientation,
                            attachSide = attachSide1.id,
                            left = left,
                            right = right,
                            top = top,
                            bottom = bottom
                    )
                }
            }

            val json = AppModule.gson.toJson(
                    ExclusionZonesJson(minScreenSize, maxScreenSize, newExclusionZones)
            )
            ChanSettings.androidTenGestureZones.set(json)

            Logger.d(TAG, "Added zone $zoneRect for orientation $orientation")
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
                            left = zone.left,
                            right = zone.right,
                            top = zone.top,
                            bottom = zone.bottom
                    )
                }
            }

            val json = AppModule.gson.toJson(
                    ExclusionZonesJson(minScreenSize, maxScreenSize, newExclusionZones)
            )
            ChanSettings.androidTenGestureZones.set(json)

            Logger.d(TAG, "Removed zone $exclusionZone for orientation $orientation")
        }
    }

    fun fillZones(zones: MutableMap<Int, MutableSet<ExclusionZone>>, skipZone: ExclusionZone?) {
        zones.clear()
        deepCopyZones(zones)

        if (skipZone != null) {
            skipZone.checkValid()
            zones[skipZone.screenOrientation]?.remove(skipZone)
        }
    }

    private fun deepCopyZones(zones: MutableMap<Int, MutableSet<ExclusionZone>>) {
        exclusionZones.forEach { (orientation, set) ->
            if (!zones.containsKey(orientation)) {
                zones[orientation] = mutableSetOf()
            }

            set.forEach { zone ->
                zones[orientation]!!.add(zone.copy())
            }
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
                .copy()
                .also { zone -> zone.checkValid() }
    }

    fun resetZones() {
        Logger.d(TAG, "All zones reset")

        if (exclusionZones.isNotEmpty()) {
            exclusionZones.clear()
        }

        ChanSettings.androidTenGestureZones.setSync(EMPTY_JSON)
    }

    companion object {
        private const val TAG = "Android10GesturesExclusionZonesHolder"
    }
}