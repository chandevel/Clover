package com.github.adamantcheese.chan.core.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.FileSegment;
import com.github.k1rakishou.fsaf.file.Segment;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import okhttp3.Call;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl;

public class ImageLoaderV2 {
    private static final String TAG = "ImageLoaderV2";

    public static void getImage(
            Loadable loadable, PostImage postImage, int width, int height, @NonNull NetUtils.BitmapResult imageListener
    ) {
        BackgroundUtils.ensureMainThread();

        if (loadable.isLocal() || loadable.isDownloading()) {
            String formattedName;
            Logger.d(TAG, "Loading image " + getImageUrlForLogs(postImage) + " from the disk");

            if (postImage.spoiler()) {
                String extension = StringUtils.extractFileNameExtension(postImage.spoilerThumbnailUrl.toString());

                formattedName = ThreadSaveManager.formatSpoilerImageName(extension);
            } else {
                String extension = StringUtils.extractFileNameExtension(postImage.thumbnailUrl.toString());

                if (extension == null) {
                    // We expect images to have extensions
                    imageListener.onBitmapFailure(
                            null,
                            new NullPointerException("Could not get extension from thumbnailUrl = " + maskImageUrl(
                                    postImage.thumbnailUrl))
                    );
                }

                formattedName = ThreadSaveManager.formatThumbnailImageName(postImage.serverFilename, extension);
            }

            try {
                getFromDisk(
                        loadable,
                        formattedName,
                        postImage.spoiler(),
                        imageListener,
                        width,
                        height,
                        () -> doFallback(postImage, imageListener, width, height)
                );
            } catch (Exception e) {
                doFallback(postImage, imageListener, width, height);
            }
        } else {
            doFallback(postImage, imageListener, width, height);
        }
    }

    private static Call doFallback(PostImage postImage, NetUtils.BitmapResult imageListener, int width, int height) {
        Logger.d(TAG, "Falling back to imageLoaderV1 load the image " + getImageUrlForLogs(postImage));
        return NetUtils.makeBitmapRequest(postImage.getThumbnailUrl(), imageListener, width, height);
    }

    public static Call getFromDisk(
            Loadable loadable,
            String filename,
            boolean isSpoiler,
            @NonNull NetUtils.BitmapResult imageListener,
            int width,
            int height,
            @Nullable ImageLoaderFallback callback
    )
            throws Exception {
        BackgroundUtils.ensureMainThread();

        return instance(ExecutorService.class).submit(() -> {
            FileManager fileManager = instance(FileManager.class);
            try {
                if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
                    throw new IOException("Base local threads directory does not exist");
                }

                AbstractFile baseDirFile = fileManager.newBaseDirectoryFile(LocalThreadsBaseDirectory.class);
                if (baseDirFile == null) {
                    // User has deleted the base directory with all the files,
                    // fallback to loading the image from the server
                    Logger.w(TAG, "Base saved files directory does not exist");

                    if (callback != null) {
                        return callback.onLocalImageDoesNotExist();
                    }
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

                    if (callback != null) {
                        return callback.onLocalImageDoesNotExist();
                    }
                }

                try (InputStream inputStream = fileManager.getInputStream(imageOnDiskFile)) {
                    // Image exists on the disk - try to load it and put in the cache
                    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                    bitmapOptions.outWidth = width;
                    bitmapOptions.outHeight = height;

                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, bitmapOptions);

                    if (bitmap == null) {
                        Logger.e(TAG, "Could not decode bitmap");
                        imageListener.onBitmapFailure(null, new Exception("Could not decode bitmap"));
                        return null;
                    }

                    imageListener.onBitmapSuccess(bitmap, false);
                }
            } catch (Exception e) {
                // Some error has occurred, fallback to loading the image from the server
                Logger.e(TAG, "Error while trying to load a local image", e);

                if (callback != null) {
                    return callback.onLocalImageDoesNotExist();
                }
            }
            return null;
        }).get();
    }

    private static String getImageUrlForLogs(PostImage postImage) {
        if (postImage.imageUrl != null) {
            return maskImageUrl(postImage.imageUrl);
        } else if (postImage.thumbnailUrl != null) {
            return maskImageUrl(postImage.thumbnailUrl);
        }

        return "No image url";
    }

    private interface ImageLoaderFallback {
        Call onLocalImageDoesNotExist();
    }
}
