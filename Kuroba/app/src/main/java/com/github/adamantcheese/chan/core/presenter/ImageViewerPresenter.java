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

import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.cache.downloader.DownloadRequestExtraInfo;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.ImageSearch;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenu;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.MultiImageView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.GIF;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.IFRAME;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.MOVIE;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.STATIC;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.MediaAutoLoadMode.shouldLoadForNetworkType;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.BIGIMAGE;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.GIFIMAGE;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.LOWRES;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.VIDEO;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.WEBVIEW;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAudioManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;

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

    @Inject
    FileCacheV2 fileCacheV2;
    @Inject
    CacheHandler cacheHandler;

    private boolean entering = true;
    private boolean exiting = false;
    private List<PostImage> images;
    private Map<Integer, Float[]> progress;
    private int selectedPosition = 0;
    private SwipeDirection swipeDirection = SwipeDirection.Default;
    private Loadable loadable;
    private final Set<CancelableDownload> preloadingImages = new HashSet<>();
    private final Set<HttpUrl> nonCancelableImages = new HashSet<>();

    // Disables swiping until the view pager is visible
    private boolean viewPagerVisible = false;
    private boolean changeViewsOnInTransitionEnd = false;

    private boolean muted = ChanSettings.videoDefaultMuted.get() && (ChanSettings.headsetDefaultMuted.get()
            || !getAudioManager().isWiredHeadsetOn());

    public ImageViewerPresenter(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        inject(this);
    }

    public void showImages(List<PostImage> images, int position, Loadable loadable) {
        this.images = images;
        this.loadable = loadable;
        this.selectedPosition = Math.max(0, Math.min(images.size() - 1, position));
        this.progress = new HashMap<>(images.size());

        for (int i = 0; i < images.size(); ++i) {
            Float[] initialProgress =
                    new Float[Math.min(4, loadable.site.getChunkDownloaderSiteProperties().maxChunksForSite)];
            Arrays.fill(initialProgress, 0f);
            progress.put(i, initialProgress);
        }

        // Do this before the view is measured, to avoid it to always loading the first two pages
        callback.setPagerItems(images, selectedPosition);
        callback.setImageMode(images.get(selectedPosition), LOWRES, true);
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

        PostImage postImage = images.get(selectedPosition);
        if (postImage.type == MOVIE || postImage.type == IFRAME) {
            callback.setImageMode(postImage, LOWRES, true);
        }

        callback.showDownloadMenuItem(false);
        callback.setPagerVisiblity(false);
        callback.setPreviewVisibility(true);
        callback.startPreviewOutTransition(postImage);
        callback.showProgress(false);

        for (CancelableDownload preloadingImage : preloadingImages) {
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
                if (multiImageView.getPostImage() == images.get(selectedPosition)) {
                    onLowResInCenter();
                }
            }
        } else {
            if (multiImageView.getPostImage() == images.get(selectedPosition)) {
                setTitle(images.get(selectedPosition), selectedPosition);
            }
        }
    }

    private void onPageSwipedTo(int position) {
        PostImage postImage = images.get(selectedPosition);
        // Reset volume icon.
        // If it has audio, we'll know after it is loaded.
        callback.showVolumeMenuItem(postImage.type == MOVIE, muted);

        //Reset the save icon
        callback.showDownloadMenuItem(false);

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
        PostImage postImage = images.get(selectedPosition);

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
        boolean loadChunked = true;

        if (postImage.type == STATIC || postImage.type == GIF) {
            load = imageAutoLoad(postImage);
        } else if (postImage.type == MOVIE) {
            load = videoAutoLoad(postImage);
        }

        /*
         * If the file is a webm file and webm streaming is turned on we don't want to download the
         * webm chunked because it will most likely corrupt the file since we will forcefully stop
         * it.
         * */
        if (postImage.type == MOVIE && ChanSettings.videoStream.get()) {
            loadChunked = false;
        }

        if (load) {
            // If downloading, remove from preloadingImages if it finished.
            // Array to allow access from within the callback (the callback should really
            // pass the filecachedownloader itself).
            final CancelableDownload[] preloadDownload = new CancelableDownload[1];

            final FileCacheListener fileCacheListener = new FileCacheListener() {
                @Override
                public void onEnd() {
                    BackgroundUtils.ensureMainThread();

                    if (preloadDownload[0] != null) {
                        preloadingImages.remove(preloadDownload[0]);
                    }
                }
            };

            if (loadChunked) {
                DownloadRequestExtraInfo extraInfo = new DownloadRequestExtraInfo(postImage.size, postImage.fileHash);

                preloadDownload[0] = fileCacheV2.enqueueChunkedDownloadFileRequest(postImage,
                        extraInfo,
                        loadable.site.getChunkDownloaderSiteProperties(),
                        fileCacheListener
                );
            } else {
                preloadDownload[0] = fileCacheV2.enqueueNormalDownloadFileRequest(postImage, fileCacheListener);
            }

            if (preloadDownload[0] != null) {
                preloadingImages.add(preloadDownload[0]);
            }
        }
    }

    private void cancelPreviousFromEndImageDownload(int position) {
        for (CancelableDownload downloader : preloadingImages) {
            int index = position + CANCEL_IMAGE_INDEX;
            if (index < images.size()) {
                if (cancelImageDownload(index, downloader)) {
                    return;
                }
            }
        }
    }

    private void cancelPreviousFromStartImageDownload(int position) {
        for (CancelableDownload downloader : preloadingImages) {
            int index = position - CANCEL_IMAGE_INDEX;
            if (index >= 0) {
                if (cancelImageDownload(index, downloader)) {
                    return;
                }
            }
        }
    }

    private boolean cancelImageDownload(int position, CancelableDownload downloader) {
        if (nonCancelableImages.contains(downloader.getUrl())) {
            Logger.d(this, "Attempt to cancel non cancelable download for image with url: " + downloader.getUrl());
            return false;
        }

        PostImage previousImage = images.get(position);
        if (downloader.getUrl().equals(previousImage.imageUrl)) {
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
            PostImage postImage = images.get(selectedPosition);
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
    public void onStartDownload(MultiImageView multiImageView, int chunksCount) {
        BackgroundUtils.ensureMainThread();

        if (chunksCount <= 0) {
            throw new IllegalArgumentException(
                    "chunksCount must be 1 or greater than 1 " + "(actual = " + chunksCount + ")");
        }

        Float[] initialProgress = new Float[chunksCount];
        Arrays.fill(initialProgress, 0f);

        for (int i = 0; i < images.size(); i++) {
            PostImage postImage = images.get(i);
            if (postImage == multiImageView.getPostImage()) {
                progress.put(i, initialProgress);
                break;
            }
        }

        if (multiImageView.getPostImage() == images.get(selectedPosition)) {
            callback.showProgress(true);
            callback.onLoadProgress(initialProgress);
        }
    }

    @Override
    public void onDownloaded(PostImage postImage) {
        BackgroundUtils.ensureMainThread();

        if (getCurrentPostImage().equals(postImage) && !postImage.deleted) { // don't allow saving the "deleted" image
            callback.showDownloadMenuItem(true);
        }
    }

    @Override
    public void hideProgress(MultiImageView multiImageView) {
        BackgroundUtils.ensureMainThread();

        callback.showProgress(false);
    }

    @Override
    public void onProgress(MultiImageView multiImageView, int chunkIndex, long current, long total) {
        BackgroundUtils.ensureMainThread();

        for (int i = 0; i < images.size(); i++) {
            PostImage postImage = images.get(i);
            if (postImage == multiImageView.getPostImage()) {
                Float[] chunksProgress = progress.get(i);

                if (chunksProgress != null) {
                    if (chunkIndex >= 0 && chunkIndex < chunksProgress.length) {
                        chunksProgress[chunkIndex] = current / (float) total;
                    }
                }

                break;
            }
        }

        if (multiImageView.getPostImage() == images.get(selectedPosition) && progress.get(selectedPosition) != null) {
            callback.showProgress(true);
            callback.onLoadProgress(progress.get(selectedPosition));
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

    private boolean imageAutoLoad(PostImage postImage) {
        // Auto load the image when it is cached
        return cacheHandler.exists(postImage.imageUrl)
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

    public boolean forceReload() {
        PostImage currentImage = getCurrentPostImage();

        if (fileCacheV2.isRunning(currentImage.imageUrl)) {
            showToast(context, "Image is not yet downloaded");
            return false;
        }

        if (!cacheHandler.deleteCacheFileByUrl(currentImage.imageUrl)) {
            showToast(context, "Can't force reload because couldn't delete cached image");
            return false;
        }

        callback.setImageMode(currentImage, LOWRES, false);
        return true;
    }

    public void showImageSearchOptions(NavigationItem navigation) {
        List<FloatingMenuItem<Integer>> items = new ArrayList<>();
        for (ImageSearch imageSearch : ImageSearch.engines) {
            items.add(new FloatingMenuItem<>(imageSearch.getId(), imageSearch.getName()));
        }
        ToolbarMenuItem overflowMenuItem = navigation.findItem(ToolbarMenu.OVERFLOW_ID);
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

        void onLoadProgress(Float[] progress);

        void showVolumeMenuItem(boolean show, boolean muted);

        void showDownloadMenuItem(boolean show);

        boolean isImmersive();

        void showSystemUI(boolean show);
    }
}
