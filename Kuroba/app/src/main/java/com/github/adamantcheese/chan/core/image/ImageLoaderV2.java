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
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.FileSegment;
import com.github.k1rakishou.fsaf.file.Segment;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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

    public ImageLoader.ImageContainer getImage(
            boolean isThumbnail,
            Loadable loadable,
            PostImage postImage,
            int width,
            int height,
            ImageLoader.ImageListener imageListener
    ) {
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
                            postImage.serverFilename,
                            extension
                    );
                } else {
                    String extension = postImage.extension;

                    formattedName = ThreadSaveManager.formatOriginalImageName(
                            postImage.serverFilename,
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
                    height
            );
        } else {
            return imageLoader.get(
                    postImage.getThumbnailUrl().toString(),
                    imageListener,
                    width,
                    height
            );
        }
    }

    public ImageLoader.ImageContainer getFromDisk(
            Loadable loadable,
            String filename,
            boolean isSpoiler,
            ImageLoader.ImageListener imageListener,
            int width,
            int height
    ) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        ImageLoader.ImageContainer container = null;

        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Constructor c = ImageLoader.ImageContainer.class.getConstructor(
                    ImageLoader.class,
                    Bitmap.class,
                    String.class,
                    String.class,
                    ImageLoader.ImageListener.class
            );

            c.setAccessible(true);
            container = (ImageLoader.ImageContainer) c.newInstance(
                    imageLoader,
                    null,
                    null,
                    null,
                    imageListener
            );

        } catch (Exception failedSomething) {
            return container;
        }

        final ImageLoader.ImageContainer finalContainer = container;

        diskLoaderExecutor.execute(() -> {
            try {
                if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
                    throw new IOException("Base local threads directory does not exist");
                }

                AbstractFile baseDirFile = fileManager.newBaseDirectoryFile(
                        LocalThreadsBaseDirectory.class
                );

                if (baseDirFile == null) {
                    throw new IOException("getFromDisk() " +
                            "fileManager.newLocalThreadFile() returned null");
                }

                List<Segment> segments = new ArrayList<>();

                if (isSpoiler) {
                    segments.addAll(ThreadSaveManager.getBoardSubDir(loadable));
                } else {
                    segments.addAll(ThreadSaveManager.getImagesSubDir(loadable));
                }

                segments.add(new FileSegment(filename));
                AbstractFile imageOnDiskFile = baseDirFile.clone(segments);

                boolean exists = fileManager.exists(imageOnDiskFile);
                boolean isFile = fileManager.isFile(imageOnDiskFile);
                boolean canRead = fileManager.canRead(imageOnDiskFile);

                if (!exists || !isFile || !canRead) {
                    String errorMessage = "Could not load image from the disk: " +
                            "(path = " + imageOnDiskFile.getFullPath() +
                            ", exists = " + exists +
                            ", isFile = " + isFile +
                            ", canRead = " + canRead + ")";

                    Logger.e(TAG, errorMessage);
                    postError(imageListener, errorMessage);
                    return;
                }

                try (InputStream inputStream = fileManager.getInputStream(imageOnDiskFile)) {
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
                        postError(imageListener, "Could not decode bitmap");
                        return;
                    }

                    mainThreadHandler.post(() -> {
                        try {
                            Field bitmapField = finalContainer.getClass().getDeclaredField("mBitmap");
                            Field urlField = finalContainer.getClass().getDeclaredField("mRequestUrl");
                            bitmapField.setAccessible(true);
                            urlField.setAccessible(true);
                            bitmapField.set(finalContainer, bitmap);
                            urlField.set(finalContainer, imageOnDiskFile.getFullPath());

                            if (imageListener != null) {
                                imageListener.onResponse(finalContainer, true);
                            }
                        } catch (Exception e) {
                            postError(imageListener, "Couldn't set fields");
                        }
                    });
                }
            } catch (Exception e) {
                String message = "Could not get an image from the disk, error message = "
                        + e.getMessage();

                postError(imageListener, message);
            }
        });

        return container;
    }

    private void postError(ImageLoader.ImageListener imageListener, String message) {
        mainThreadHandler.post(() -> {
            if (imageListener != null) {
                imageListener.onErrorResponse(new VolleyError(message));
            }
        });
    }

    public void cancelRequest(ImageLoader.ImageContainer container) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        container.cancelRequest();
    }

    public ImageLoader.ImageContainer get(
            String requestUrl,
            ImageLoader.ImageListener listener
    ) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        return imageLoader.get(requestUrl, listener);
    }

    public ImageLoader.ImageContainer get(
            String requestUrl,
            ImageLoader.ImageListener listener,
            int width,
            int height
    ) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        return imageLoader.get(requestUrl, listener, width, height);
    }
}
