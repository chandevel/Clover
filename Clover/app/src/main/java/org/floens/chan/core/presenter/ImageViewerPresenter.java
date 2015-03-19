package org.floens.chan.core.presenter;

import android.support.v4.view.ViewPager;
import android.util.Log;

import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.view.MultiImageView;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerPresenter implements MultiImageView.Callback, ViewPager.OnPageChangeListener {
    private static final String TAG = "ImageViewerPresenter";

    private final Callback callback;

    private final boolean imageAutoLoad = ChanSettings.imageAutoLoad.get();

    private boolean entering = true;
    private boolean exiting = false;
    private List<PostImage> images;
    private int selectedPosition;

    // Disables swiping until the view pager is visible
    private boolean viewPagerVisible = false;
    private boolean changeViewsOnInTransitionEnd = false;

    public ImageViewerPresenter(Callback callback) {
        this.callback = callback;
    }

    public void showImages(List<PostImage> images, int position) {
        this.images = images;
        selectedPosition = position;

        Logger.test("showImages position " + position);

        // Do this before the view is measured, to avoid it to always loading the first two pages
        callback.setPagerItems(images, selectedPosition);
        callback.setImageMode(images.get(selectedPosition), MultiImageView.Mode.LOWRES);
    }

    public void onViewMeasured() {
        // Pager is measured, but still invisible
        callback.startPreviewInTransition();
    }

    public void onInTransitionEnd() {
        entering = false;
        // Depends on what onModeLoaded did
        if (changeViewsOnInTransitionEnd) {
            callback.setPreviewVisibility(false);
            callback.setPagerVisiblity(true);
        }
    }

    public void onExit() {
        if (entering || exiting) return;
        exiting = true;

        callback.setPagerVisiblity(false);
        callback.setPreviewVisibility(true);
        callback.startPreviewOutTransition();
    }

    @Override
    public void onPageSelected(int position) {
        Logger.test("onPageSelected " + selectedPosition + ", " + position);

        if (!viewPagerVisible) {
            return;
        }

        selectedPosition = position;

        onPageSwipedTo(position);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public void onModeLoaded(MultiImageView multiImageView, MultiImageView.Mode mode) {
        if (mode == MultiImageView.Mode.LOWRES) {
            // lowres is requested at the beginning of the transition,
            // the lowres is loaded before the in transition or after
            if (!viewPagerVisible) {
                viewPagerVisible = true;
                if (!entering) {
                    // Entering transition was already ended, switch now
                    callback.setPreviewVisibility(false);
                    callback.setPagerVisiblity(true);
                } else {
                    // Wait for enter animation to finish before changing views
                    changeViewsOnInTransitionEnd = true;
                }
                // Transition ended or not, request loading the other side views to lowres
                for (PostImage other : getOther(selectedPosition, false)) {
                    callback.setImageMode(other, MultiImageView.Mode.LOWRES);
                }
                onLowResInCenter();
            } else {
                if (multiImageView.getPostImage() == images.get(selectedPosition)) {
                    Log.i(TAG, "Loading high res from a onModeLoaded");
                    onLowResInCenter();
                }
            }
        }
    }

    private void onPageSwipedTo(int position) {
        for (PostImage other : getOther(position, false)) {
            callback.setImageMode(other, MultiImageView.Mode.LOWRES);
        }

        // Already in LOWRES mode
        if (callback.getImageMode(images.get(selectedPosition)) == MultiImageView.Mode.LOWRES) {
            Log.i(TAG, "Loading high res from a swipe");
            onLowResInCenter();
        }
        // Else let onModeChange handle it
    }

    // Called from either a page swipe caused a lowres image to the center or an
    // onModeLoaded when a unloaded image was swiped to the center earlier
    private void onLowResInCenter() {
        PostImage postImage = images.get(selectedPosition);
        if (postImage.type == PostImage.Type.STATIC) {
            callback.setImageMode(postImage, MultiImageView.Mode.BIGIMAGE);
        } else {
            // todo
        }
    }

    @Override
    public void onTap(MultiImageView multiImageView) {
        // Don't mistake a swipe from a user when the pager is disabled as a tap
        if (viewPagerVisible) {
            onExit();
        }
    }

    @Override
    public void setProgress(MultiImageView multiImageView, boolean progress) {

    }

    @Override
    public void setLinearProgress(MultiImageView multiImageView, long current, long total, boolean done) {

    }

    @Override
    public void onVideoLoaded(MultiImageView multiImageView) {

    }

    @Override
    public void onVideoError(MultiImageView multiImageView, File video) {

    }

    private List<PostImage> getOther(int position, boolean all) {
        List<PostImage> other = new ArrayList<>(3);
        if (position - 1 >= 0) {
            other.add(images.get(position - 1));
        }
        if (all) {
            other.add(images.get(position));
        }
        if (position + 1 < images.size()) {
            other.add(images.get(position + 1));
        }
        return other;
    }

    public interface Callback {
        public void startPreviewInTransition();

        public void startPreviewOutTransition();

        public void setPreviewVisibility(boolean visible);

        public void setPagerVisiblity(boolean visible);

        public void setPagerItems(List<PostImage> images, int initialIndex);

        public void setImageMode(PostImage postImage, MultiImageView.Mode mode);

        public MultiImageView.Mode getImageMode(PostImage postImage);
    }
}
