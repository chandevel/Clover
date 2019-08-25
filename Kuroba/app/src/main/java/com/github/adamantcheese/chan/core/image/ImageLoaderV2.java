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
import com.github.adamantcheese.chan.core.saf.FileManager;
import com.github.adamantcheese.chan.core.saf.file.AbstractFile;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ImageLoaderV2 {
    private static final String TAG = "ImageLoaderV2";

    private ImageLoader imageLoader;
    private FileManager fileManager;

    private Executor diskLoaderExecutor = Executors.newSingleThreadExecutor();
    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public ImageLoaderV2(ImageLoader imageLoader, FileManager fileManager) {
        this.imageLoader = imageLoader;
        this.fileManager = fileManager;
    }

    public ImageContainer getImage(
            boolean isThumbnail,
            Loadable loadable,
            PostImage postImage,
            int width,
            int height,
            ImageListener imageListener) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        if (loadable.isLocal()) {
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
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        ImageContainer container = new ImageContainer(
                null,
                null,
                null,
                imageListener);

        diskLoaderExecutor.execute(() -> {
            try {
                if (!fileManager.baseLocalThreadsDirectoryExists()) {
                    throw new IOException("Base local threads directory does not exist");
                }

                AbstractFile baseDirFile = fileManager.newLocalThreadFile();
                if (baseDirFile == null) {
                    throw new IOException("fileManager.newLocalThreadFile() returned null");
                }

                String imageDir;
                if (isSpoiler) {
                    imageDir = ThreadSaveManager.getBoardSubDir(loadable);
                } else {
                    imageDir = ThreadSaveManager.getImagesSubDir(loadable);
                }

                AbstractFile imageOnDiskFile = baseDirFile
                        .appendSubDirSegment(imageDir)
                        .appendFileNameSegment(filename);

                if (!imageOnDiskFile.exists()
                        || !imageOnDiskFile.isFile()
                        || !imageOnDiskFile.canRead()) {
                    String errorMessage = "Could not load image from the disk: " +
                            "(path = " + imageOnDiskFile.getFullPath() +
                            ", exists = " + imageOnDiskFile.exists() +
                            ", isFile = " + imageOnDiskFile.isFile() +
                            ", canRead = " + imageOnDiskFile.canRead() + ")";

                    Logger.e(TAG, errorMessage);
                    postError(container, errorMessage);
                    return;
                }

                try (InputStream inputStream = imageOnDiskFile.getInputStream()) {
                    // Image exists on the disk - try to load it and put in the cache
                    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                    bitmapOptions.outWidth = width;
                    bitmapOptions.outHeight = height;

                    Bitmap bitmap = BitmapFactory.decodeStream(
                            inputStream,
                            null,
                            bitmapOptions);

                    if (bitmap == null) {
                        Logger.e(TAG, "Could not decode bitmap");
                        postError(container, "Could not decode bitmap");
                        return;
                    }

                    mainThreadHandler.post(() -> {
                        container.setBitmap(bitmap);
                        container.setRequestUrl(imageDir);
                        if (container.getListener() != null) {
                    container.getListener().onResponse(container, true);
                    }});
                }
            } catch (Exception e) {
                String message = "Could not get an image from the disk, error message = "
                        + e.getMessage();
                postError(container, message);
            }
        });

        return container;
    }

    private void postError(ImageContainer container, String message) {
        mainThreadHandler.post(() -> {
            if (container.getListener() != null) {
                container.getListener().onErrorResponse(new VolleyError(message));
            }
        });
    }

    public void cancelRequest(ImageContainer container) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        imageLoader.cancelRequest(container);
    }

    public ImageContainer get(
            String requestUrl,
            ImageListener listener) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        return imageLoader.get(requestUrl, listener);
    }

    public ImageContainer get(
            String requestUrl,
            ImageListener listener,
            int width,
            int height) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        return imageLoader.get(requestUrl, listener, width, height);
    }
}
