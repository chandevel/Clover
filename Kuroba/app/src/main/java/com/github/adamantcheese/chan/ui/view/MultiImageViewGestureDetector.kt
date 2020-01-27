package com.github.adamantcheese.chan.ui.view

import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import com.github.adamantcheese.chan.utils.AndroidUtils.dp
import com.google.android.exoplayer2.ui.PlayerView
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import kotlin.math.abs

/**
 * A gesture detector that handles swipe-up and swipe-bottom as well as single tap events for
 * ThumbnailView, BigImageView, GifImageView and VideoView (or ExoplayerView).
 * */
class MultiImageViewGestureDetector(
        private val callbacks: MultiImageViewGestureDetectorCallbacks
) : SimpleOnGestureListener() {

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val gifImageView = callbacks.findGifImageView()
        if (gifImageView != null) {
            // If a GifImageView is visible then toggle it's play state
            val drawable = gifImageView.drawable as GifDrawable
            if (drawable.isPlaying) {
                drawable.pause()
            } else {
                drawable.start()
            }

            // Fallthrough
        } else if (callbacks.findVideoPlayerView() != null) {
            callbacks.onPlayerTogglePlayState()
        } else {
            callbacks.onTap()
        }

        return true
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, vx: Float, vy: Float): Boolean {
        val bigImageView = callbacks.findBigImageView()

        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x

        val motionEventIsOk = abs(diffY) > FLING_DIFF_Y_THRESHOLD
                && abs(vy) > FLING_VELOCITY_Y_THRESHOLD
                && abs(diffX) < FLING_DIST_X_THRESHOLD

        if (!motionEventIsOk) {
            return false
        }

        if (diffY <= 0) {
            // Swiped up (swipe-to-close)
            if (onSwipedUp(bigImageView)) {
                return true
            }

            // Fallthrough
        } else {
            // Swiped bottom (swipe-to-save file)
            return onSwipedBottom(bigImageView)
        }

        return false
    }

    private fun onSwipedBottom(bigImageView: CustomScaleImageView?): Boolean {
        if (bigImageView != null) {
            // Current image is big image
            if (
                    bigImageView.isViewportTouchingImageBottom &&
                    bigImageView.isViewportTouchingImageTop
            ) {
                // We are either not zoomed in or just slightly zoomed in (i.e. the view
                // port is touching both the top and bottom of an image). We can use
                // swipe-to-save image gesture.
                swipeToSaveOrClose()

                return true
            } else if (
                    bigImageView.isViewportTouchingImageBottom ||
                    bigImageView.isViewportTouchingImageTop
            ) {
                // We are zoomed in and the viewport is touching either top or bottom of an
                // image. We don't want to use swipe-to-save image gesture, we want to use
                // swipe-to-close gesture instead.
                callbacks.onSwipeToCloseImage()
                return true
            } else {
                // We are zoomed in and the viewport is not touching neither top nor bottom of
                // an image we want to pass this event to other views.
                return false
            }
        } else {
            if (callbacks.findThumbnailImageView() != null) {
                // Current image is thumbnail, we can't use swipe-to-save gesture
                callbacks.onSwipeToCloseImage()
            } else {
                // Current image is either a video or a gif, it's safe to use swipe-to-save gesture
                swipeToSaveOrClose()
            }

            return true
        }
    }

    /**
     * If already saved, then swipe-to-close gesture will be used instead of swipe-to-save
     * */
    private fun swipeToSaveOrClose() {
        if (callbacks.isImageAlreadySaved()) {
            // Image already saved, we can't use swipe-to-save image gesture but we
            // can use swipe-to-close image instead
            callbacks.onSwipeToCloseImage()
        } else {
            callbacks.onSwipeToSaveImage()
            callbacks.setImageAlreadySaved()
        }
    }

    private fun onSwipedUp(bigImageView: CustomScaleImageView?): Boolean {
        // If either any view, other than the big image view, is visible (thumbnail, gif or video) OR
        // big image is visible and the viewport is touching image bottom then use
        // close-to-swipe gesture
        if (bigImageView == null || bigImageView.isViewportTouchingImageBottom) {
            callbacks.onSwipeToCloseImage()
            return true
        }

        return false
    }

    interface MultiImageViewGestureDetectorCallbacks {
        fun findBigImageView(): CustomScaleImageView?
        fun findGifImageView(): GifImageView?
        fun findThumbnailImageView(): ThumbnailImageView?
        fun findVideoPlayerView(): PlayerView?
        fun isImageAlreadySaved(): Boolean
        fun setImageAlreadySaved()
        fun onTap()
        fun onPlayerTogglePlayState()
        fun onSwipeToCloseImage()
        fun onSwipeToSaveImage()
    }

    companion object {
        private val FLING_DIFF_Y_THRESHOLD = dp(100f).toFloat()
        private val FLING_VELOCITY_Y_THRESHOLD = dp(300f).toFloat()
        private val FLING_DIST_X_THRESHOLD = dp(75f).toFloat()
    }
}