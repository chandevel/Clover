package org.floens.chan.core.presenter;

import android.support.v4.view.ViewPager;

import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.view.MultiImageView;

import java.util.ArrayList;
import java.util.List;

public class ImageViewerPresenter implements MultiImageView.Callback, ViewPager.OnPageChangeListener {
    private static final String TAG = "ImageViewerPresenter";

    private final Callback callback;

    private final boolean imageAutoLoad = ChanSettings.imageAutoLoad.get();
    private final boolean movieAutoLoad = imageAutoLoad && ChanSettings.videoAutoLoad.get();

    private boolean entering = true;
    private boolean exiting = false;
    private List<PostImage> images;
    private List<Float> progress;
    private int selectedPosition;
    private Loadable loadable;

    // Disables swiping until the view pager is visible
    private boolean viewPagerVisible = false;
    private boolean changeViewsOnInTransitionEnd = false;

    public ImageViewerPresenter(Callback callback) {
        this.callback = callback;
    }

    public void showImages(List<PostImage> images, int position, Loadable loadable) {
        this.images = images;
        selectedPosition = position;
        this.loadable = loadable;

        progress = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            progress.add(i, -1f);
        }

        // Do this before the view is measured, to avoid it to always loading the first two pages
        callback.setPagerItems(images, selectedPosition);
        callback.setImageMode(images.get(selectedPosition), MultiImageView.Mode.LOWRES);
    }

    public void onViewMeasured() {
        // Pager is measured, but still invisible
        callback.startPreviewInTransition(images.get(selectedPosition));
        callback.setTitle(images.get(selectedPosition), selectedPosition, images.size());
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
        callback.startPreviewOutTransition(images.get(selectedPosition));
        callback.showProgress(false);
    }

    public List<PostImage> getAllPostImages() {
        return images;
    }

    public PostImage getCurrentPostImage() {
        return images.get(selectedPosition);
    }

    public Loadable getLoadable() {
        return loadable;
    }

    @Override
    public void onPageSelected(int position) {
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
                    onLowResInCenter();
                }
            }
        }
    }

    private void onPageSwipedTo(int position) {
        callback.setTitle(images.get(selectedPosition), position, images.size());
        callback.scrollToImage(images.get(selectedPosition));

        for (PostImage other : getOther(position, false)) {
            callback.setImageMode(other, MultiImageView.Mode.LOWRES);
        }

        // Already in LOWRES mode
        if (callback.getImageMode(images.get(selectedPosition)) == MultiImageView.Mode.LOWRES) {
            onLowResInCenter();
        }
        // Else let onModeChange handle it

        callback.showProgress(progress.get(selectedPosition) >= 0f);
        callback.onLoadProgress(progress.get(selectedPosition));
    }

    // Called from either a page swipe caused a lowres image to the center or an
    // onModeLoaded when a unloaded image was swiped to the center earlier
    private void onLowResInCenter() {
        PostImage postImage = images.get(selectedPosition);

        if (imageAutoLoad && !postImage.spoiler) {
            if (postImage.type == PostImage.Type.STATIC) {
                callback.setImageMode(postImage, MultiImageView.Mode.BIGIMAGE);
            } else if (postImage.type == PostImage.Type.GIF) {
                callback.setImageMode(postImage, MultiImageView.Mode.GIF);
            } else if (postImage.type == PostImage.Type.MOVIE && movieAutoLoad) {
                callback.setImageMode(postImage, MultiImageView.Mode.MOVIE);
            }
        }
    }

    @Override
    public void onTap(MultiImageView multiImageView) {
        // Don't mistake a swipe when the pager is disabled as a tap
        if (viewPagerVisible) {
            PostImage postImage = images.get(selectedPosition);
            if (imageAutoLoad && !postImage.spoiler) {
                if (movieAutoLoad) {
                    onExit();
                } else {
                    if (postImage.type == PostImage.Type.MOVIE) {
                        callback.setImageMode(postImage, MultiImageView.Mode.MOVIE);
                    } else {
                        onExit();
                    }
                }
            } else {
                MultiImageView.Mode currentMode = callback.getImageMode(postImage);
                if (postImage.type == PostImage.Type.STATIC && currentMode != MultiImageView.Mode.BIGIMAGE) {
                    callback.setImageMode(postImage, MultiImageView.Mode.BIGIMAGE);
                } else if (postImage.type == PostImage.Type.GIF && currentMode != MultiImageView.Mode.GIF) {
                    callback.setImageMode(postImage, MultiImageView.Mode.GIF);
                } else if (postImage.type == PostImage.Type.MOVIE && currentMode != MultiImageView.Mode.MOVIE) {
                    callback.setImageMode(postImage, MultiImageView.Mode.MOVIE);
                } else {
                    onExit();
                }
            }
        }
    }

    @Override
    public void showProgress(MultiImageView multiImageView, boolean show) {
        for (int i = 0; i < images.size(); i++) {
            PostImage postImage = images.get(i);
            if (postImage == multiImageView.getPostImage()) {
                progress.set(i, show ? 0f : -1f);
                break;
            }
        }

        if (multiImageView.getPostImage() == images.get(selectedPosition)) {
            callback.showProgress(progress.get(selectedPosition) >= 0f);
            if (show) {
                callback.onLoadProgress(0f);
            }
        }
    }

    @Override
    public void onProgress(MultiImageView multiImageView, long current, long total) {
        for (int i = 0; i < images.size(); i++) {
            PostImage postImage = images.get(i);
            if (postImage == multiImageView.getPostImage()) {
                progress.set(i, current / (float) total);
                break;
            }
        }

        if (multiImageView.getPostImage() == images.get(selectedPosition)) {
            callback.onLoadProgress(progress.get(selectedPosition));
        }
    }

    @Override
    public void onVideoError(MultiImageView multiImageView) {
        callback.onVideoError(multiImageView);
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
        void startPreviewInTransition(PostImage postImage);

        void startPreviewOutTransition(PostImage postImage);

        void setPreviewVisibility(boolean visible);

        void setPagerVisiblity(boolean visible);

        void setPagerItems(List<PostImage> images, int initialIndex);

        void setImageMode(PostImage postImage, MultiImageView.Mode mode);

        void setTitle(PostImage postImage, int index, int count);

        void scrollToImage(PostImage postImage);

        MultiImageView.Mode getImageMode(PostImage postImage);

        void showProgress(boolean show);

        void onLoadProgress(float progress);

        void onVideoError(MultiImageView multiImageView);
    }
}
