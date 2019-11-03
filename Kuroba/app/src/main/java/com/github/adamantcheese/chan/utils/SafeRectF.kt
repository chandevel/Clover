package com.github.adamantcheese.chan.utils

import android.graphics.PointF
import android.graphics.RectF

/**
 *              SafeRectF               |             RectF                   |      Android Phone Screen
 * -------------------------------------+-------------------------------------+------------------------------------
 *    0; 0      bottom                  |              top       maxX; maxY   |    0; 0     bottom
 *         +--------------+             |        +--------------+             |       +--------------+
 *         |              |             |        |              |             |       |              |
 *         |              |             |        |              |             |       |              |
 *  left   |              |  right      |   left |              | right       |  left |              |  right
 *         |              |             |        |              |             |       |              |
 *         |              |             |        |              |             |       |              |
 *         +--------------+             |        +--------------+             |       +--------------+
 *                top       maxX; maxY  |    0; 0     bottom                  |               top      maxX; maxY
 *                                      |                                     |
 * -------------------------------------+-------------------------------------+------------------------------------
 * */
class SafeRectF {
    private var _x = 0f
    private var _y = 0f
    private var _width = 0f
    private var _height = 0f

    private val _rect = RectF(0f, 0f, 0f, 0f)
    private val _center = PointF(0f, 0f)

    val x: Float
        get() = _x
    val y: Float
        get() = _y
    val width: Float
        get() = _width
    val height: Float
        get() = _height

    constructor(other: SafeRectF) {
        SafeRectF(other.x, other.y, other.width, other.height)
    }

    constructor(rect: RectF) {
        SafeRectF(rect.left, rect.bottom, rect.width(), rect.height())
    }

    constructor(x: Float, y: Float, width: Float, height: Float) {
        check(width >= 0f)
        check(height >= 0f)

        this._x = x
        this._y = y
        this._width = width
        this._height = height
    }

    fun moveTo(point: PointF) {
        moveTo(point.x, point.y)
    }

    fun moveTo(x: Float, y: Float) {
        _x = x
        _y = y
    }

    fun moveBy(dx: Float, dy: Float) {
        _x += dx
        _y += dy
    }

    fun resize(width: Float, height: Float) {
        check(width > 0f) { "width must be greater than 0f!" }
        check(height > 0f) { "height must be greater than 0f!" }

        _width = width
        _height = height
    }

    fun contains(point: PointF): Boolean {
        return contains(point.x, point.y)
    }

    fun contains(x: Float, y: Float): Boolean {
        if (x < _x) {
            return false
        }

        if (y < _y) {
            return false
        }

        if (x > _x + width) {
            return false
        }

        if (y > _y + height) {
            return false
        }

        return true
    }

    fun left(): Float = _x
    fun right(): Float = _x + width
    fun top(): Float = _y
    fun bottom(): Float = _y + height

    // Do not mutate the returned RectF !!!
    fun asRectF(): RectF {
        _rect.set(_x, _y + height, _x + _width, _y)

        check(_rect.right > _rect.left)
        check(_rect.top > _rect.bottom)

        return _rect
    }

    // Do not mutate the returned PointF !!!
    fun center(): PointF {
        _center.set(x + (width / 2f), y + (height / 2f))
        return _center
    }
}