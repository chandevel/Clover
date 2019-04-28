/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.presenter;

import android.net.ConnectivityManager;
import android.support.v4.view.ViewPager;

import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.cache.FileCacheDownloader;
import org.floens.chan.core.cache.FileCacheListener;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.view.MultiImageView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.utils.AndroidUtils.isConnected;

public class ImageViewerPresenter implements MultiImageView.Callback, ViewPager.OnPageChangeListener {
    private static final String TAG = "ImageViewerPresenter";

    private final Callback callback;

    @Inject
    FileCache fileCache;

    private boolean entering = true;
    private boolean exiting = false;
    private List<PostImage> images;
    private List<Float> progress;
    private int selectedPosition;
    private Loadable loadable;

    private Set<FileCacheDownloader> preloadingImages = new HashSet<>();

    // Disables swiping until the view pager is visible
    private boolean viewPagerVisible = false;
    private boolean changeViewsOnInTransitionEnd = false;

    private boolean muted;

    public ImageViewerPresenter(Callback callback) {
        this.callback = callback;
        inject(this);

        muted = ChanSettings.videoDefaultMuted.get();
    }

    public void showImages(List<PostImage> images, int position, Loadable loadable) {
        this.images = images;
        selectedPosition = Math.max(0, Math.min(images.size() - 1, position));
        this.loadable = loadable;

        progress = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            progress.add(i, -1f);
        }

        // Do this before the view is measured, to avoid it to always loading the first two pages
        callback.setPagerItems(images, selectedPosition);
        callback.setImageMode(images.get(selectedPosition), MultiImageView.Mode.LOWRES, true);
    }

    public void onViewMeasured() {
        // Pager is measured, but still invisible
        callback.startPreviewInTransition(images.get(selectedPosition));
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
        if (postImage.type == PostImage.Type.MOVIE) {
            // VideoView doesn't work with invisible visibility
            callback.setImageMode(postImage, MultiImageView.Mode.LOWRES, true);
        }

        callback.setPagerVisiblity(false);
        callback.setPreviewVisibility(true);
        callback.startPreviewOutTransition(postImage);
        callback.showProgress(false);

        cancelPreloadingImages();
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
                for (PostImage other : getOther(selectedPosition)) {
                    callback.setImageMode(other, MultiImageView.Mode.LOWRES, false);
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

        PostImage postImage = images.get(selectedPosition);
        setTitle(postImage, position);
        callback.scrollToImage(postImage);

        for (PostImage other : getOther(position)) {
            callback.setImageMode(other, MultiImageView.Mode.LOWRES, false);
        }

        // Already in LOWRES mode
        if (callback.getImageMode(postImage) == MultiImageView.Mode.LOWRES) {
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

        if (imageAutoLoad(postImage) && !postImage.spoiler) {
            if (postImage.type == PostImage.Type.STATIC) {
                callback.setImageMode(postImage, MultiImageView.Mode.BIGIMAGE, true);
            } else if (postImage.type == PostImage.Type.GIF) {
                callback.setImageMode(postImage, MultiImageView.Mode.GIF, true);
            } else if (postImage.type == PostImage.Type.MOVIE && videoAutoLoad(postImage)) {
                callback.setImageMode(postImage, MultiImageView.Mode.MOVIE, true);
            }
        }

        preloadNext();
    }

    // This won't actually change any modes, but it will preload the image so that it's
    // available immediately when the user swipes right.
    private void preloadNext() {
        if (selectedPosition + 1 < images.size()) {
            PostImage next = images.get(selectedPosition + 1);

            boolean load = false;
            if (next.type == PostImage.Type.STATIC || next.type == PostImage.Type.GIF) {
                load = imageAutoLoad(next);
            } else if (next.type == PostImage.Type.MOVIE) {
                load = videoAutoLoad(next);
            }

            if (load) {
                final String fileUrl = next.imageUrl.toString();

                // If downloading, remove from preloadingImages if it finished.
                // Array to allow access from within the callback (the callback should really
                // pass the filecachedownloader itself).
                final FileCacheDownloader[] preloadDownload =
                        new FileCacheDownloader[1];
                preloadDownload[0] = fileCache.downloadFile(fileUrl,
                        new FileCacheListener() {
                            @Override
                            public void onEnd() {
                                if (preloadDownload[0] != null) {
                                    preloadingImages.remove(preloadDownload[0]);
                                }
                            }
                        }
                );

                if (preloadDownload[0] != null) {
                    preloadingImages.add(preloadDownload[0]);
                }
            }
        }
    }

    private void cancelPreloadingImages() {
        for (FileCacheDownloader preloadingImage : preloadingImages) {
            preloadingImage.cancel();
        }
        preloadingImages.clear();
    }

    @Override
    public void onTap() {
        // Don't mistake a swipe when the pager is disabled as a tap
        if (viewPagerVisible) {
            PostImage postImage = images.get(selectedPosition);
            if (imageAutoLoad(postImage) && !postImage.spoiler) {
                if (postImage.type == PostImage.Type.MOVIE) {
                    callback.setImageMode(postImage, MultiImageView.Mode.MOVIE, true);
                } else {
                    onExit();
                }
            } else {
                MultiImageView.Mode currentMode = callback.getImageMode(postImage);
                if (postImage.type == PostImage.Type.STATIC && currentMode != MultiImageView.Mode.BIGIMAGE) {
                    callback.setImageMode(postImage, MultiImageView.Mode.BIGIMAGE, true);
                } else if (postImage.type == PostImage.Type.GIF && currentMode != MultiImageView.Mode.GIF) {
                    callback.setImageMode(postImage, MultiImageView.Mode.GIF, true);
                } else if (postImage.type == PostImage.Type.MOVIE && currentMode != MultiImageView.Mode.MOVIE) {
                    callback.setImageMode(postImage, MultiImageView.Mode.MOVIE, true);
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
        callback.onVideoError();
    }

    @Override
    public void onVideoLoaded(MultiImageView multiImageView, boolean hasAudio) {
        PostImage currentPostImage = getCurrentPostImage();
        if (multiImageView.getPostImage() == currentPostImage) {
            if (hasAudio) {
                callback.showVolumeMenuItem(true, muted);
                callback.setVolume(currentPostImage, muted);
            }
        }
    }

    private boolean imageAutoLoad(PostImage postImage) {
        // Auto load the image when it is cached
        return fileCache.exists(postImage.imageUrl.toString()) || shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get());
    }

    private boolean videoAutoLoad(PostImage postImage) {
        return imageAutoLoad(postImage) && shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get());
    }

    private boolean shouldLoadForNetworkType(ChanSettings.MediaAutoLoadMode networkType) {
        if (networkType == ChanSettings.MediaAutoLoadMode.NONE) {
            return false;
        } else if (networkType == ChanSettings.MediaAutoLoadMode.WIFI) {
            return isConnected(ConnectivityManager.TYPE_WIFI);
        } else return networkType == ChanSettings.MediaAutoLoadMode.ALL;

        // Not connected or unrecognized
    }

    private void setTitle(PostImage postImage, int position) {
        callback.setTitle(postImage, position, images.size(),
                postImage.spoiler && callback.getImageMode(postImage) == MultiImageView.Mode.LOWRES);
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

        MultiImageView.Mode getImageMode(PostImage postImage);

        void showProgress(boolean show);

        void onLoadProgress(float progress);

        void onVideoError();

        void showVolumeMenuItem(boolean show, boolean muted);
    }
}
