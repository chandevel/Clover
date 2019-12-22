package com.github.adamantcheese.chan.core.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageLoader.ImageListener;
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

import static com.github.adamantcheese.chan.utils.BackgroundUtils.runOnUiThread;

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
            ImageListener imageListener
    ) {
        BackgroundUtils.ensureMainThread();

        if (loadable.isLocal() || loadable.isDownloading()) {
            String formattedName;
            Logger.d(TAG, "Loading image " + postImage.imageUrl.toString() + " from the disk");

            if (postImage.spoiler) {
                String extension = StringUtils.extractFileNameExtension(postImage.spoilerThumbnailUrl.toString());

                formattedName = ThreadSaveManager.formatSpoilerImageName(extension);
            } else {
                if (isThumbnail) {
                    String extension = StringUtils.extractFileNameExtension(postImage.thumbnailUrl.toString());

                    if (extension == null) {
                        // We expect images to have extensions
                        throw new NullPointerException(
                                "Could not get extension from thumbnailUrl = " + postImage.thumbnailUrl.toString());
                    }

                    formattedName = ThreadSaveManager.formatThumbnailImageName(postImage.serverFilename, extension);
                } else {
                    String extension = postImage.extension;

                    formattedName = ThreadSaveManager.formatOriginalImageName(postImage.serverFilename, extension);
                }
            }

            return getFromDisk(loadable, formattedName, postImage.spoiler, imageListener, width, height, () -> {
                Logger.d(TAG, "Falling back to imageLoaderV1 load the image " + postImage.imageUrl.toString());

                return imageLoader.get(postImage.getThumbnailUrl().toString(), imageListener, width, height);
            });
        } else {
            Logger.d(TAG, "Loading image " + postImage.imageUrl.toString() + " via the imageLoaderV1");

            return imageLoader.get(postImage.getThumbnailUrl().toString(), imageListener, width, height);
        }
    }

    public ImageLoader.ImageContainer getFromDisk(
            Loadable loadable,
            String filename,
            boolean isSpoiler,
            ImageLoader.ImageListener imageListener,
            int width,
            int height,
            @Nullable ImageLoaderFallbackCallback callback
    ) {
        BackgroundUtils.ensureMainThread();

        ImageLoader.ImageContainer container = null;

        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Constructor c = ImageLoader.ImageContainer.class.getConstructor(ImageLoader.class,
                    Bitmap.class,
                    String.class,
                    String.class,
                    ImageListener.class
            );

            c.setAccessible(true);
            container = (ImageLoader.ImageContainer) c.newInstance(imageLoader, null, null, null, imageListener);
        } catch (Exception failedSomething) {
            Logger.e(TAG, "Reflection failed", failedSomething);
            return null;
        }

        final ImageLoader.ImageContainer finalContainer = container;

        diskLoaderExecutor.execute(() -> {
            try {
                if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
                    throw new IOException("Base local threads directory does not exist");
                }

                AbstractFile baseDirFile = fileManager.newBaseDirectoryFile(LocalThreadsBaseDirectory.class);
                if (baseDirFile == null) {
                    // User has deleted the base directory with all the files,
                    // fallback to loading the image from the server
                    Logger.w(TAG, "Base saved files directory does not exist");

                    if (imageListener != null && callback != null) {
                        runOnUiThread(() -> imageListener.onResponse(callback.onLocalImageDoesNotExist(), true));
                    }

                    return;
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
                    // Local file does not exist, fallback to loading the image from the server
                    Logger.d(TAG, "Local image does not exist (or is inaccessible)");

                    if (imageListener != null && callback != null) {
                        runOnUiThread(() -> imageListener.onResponse(callback.onLocalImageDoesNotExist(), true));
                    }
                    return;
                }

                try (InputStream inputStream = fileManager.getInputStream(imageOnDiskFile)) {
                    // Image exists on the disk - try to load it and put in the cache
                    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                    bitmapOptions.outWidth = width;
                    bitmapOptions.outHeight = height;

                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, bitmapOptions);

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
                // Some error has occurred, fallback to loading the image from the server
                Logger.e(TAG, "Error while trying to load a local image", e);

                if (imageListener != null && callback != null) {
                    runOnUiThread(() -> imageListener.onResponse(callback.onLocalImageDoesNotExist(), true));
                }
            }
        });

        return container;
    }

    private void postError(ImageListener imageListener, String message) {
        mainThreadHandler.post(() -> {
            if (imageListener != null) {
                imageListener.onErrorResponse(new VolleyError(message));
            }
        });
    }

    public void cancelRequest(ImageLoader.ImageContainer container) {
        BackgroundUtils.ensureMainThread();
        container.cancelRequest();
    }

    public ImageLoader.ImageContainer get(String requestUrl, ImageListener listener) {
        BackgroundUtils.ensureMainThread();
        return imageLoader.get(requestUrl, listener);
    }

    public ImageLoader.ImageContainer get(String requestUrl, ImageListener listener, int width, int height) {
        BackgroundUtils.ensureMainThread();

        return imageLoader.get(requestUrl, listener, width, height);
    }

    private interface ImageLoaderFallbackCallback {
        ImageLoader.ImageContainer onLocalImageDoesNotExist();
    }
}
