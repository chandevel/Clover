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

import android.annotation.SuppressLint;
import android.media.AudioManager;

import androidx.viewpager.widget.ViewPager;

import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.cache.downloader.DownloadRequestExtraInfo;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.view.MultiImageView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import static android.content.Context.AUDIO_SERVICE;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.GIF;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.MOVIE;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.PDF;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.STATIC;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.MediaAutoLoadMode.shouldLoadForNetworkType;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.BIGIMAGE;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.GIFIMAGE;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.LOWRES;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.OTHER;
import static com.github.adamantcheese.chan.ui.view.MultiImageView.Mode.VIDEO;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ImageViewerPresenter
        implements MultiImageView.Callback, ViewPager.OnPageChangeListener {
    private static final String TAG = "ImageViewerPresenter";
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
    private Map<Integer, List<Float>> progress;
    private int selectedPosition = 0;
    private SwipeDirection swipeDirection = SwipeDirection.Default;
    private Loadable loadable;
    private Set<CancelableDownload> preloadingImages = new HashSet<>();
    private final Set<String> nonCancelableImages = new HashSet<>();

    // Disables swiping until the view pager is visible
    private boolean viewPagerVisible = false;
    private boolean changeViewsOnInTransitionEnd = false;

    private boolean muted;

    public ImageViewerPresenter(Callback callback) {
        this.callback = callback;
        inject(this);

        AudioManager audioManager = (AudioManager) getAppContext().getSystemService(AUDIO_SERVICE);
        muted = ChanSettings.videoDefaultMuted.get() && (ChanSettings.headsetDefaultMuted.get()
                || !audioManager.isWiredHeadsetOn());
    }

    @SuppressLint("UseSparseArrays")
    public void showImages(List<PostImage> images, int position, Loadable loadable) {
        this.images = images;
        this.loadable = loadable;
        this.selectedPosition = Math.max(0, Math.min(images.size() - 1, position));
        this.progress = new HashMap<>(images.size());

        int chunksCount = ChanSettings.concurrentDownloadChunkCount.get().toInt();

        for (int i = 0; i < images.size(); ++i) {
            List<Float> initialProgress = new ArrayList<>(chunksCount);

            for (int j = 0; j < chunksCount; ++j) {
                initialProgress.add(.1f);
            }

            // Always use a little bit of progress so it's obvious that we have started downloading
            // the image
            progress.put(i, initialProgress);
        }

        // Do this before the view is measured, to avoid it to always loading the first two pages
        callback.setPagerItems(loadable, images, selectedPosition);
        callback.setImageMode(images.get(selectedPosition), LOWRES, true);
    }

    public void onViewMeasured() {
        // Pager is measured, but still invisible
        callback.startPreviewInTransition(loadable, images.get(selectedPosition));
        PostImage postImage = images.get(selectedPosition);
        callback.setTitle(postImage, selectedPosition, images.size(), postImage.spoiler);
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
        if (postImage.type == MOVIE) {
            callback.setImageMode(postImage, LOWRES, true);
        }

        callback.showDownloadMenuItem(false);
        callback.setPagerVisiblity(false);
        callback.setPreviewVisibility(true);
        callback.startPreviewOutTransition(loadable, postImage);
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
                    callback.setPreviewVisibility(false);
                    callback.setPagerVisiblity(true);
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
        // Reset volume icon.
        // If it has audio, we'll know after it is loaded.
        callback.showVolumeMenuItem(false, true);

        //Reset the save icon
        callback.showDownloadMenuItem(false);

        PostImage postImage = images.get(selectedPosition);
        setTitle(postImage, position);
        callback.scrollToImage(postImage);

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

        if (imageAutoLoad(loadable, postImage) && (!postImage.spoiler || ChanSettings.revealimageSpoilers.get())) {
            if (postImage.type == STATIC) {
                callback.setImageMode(postImage, BIGIMAGE, true);
            } else if (postImage.type == GIF) {
                callback.setImageMode(postImage, GIFIMAGE, true);
            } else if (postImage.type == MOVIE && videoAutoLoad(loadable, postImage)) {
                callback.setImageMode(postImage, VIDEO, true);
            } else if (postImage.type == PDF) {
                callback.setImageMode(postImage, OTHER, true);
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
        BackgroundUtils.ensureMainThread();
        int index = selectedPosition - PRELOAD_IMAGE_INDEX;

        if (index >= 0 && index < images.size()) {
            doPreloading(images.get(index));
        }
    }

    // This won't actually change any modes, but it will preload the image so that it's
    // available immediately when the user swipes right.
    private void preloadNext() {
        BackgroundUtils.ensureMainThread();
        int index = selectedPosition + PRELOAD_IMAGE_INDEX;

        if (index >= 0 && index < images.size()) {
            doPreloading(images.get(index));
        }
    }

    private List<String> getNonCancelableImages(int index) {
        if (images.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> nonCancelableImages = new ArrayList<>(3);

        if (index - 1 >= 0) {
            nonCancelableImages.add(images.get(index - 1).imageUrl.toString());
        }

        if (index >= 0 && index < images.size()) {
            nonCancelableImages.add(images.get(index).imageUrl.toString());
        }

        if (index + 1 < images.size()) {
            nonCancelableImages.add(images.get(index + 1).imageUrl.toString());
        }

        return nonCancelableImages;
    }

    private void doPreloading(PostImage postImage) {
        boolean load = false;
        boolean loadChunked = true;

        if (postImage.type == STATIC || postImage.type == GIF) {
            load = imageAutoLoad(loadable, postImage);
        } else if (postImage.type == MOVIE) {
            load = videoAutoLoad(loadable, postImage);
        }

        /**
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

                preloadDownload[0] = fileCacheV2.enqueueChunkedDownloadFileRequest(loadable,
                        postImage,
                        extraInfo,
                        fileCacheListener
                );
            } else {
                preloadDownload[0] =
                        fileCacheV2.enqueueNormalDownloadFileRequest(loadable, postImage, false, fileCacheListener);
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
            Logger.d(TAG, "Attempt to cancel non cancelable download for image with url: " + downloader.getUrl());
            return false;
        }

        PostImage previousImage = images.get(position);
        if (downloader.getUrl().equals(previousImage.imageUrl.toString())) {
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
            if (imageAutoLoad(loadable, postImage) && !postImage.spoiler) {
                if (postImage.type == MOVIE) {
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
                } else if (postImage.type == PDF && currentMode != OTHER) {
                    callback.setImageMode(postImage, OTHER, true);
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
    public void onDoubleTap() {
        onExit();
    }

    @Override
    public void onStartDownload(MultiImageView multiImageView, int chunksCount) {
        BackgroundUtils.ensureMainThread();

        if (chunksCount <= 0) {
            throw new IllegalArgumentException(
                    "chunksCount must be 1 or greater than 1 " + "(actual = " + chunksCount + ")");
        }

        List<Float> initialProgress = new ArrayList<>(chunksCount);

        for (int i = 0; i < chunksCount; ++i) {
            // Always use a little bit of progress so it's obvious that we have started downloading
            // the image
            initialProgress.add(.1f);
        }

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

        if (getCurrentPostImage().equalUrl(postImage)) {
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
                List<Float> chunksProgress = progress.get(i);

                if (chunksProgress != null) {
                    if (chunkIndex >= 0 && chunkIndex < chunksProgress.size()) {
                        chunksProgress.set(chunkIndex, current / (float) total);
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
    public void onVideoLoaded(MultiImageView multiImageView) {
        callback.showVolumeMenuItem(false, muted);
    }

    @Override
    public void onAudioLoaded(MultiImageView multiImageView) {
        PostImage currentPostImage = getCurrentPostImage();
        if (multiImageView.getPostImage() == currentPostImage) {
            callback.showVolumeMenuItem(true, muted);
            callback.setVolume(currentPostImage, muted);
        }
    }

    private boolean imageAutoLoad(Loadable loadable, PostImage postImage) {
        if (loadable.isLocal()) {
            // All images are stored locally when isSavedCopy is true
            return true;
        }

        // Auto load the image when it is cached
        return cacheHandler.exists(postImage.imageUrl.toString())
                || shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get());
    }

    private boolean videoAutoLoad(Loadable loadable, PostImage postImage) {
        if (loadable.isLocal()) {
            // All videos are stored locally when isSavedCopy is true
            return true;
        }

        return imageAutoLoad(loadable, postImage) && shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get());
    }

    private void setTitle(PostImage postImage, int position) {
        callback.setTitle(postImage,
                position,
                images.size(),
                postImage.spoiler && callback.getImageMode(postImage) == LOWRES
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

        if (fileCacheV2.isRunning(currentImage.imageUrl.toString())) {
            showToast("Image is not yet downloaded");
            return false;
        }

        if (!cacheHandler.deleteCacheFileByUrl(currentImage.imageUrl.toString())) {
            showToast("Can't force reload because couldn't delete cached image");
            return false;
        }

        callback.setImageMode(currentImage, LOWRES, false);
        return true;
    }

    private enum SwipeDirection {
        Default,
        Forward,
        Backward
    }

    public interface Callback {
        void startPreviewInTransition(Loadable loadable, PostImage postImage);

        void startPreviewOutTransition(Loadable loadable, PostImage postImage);

        void setPreviewVisibility(boolean visible);

        void setPagerVisiblity(boolean visible);

        void setPagerItems(Loadable loadable, List<PostImage> images, int initialIndex);

        void setImageMode(PostImage postImage, MultiImageView.Mode mode, boolean center);

        void setVolume(PostImage postImage, boolean muted);

        void setTitle(PostImage postImage, int index, int count, boolean spoiler);

        void scrollToImage(PostImage postImage);

        MultiImageView.Mode getImageMode(PostImage postImage);

        void showProgress(boolean show);

        void onLoadProgress(List<Float> progress);

        void showVolumeMenuItem(boolean show, boolean muted);

        void showDownloadMenuItem(boolean show);

        boolean isImmersive();

        void showSystemUI(boolean show);
    }
}
