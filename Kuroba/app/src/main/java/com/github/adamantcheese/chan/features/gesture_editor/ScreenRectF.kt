@file:Suppress("unused", "unused", "unused")

package com.github.adamantcheese.chan.features.gesture_editor

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF

/**
 * A non-retarded version of a RectF with additional safety checks.
 * "It's so safe, it's like it was written in Rust" - (c) Anonymous.
 *
 *
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
class ScreenRectF {
    private var _x = 0f
    private var _y = 0f
    private var _width = 0f
    private var _height = 0f

    private val _rectF = RectF(0f, 0f, 0f, 0f)
    private val _rect = Rect(0, 0, 0, 0)
    private val _center = PointF(0f, 0f)

    private val minSize: Float
    private val maxSize: Float

    val x: Float
        get() = _x
    val y: Float
        get() = _y
    val width: Float
        get() = _width
    val height: Float
        get() = _height

    constructor(other: ScreenRectF) {
        this.minSize = other.minSize
        this.maxSize = other.maxSize

        ScreenRectF(other.x, other.y, other.width, other.height, other.minSize, other.maxSize)
    }

    constructor(rect: RectF, minSize: Float, maxSize: Float) {
        this.minSize = minSize
        this.maxSize = maxSize

        ScreenRectF(rect.left, rect.bottom, rect.width(), rect.height(), minSize, maxSize)
    }

    constructor(x: Float, y: Float, width: Float, height: Float, minSize: Float, maxSize: Float) {
        this.minSize = minSize
        this.maxSize = maxSize

        require(width >= minSize) { "width must be >= minSize!" }
        require(height >= minSize) { "height must be >= minSize!" }

        require(width <= maxSize) { "width must be <= MAXIMUM_SIZE!" }
        require(height <= maxSize) { "height must be <= MAXIMUM_SIZE!" }

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

    fun set(x: Float, y: Float, width: Float, height: Float) {
        require(width >= minSize) { "width (${width}) must be >= minSize!" }
        require(height >= minSize) { "height (${height}) must be >= minSize!" }

        require(width <= maxSize) { "width (${width}) must be <= maxSize!" }
        require(height <= maxSize) { "height (${height}) must be <= maxSize!" }

        _x = x
        _y = y

        _width = width
        _height = height
    }

    fun setSize(width: Float, height: Float) {
        require(width >= minSize) { "width (${width}) must be >= minSize!" }
        require(height >= minSize) { "height (${height}) must be >= minSize!" }

        require(width <= maxSize) { "width (${width}) must be <= maxSize!" }
        require(height <= maxSize) { "height (${height}) must be <= maxSize!" }

        _width = width
        _height = height
    }

    fun setWidth(width: Float) {
        require(width >= minSize) { "width (${width}) must be >= minSize!" }
        require(width <= maxSize) { "width (${width}) must be <= maxSize!" }

        _width = width
    }

    fun setHeight(height: Float) {
        require(height >= minSize) { "height (${height}) must be >= minSize!" }
        require(height <= maxSize) { "height (${height}) must be <= maxSize!" }

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

    // Do not mutate the returned RectF since we use only one instance to avoid unnecessary
    // allocations. Ideally, the returned RectF should only be used for drawing.
    fun asRectF(): RectF {
        _rectF.set(_x, _y, _x + _width, _y + height)

        check(_rectF.left <= _rectF.right) { "_rectF.right (${_rectF.right}) > _rectF.left (${_rectF.left})" }
        check(_rectF.top <= _rectF.bottom) { "_rectF.top (${_rectF.top}) > _rectF.bottom (${_rectF.bottom})" }

        return _rectF
    }

    // Do not mutate the returned Rect since we use only one instance to avoid unnecessary
    // allocations. Ideally, the returned Rect should only be used for drawing.
    fun asRect(): Rect {
        _rect.set(_x.toInt(), _y.toInt(), (_x + _width).toInt(), (_y + height).toInt())

        check(_rect.left <= _rect.right) { "_rect.right (${_rect.right}) > _rect.left (${_rect.left})" }
        check(_rect.top <= _rect.bottom) { "_rect.top (${_rect.top}) > _rect.bottom (${_rect.bottom})" }

        return _rect
    }

    // Do not mutate the returned PointF since we use only one instance to avoid unnecessary
    // allocations. Ideally, the returned PointF should only be used for drawing.
    fun center(): PointF {
        _center.set(x + (width / 2f), y + (height / 2f))
        return _center
    }
}