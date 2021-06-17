package com.github.adamantcheese.chan.features.gesture_editor

import android.graphics.Rect
import com.github.adamantcheese.chan.core.di.AppModule
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.EMPTY_JSON
import com.github.adamantcheese.chan.utils.AndroidUtils.*
import com.github.adamantcheese.chan.utils.Logger

object Android10GesturesExclusionZonesHolder {
    private val exclusionZones: MutableMap<Int, MutableSet<ExclusionZone>> = mutableMapOf()
    private var minScreenSize: Int = getMinScreenSize()
    private var maxScreenSize: Int = getMaxScreenSize()

    init {
        loadZones()
    }

    private fun loadZones() {
        val json = ChanSettings.androidTenGestureZones.get()
        if (json.isEmpty() || !isAndroid10()) {
            Logger.d(this, "Gesture zones reset, either empty or not Android 10+.")
            resetZones()
            return
        }

        val existingZones = try {
            AppModule.gson.fromJson(json, ExclusionZonesJson::class.java)
        } catch (error: Throwable) {
            Logger.e(this, "Error while trying to parse zones json, reset.", error)
            resetZones()
            return
        }

        // The old settings must belong to a phone with the same screen size as the current one,
        // otherwise something may break
        if (
                existingZones.minScreenSize != minScreenSize
                || existingZones.maxScreenSize != maxScreenSize
        ) {
            val sizesString = "oldMinScreenSize = ${existingZones.minScreenSize}, " +
                    "currentMinScreenSize = ${minScreenSize}, " +
                    "oldMaxScreenSize = ${existingZones.maxScreenSize}, " +
                    "currentMaxScreenSize = $maxScreenSize"

            Logger.d(this, "Screen sizes do not match! $sizesString")
            resetZones()
            return
        }

        existingZones.zones.forEach { zoneJson ->
            if (!exclusionZones.containsKey(zoneJson.screenOrientation)) {
                exclusionZones[zoneJson.screenOrientation] = mutableSetOf()
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
            exclusionZones[zoneJson.screenOrientation]!!.add(zoneRect)
            Logger.d(this, "Loaded zone $zoneJson")
        }
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

        val prevZone = getZone(orientation, attachSide)
        if (prevZone != null) {
            Logger.d(this, "addZone() Removing previous zone with the same params " +
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
            ChanSettings.androidTenGestureZones.setSync(json)

            Logger.d(this, "Added zone $zoneRect for orientation $orientation")
        }
    }

    @JvmStatic
    fun removeZone(orientation: Int, attachSide: AttachSide) {
        if (exclusionZones.isEmpty()) {
            return
        }

        if (!exclusionZones.containsKey(orientation)) {
            return
        }

        val exclusionZone = getZone(orientation, attachSide) ?: return

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
            ChanSettings.androidTenGestureZones.setSync(json)

            Logger.d(this, "Removed zone $exclusionZone for orientation $orientation")
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

    @JvmStatic
    fun getZones(): MutableMap<Int, MutableSet<ExclusionZone>> {
        return exclusionZones
    }

    @JvmStatic
    fun getZone(orientation: Int, attachSide: AttachSide): ExclusionZone? {
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

    @JvmStatic
    fun resetZones() {
        Logger.d(this, "All zones reset")

        if (exclusionZones.isNotEmpty()) {
            exclusionZones.clear()
        }

        ChanSettings.androidTenGestureZones.setSync(EMPTY_JSON)
    }
}