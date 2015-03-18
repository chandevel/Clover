package org.floens.chan.core.presenter;

import android.support.v4.view.ViewPager;

import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.view.MultiImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerPresenter implements MultiImageView.Callback, ViewPager.OnPageChangeListener {
    private final Callback callback;

    private final boolean imageAutoLoad = ChanSettings.imageAutoLoad.get();

    private boolean entering = true;
    private boolean exiting = false;
    private List<PostImage> images;
    private int selectedIndex;
    private boolean initalLowResLoaded = false;
    private boolean changeViewsOnInTransitionEnd = false;

    public ImageViewerPresenter(Callback callback) {
        this.callback = callback;
    }

    public void showImages(List<PostImage> images, int index) {
        this.images = images;
        selectedIndex = index;

        // Do this before the view is measured, to avoid it to always loading the first two pages
        callback.setPagerItems(images, selectedIndex);
        callback.setImageMode(images.get(selectedIndex), MultiImageView.Mode.LOWRES);
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
        selectedIndex = position;
        if (initalLowResLoaded) {
            for (PostImage other : getOther(selectedIndex)) {
                callback.setImageMode(other, MultiImageView.Mode.LOWRES);
            }
            callback.setImageMode(images.get(selectedIndex), MultiImageView.Mode.LOWRES);
        }
        // onModeLoaded will handle the else case
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
            if (!initalLowResLoaded) {
                initalLowResLoaded = true;
                if (!entering) {
                    // Entering transition was already ended, switch now
                    callback.setPreviewVisibility(false);
                    callback.setPagerVisiblity(true);
                } else {
                    // Wait for enter animation to finish before changing views
                    changeViewsOnInTransitionEnd = true;
                }
                // Transition ended or not, request loading the other side views to lowres
                for (PostImage other : getOther(selectedIndex)) {
                    callback.setImageMode(other, MultiImageView.Mode.LOWRES);
                }
                // selectedIndex can be different than the initial one because of page changes before onModeLoaded was called,
                // request a load of the current selectedIndex one here
                callback.setImageMode(images.get(selectedIndex), MultiImageView.Mode.LOWRES);
            }

            // Initial load or not, transitioning or not, load the high res when the user setting says so after the low res
            if (imageAutoLoad) {
                multiImageView.setMode(MultiImageView.Mode.BIGIMAGE);
            }
        }
    }

    @Override
    public void onTap(MultiImageView multiImageView) {
        onExit();
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

    private List<PostImage> getOther(int position) {
        List<PostImage> other = new ArrayList<>(2);
        if (position - 1 >= 0) {
            other.add(images.get(position - 1));
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
