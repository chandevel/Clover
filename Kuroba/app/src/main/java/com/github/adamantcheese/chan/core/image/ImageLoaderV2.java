package com.github.adamantcheese.chan.core.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ImageLoaderV2 {
    private static final String TAG = "ImageLoaderV2";

    private ImageLoader imageLoader;
    private Executor diskLoaderExecutor = Executors.newSingleThreadExecutor();
    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public ImageLoaderV2(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    public ImageContainer getImage(
            boolean isThumbnail,
            Loadable loadable,
            PostImage postImage,
            int width,
            int height,
            ImageListener imageListener) {
        if (loadable.isSavedCopy) {
            String formattedName;

            if (postImage.spoiler) {
                String extension = StringUtils.extractFileExtensionFromImageUrl(
                        postImage.spoilerThumbnailUrl.toString());

                formattedName = ThreadSaveManager.formatSpoilerImageName(extension);
            } else {
                if (isThumbnail) {
                    String extension = StringUtils.extractFileExtensionFromImageUrl(
                            postImage.thumbnailUrl.toString());

                    if (extension == null) {
                        // We expect images to have extensions
                        throw new NullPointerException("Could not extract extension from a thumbnailUrl = "
                                + postImage.thumbnailUrl.toString());
                    }

                    formattedName = ThreadSaveManager.formatThumbnailImageName(
                            postImage.originalName,
                            extension
                    );
                } else {
                    String extension = postImage.extension;

                    formattedName = ThreadSaveManager.formatOriginalImageName(
                            postImage.originalName,
                            extension
                    );
                }
            }

            return getFromDisk(
                    loadable,
                    formattedName,
                    postImage.spoiler,
                    imageListener,
                    width,
                    height);
        } else {
            return imageLoader.get(
                    postImage.getThumbnailUrl().toString(),
                    imageListener,
                    width,
                    height);
        }
    }

    public ImageContainer getFromDisk(
            Loadable loadable,
            String filename,
            boolean isSpoiler,
            ImageListener imageListener,
            int width,
            int height) {
        ImageContainer container = new ImageContainer(null, null, null, imageListener);

        diskLoaderExecutor.execute(() -> {
            String imageDir;
            if (isSpoiler) {
                imageDir = ThreadSaveManager.getBoardSubDir(loadable);
            } else {
                imageDir = ThreadSaveManager.getImagesSubDir(loadable);
            }

            File fullImagePath = new File(ChanSettings.saveLocation.get(), imageDir);
            File imageOnDiskFile = new File(fullImagePath, filename);
            String imageOnDisk = imageOnDiskFile.getAbsolutePath();

            if (!imageOnDiskFile.exists() || !imageOnDiskFile.isFile() || !imageOnDiskFile.canRead()) {
                String errorMessage = "Could not load image from the disk: " +
                        "(path = " + imageOnDiskFile.getAbsolutePath() +
                        ", exists = " + imageOnDiskFile.exists() +
                        ", isFile = " + imageOnDiskFile.isFile() +
                        ", canRead = " + imageOnDiskFile.canRead() + ")";
                Logger.e(TAG, errorMessage);

                mainThreadHandler.post(() -> {
                    if (container.getListener() != null) {
                        container.getListener().onErrorResponse(new VolleyError(errorMessage));
                    }
                });
                return;
            }

            // Image exists on the disk - try to load it and put in the cache
            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.outWidth = width;
            bitmapOptions.outHeight = height;

            Bitmap bitmap = BitmapFactory.decodeFile(imageOnDisk, bitmapOptions);
            if (bitmap == null) {
                Logger.e(TAG, "Could not decode bitmap");

                mainThreadHandler.post(() -> {
                    if (container.getListener() != null) {
                        container.getListener().onErrorResponse(new VolleyError("Could not decode bitmap"));
                    }
                });
                return;
            }

            mainThreadHandler.post(() -> {
                container.setBitmap(bitmap);
                container.setRequestUrl(imageDir);
                imageListener.onResponse(container, true);
            });
        });

        return container;
    }

    public void cancelRequest(ImageContainer container) {
        imageLoader.cancelRequest(container);
    }

    public ImageContainer get(
            String requestUrl,
            ImageListener listener) {
        return imageLoader.get(requestUrl, listener);
    }

    public ImageContainer get(
            String requestUrl,
            ImageListener listener,
            int width,
            int height) {
        return imageLoader.get(requestUrl, listener, width, height);
    }
}
