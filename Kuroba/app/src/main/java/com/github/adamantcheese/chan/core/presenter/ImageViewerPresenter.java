/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.presenter;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.ImageSearch;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.MultiImageView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.model.PostImage.Type.GIF;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.IFRAME;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.MOVIE;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.OTHER;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.STATIC;
import static com.github.adamantcheese.chan.core.net.NetUtilsClasses.EMPTY_CONVERTER;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.MediaAutoLoadMode.shouldLoadForNetworkType;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.BIGIMAGE;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.GIFIMAGE;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.LOWRES;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.VIDEO;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.WEBVIEW;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDefaultMuteState;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;

public class ImageViewerPresenter
        implements MultiImageView.Callback, ViewPager.OnPageChangeListener {
    private final Context context;
    private static final int PRELOAD_IMAGE_INDEX = 1;
    /**
     * We don't want to cancel an image right after we have started preloading it because it
     * sometimes causes weird bugs where you swipe to an already canceled image/webm and nothing
     * happens so you need to swipe back and forth for it to start loading.
     */
    private static final int CANCEL_IMAGE_INDEX = 2;

    private final Callback callback;

    private boolean entering = true;
    private boolean exiting = false;
    private List<PostImage> images;
    private int selectedPosition = 0;
    private SwipeDirection swipeDirection = SwipeDirection.Default;
    private Loadable loadable;
    private final Set<Call> preloadingImages = new CopyOnWriteArraySet<>();
    private final Set<HttpUrl> nonCancelableImages = new CopyOnWriteArraySet<>();

    // Disables swiping until the view pager is visible
    private boolean viewPagerVisible = false;
    private boolean changeViewsOnInTransitionEnd = false;

    private boolean muted = getDefaultMuteState();

    public ImageViewerPresenter(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void showImages(List<PostImage> images, int position, Loadable loadable) {
        this.images = images;
        this.loadable = loadable;
        this.selectedPosition = Math.max(0, Math.min(images.size() - 1, position));

        // Do this before the view is measured, to avoid it to always loading the first two pages
        callback.setPagerItems(images, selectedPosition);
        PostImage initialImage = getCurrentPostImage();
        callback.setImageMode(initialImage, LOWRES, true);
        callback.showDownloadMenuItem(!initialImage.deleted && initialImage.type != IFRAME);
    }

    public boolean isTransitioning() {
        return entering;
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

        PostImage postImage = getCurrentPostImage();
        if (postImage.type == MOVIE || postImage.type == IFRAME) {
            callback.setImageMode(postImage, LOWRES, true);
        }

        callback.showDownloadMenuItem(false);
        callback.setPagerVisiblity(false);
        callback.setPreviewVisibility(true);
        callback.startPreviewOutTransition(postImage);
        callback.showProgress(false);

        for (Call preloadingImage : preloadingImages) {
            preloadingImage.cancel();
        }

        nonCancelableImages.clear();
        preloadingImages.clear();
    }

    public void onVolumeClicked() {
        muted = !muted;
        callback.showVolumeMenuItem(true, muted);
        callback.setVolume(getCurrentPostImage(), muted);
    }

    public List<PostImage> getAllPostImages() {
        return images;
    }

    public PostImage getCurrentPostImage() {
        return images.get(selectedPosition);
    }

    @Override
    public Loadable getLoadable() {
        return loadable;
    }

    @Override
    public void onPageSelected(int position) {
        if (!viewPagerVisible) {
            return;
        }

        if (position == selectedPosition) {
            swipeDirection = SwipeDirection.Default;
        } else if (position > selectedPosition) {
            swipeDirection = SwipeDirection.Forward;
        } else {
            swipeDirection = SwipeDirection.Backward;
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
        if (exiting) return;

        if (mode == LOWRES) {
            // lowres is requested at the beginning of the transition,
            // the lowres is loaded before the in transition or after
            if (!viewPagerVisible) {
                viewPagerVisible = true;
                if (!entering) {
                    // Entering transition was already ended, switch now
                    onInTransitionEnd();
                } else {
                    // Wait for enter animation to finish before changing views
                    changeViewsOnInTransitionEnd = true;
                }
                // Transition ended or not, request loading the other side views to lowres
                for (PostImage other : getOther(selectedPosition)) {
                    callback.setImageMode(other, LOWRES, false);
                }
                onLowResInCenter();
            } else {
                if (multiImageView.getPostImage() == getCurrentPostImage()) {
                    onLowResInCenter();
                }
            }
        } else {
            PostImage currentImage = getCurrentPostImage();
            if (multiImageView.getPostImage() == currentImage) {
                setTitle(currentImage, selectedPosition);
            }
        }
    }

    private void onPageSwipedTo(int position) {
        PostImage postImage = getCurrentPostImage();
        // Reset volume icon.
        // If it has audio, we'll know after it is loaded.
        callback.showVolumeMenuItem(postImage.type == MOVIE, muted);

        //Reset the save icon, don't allow deleted saves
        callback.showDownloadMenuItem(!postImage.deleted && postImage.type != IFRAME);

        setTitle(postImage, position);
        callback.scrollToImage(postImage);
        callback.updatePreviewImage(postImage);

        for (PostImage other : getOther(position)) {
            callback.setImageMode(other, LOWRES, false);
        }

        nonCancelableImages.clear();
        nonCancelableImages.addAll(getNonCancelableImages(position));

        if (swipeDirection == SwipeDirection.Forward) {
            cancelPreviousFromStartImageDownload(position);
        } else if (swipeDirection == SwipeDirection.Backward) {
            cancelPreviousFromEndImageDownload(position);
        }

        // Already in LOWRES mode
        if (callback.getImageMode(postImage) == LOWRES) {
            onLowResInCenter();
        }
    }

    // Called from either a page swipe caused a lowres image to be in the center or an
    // onModeLoaded when a unloaded image was swiped to the center earlier
    private void onLowResInCenter() {
        PostImage postImage = getCurrentPostImage();

        if (imageAutoLoad(postImage) && (!postImage.spoiler() || ChanSettings.revealimageSpoilers.get())) {
            if (postImage.type == STATIC) {
                callback.setImageMode(postImage, BIGIMAGE, true);
            } else if (postImage.type == GIF) {
                callback.setImageMode(postImage, GIFIMAGE, true);
            } else if (postImage.type == MOVIE && videoAutoLoad(postImage)) {
                callback.setImageMode(postImage, VIDEO, true);
            } else if (postImage.type == PostImage.Type.IFRAME) {
                callback.setImageMode(postImage, WEBVIEW, true);
            } else if (postImage.type == PostImage.Type.OTHER) {
                callback.setImageMode(postImage, MultiImageView.Mode.OTHER, true);
            }
        }

        ChanSettings.ImageClickPreloadStrategy strategy = ChanSettings.imageClickPreloadStrategy.get();

        if (swipeDirection == SwipeDirection.Forward) {
            preloadNext();
        } else if (swipeDirection == SwipeDirection.Backward) {
            preloadPrevious();
        } else {
            switch (strategy) {
                case PreloadNext:
                    preloadNext();
                    break;
                case PreloadPrevious:
                    preloadPrevious();
                    break;
                case PreloadBoth:
                    preloadNext();
                    preloadPrevious();
                    break;
                case PreloadNeither:
                    break;
            }
        }
    }

    private void preloadPrevious() {
        int index = selectedPosition - PRELOAD_IMAGE_INDEX;

        if (index >= 0 && index < images.size()) {
            doPreloading(images.get(index));
        }
    }

    // This won't actually change any modes, but it will preload the image so that it's
    // available immediately when the user swipes right.
    private void preloadNext() {
        int index = selectedPosition + PRELOAD_IMAGE_INDEX;

        if (index >= 0 && index < images.size()) {
            doPreloading(images.get(index));
        }
    }

    private List<HttpUrl> getNonCancelableImages(int index) {
        if (images.isEmpty()) {
            return Collections.emptyList();
        }

        List<HttpUrl> nonCancelableImages = new ArrayList<>(3);

        if (index - 1 >= 0) {
            nonCancelableImages.add(images.get(index - 1).imageUrl);
        }

        if (index >= 0 && index < images.size()) {
            nonCancelableImages.add(images.get(index).imageUrl);
        }

        if (index + 1 < images.size()) {
            nonCancelableImages.add(images.get(index + 1).imageUrl);
        }

        return nonCancelableImages;
    }

    private void doPreloading(PostImage postImage) {
        boolean load = false;

        if (postImage.type == STATIC || postImage.type == GIF || postImage.type == OTHER) {
            load = imageAutoLoad(postImage);
        } else if (postImage.type == MOVIE) {
            load = videoAutoLoad(postImage);
        }

        if (load) {
            // If downloading, remove from preloadingImages if it finished.
            // Array to allow access from within the callback
            final Call[] preloadDownload = new Call[1];

            preloadDownload[0] = NetUtils.makeRequest(NetUtils.applicationClient.getHttpRedirectClient(),
                    postImage.imageUrl,
                    EMPTY_CONVERTER,
                    new NetUtilsClasses.ResponseResult<Object>() {
                        @Override
                        public void onFailure(Exception e) {
                            updatePreload();
                        }

                        @Override
                        public void onSuccess(Object result) {
                            updatePreload();
                        }

                        private void updatePreload() {
                            if (preloadDownload[0] != null) {
                                preloadingImages.remove(preloadDownload[0]);
                            }
                        }
                    },
                    null,
                    NetUtilsClasses.ONE_DAY_CACHE
            );

            if (preloadDownload[0] != null) {
                preloadingImages.add(preloadDownload[0]);
            }
        }
    }

    private void cancelPreviousFromEndImageDownload(int position) {
        for (Call downloader : preloadingImages) {
            int index = position + CANCEL_IMAGE_INDEX;
            if (index < images.size()) {
                if (cancelImageDownload(index, downloader)) {
                    return;
                }
            }
        }
    }

    private void cancelPreviousFromStartImageDownload(int position) {
        for (Call downloader : preloadingImages) {
            int index = position - CANCEL_IMAGE_INDEX;
            if (index >= 0) {
                if (cancelImageDownload(index, downloader)) {
                    return;
                }
            }
        }
    }

    private boolean cancelImageDownload(int position, Call downloader) {
        if (nonCancelableImages.contains(downloader.request().url())) {
            Logger.d(this,
                    "Attempt to cancel non cancelable download for image with url: " + downloader.request().url()
            );
            return false;
        }

        PostImage previousImage = images.get(position);
        if (downloader.request().url().equals(previousImage.imageUrl)) {
            downloader.cancel();
            preloadingImages.remove(downloader);
            return true;
        }

        return false;
    }

    @Override
    public void onTap() {
        // Don't mistake a swipe when the pager is disabled as a tap
        if (viewPagerVisible) {
            PostImage postImage = getCurrentPostImage();
            if (imageAutoLoad(postImage) && !postImage.spoiler()) {
                if (postImage.type == MOVIE && callback.getImageMode(postImage) != VIDEO) {
                    callback.setImageMode(postImage, VIDEO, true);
                } else {
                    if (callback.isImmersive()) {
                        callback.showSystemUI(true);
                    } else {
                        onExit();
                    }
                }
            } else {
                MultiImageView.Mode currentMode = callback.getImageMode(postImage);
                if (postImage.type == STATIC && currentMode != BIGIMAGE) {
                    callback.setImageMode(postImage, BIGIMAGE, true);
                } else if (postImage.type == GIF && currentMode != GIFIMAGE) {
                    callback.setImageMode(postImage, GIFIMAGE, true);
                } else if (postImage.type == MOVIE && currentMode != VIDEO) {
                    callback.setImageMode(postImage, VIDEO, true);
                } else if (postImage.type == IFRAME && currentMode != WEBVIEW) {
                    callback.setImageMode(postImage, WEBVIEW, true);
                } else if ((postImage.type == PostImage.Type.OTHER) && currentMode != MultiImageView.Mode.OTHER) {
                    callback.setImageMode(postImage, MultiImageView.Mode.OTHER, true);
                } else {
                    if (callback.isImmersive()) {
                        callback.showSystemUI(true);
                    } else {
                        onExit();
                    }
                }
            }
        }
    }

    @Override
    public void checkImmersive() {
        if (callback.isImmersive()) {
            callback.showSystemUI(true);
        }
    }

    @Override
    public void onSwipeToCloseImage() {
        onExit();
    }

    @Override
    public void onSwipeToSaveImage() {
        callback.saveImage();
    }

    @Override
    public void hideProgress(MultiImageView multiImageView) {
        callback.showProgress(false);
    }

    @Override
    public void onProgress(MultiImageView multiImageView, long current, long total) {
        if (multiImageView.getPostImage() == getCurrentPostImage()) {
            callback.showProgress(true);
            callback.onLoadProgress(current / (float) total);
        }
    }

    @Override
    public void onAudioLoaded(MultiImageView multiImageView) {
        PostImage currentPostImage = getCurrentPostImage();
        if (multiImageView.getPostImage() == currentPostImage) {
            muted = muted || !BackgroundUtils.isInForeground();
            callback.showVolumeMenuItem(true, muted);
            callback.setVolume(currentPostImage, muted);
        }
    }

    @Override
    public void onOpacityChanged(MultiImageView multiImageView, boolean hasOpacity, boolean opaque) {
        PostImage currentPostImage = getCurrentPostImage();
        if (multiImageView.getPostImage() == currentPostImage) {
            callback.showOpacityMenuItem(hasOpacity, opaque);
        }
    }

    private boolean imageAutoLoad(PostImage postImage) {
        // Auto load the image when it is cached
        return NetUtils.isCached(postImage.imageUrl)
                || shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get());
    }

    private boolean videoAutoLoad(PostImage postImage) {
        return imageAutoLoad(postImage) && shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get());
    }

    private void setTitle(PostImage postImage, int position) {
        callback.setTitle(postImage,
                position,
                images.size(),
                postImage.spoiler() && callback.getImageMode(postImage) == LOWRES
        );
    }

    private List<PostImage> getOther(int position) {
        List<PostImage> other = new ArrayList<>(3);
        if (position - 1 >= 0) {
            other.add(images.get(position - 1));
        }
        if (position + 1 < images.size()) {
            other.add(images.get(position + 1));
        }
        return other;
    }

    public void showImageSearchOptions(NavigationItem navigation) {
        List<FloatingMenuItem<Integer>> items = new ArrayList<>();
        for (ImageSearch imageSearch : ImageSearch.engines) {
            items.add(new FloatingMenuItem<>(imageSearch.getId(), imageSearch.getName()));
        }
        ToolbarMenuItem overflowMenuItem = navigation.findOverflow();
        FloatingMenu<Integer> menu = new FloatingMenu<>(context, overflowMenuItem.getView(), items);
        menu.setCallback(new FloatingMenu.ClickCallback<Integer>() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu<Integer> menu, FloatingMenuItem<Integer> item) {
                for (ImageSearch imageSearch : ImageSearch.engines) {
                    if (item.getId() == imageSearch.getId()) {
                        final HttpUrl searchImageUrl = getSearchImageUrl(getCurrentPostImage());
                        if (searchImageUrl == null) {
                            Logger.e(this, "onFloatingMenuItemClicked() searchImageUrl == null");
                            break;
                        }

                        openLinkInBrowser(context, imageSearch.getUrl(searchImageUrl.toString()));
                        break;
                    }
                }
            }
        });
        menu.show();
    }

    /**
     * Send thumbnail image of movie posts because none of the image search providers support movies (such as webm) directly
     *
     * @param postImage the post image
     * @return url of an image to be searched
     */
    @Nullable
    private HttpUrl getSearchImageUrl(final PostImage postImage) {
        return postImage.type == PostImage.Type.MOVIE ? postImage.thumbnailUrl : postImage.imageUrl;
    }

    private enum SwipeDirection {
        Default,
        Forward,
        Backward
    }

    public interface Callback {
        void startPreviewInTransition(PostImage postImage);

        void startPreviewOutTransition(PostImage postImage);

        void setPreviewVisibility(boolean visible);

        void setPagerVisiblity(boolean visible);

        void setPagerItems(List<PostImage> images, int initialIndex);

        void setImageMode(PostImage postImage, MultiImageView.Mode mode, boolean center);

        void setVolume(PostImage postImage, boolean muted);

        void setTitle(PostImage postImage, int index, int count, boolean spoiler);

        void scrollToImage(PostImage postImage);

        void updatePreviewImage(PostImage postImage);

        void saveImage();

        MultiImageView.Mode getImageMode(PostImage postImage);

        void showProgress(boolean show);

        void onLoadProgress(float progress);

        void showVolumeMenuItem(boolean show, boolean muted);

        void showOpacityMenuItem(boolean show, boolean opaque);

        void showDownloadMenuItem(boolean show);

        boolean isImmersive();

        void showSystemUI(boolean show);
    }
}
