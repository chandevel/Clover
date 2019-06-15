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
package com.github.adamantcheese.chan.core.cache;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;

public class FileCache implements FileCacheDownloader.Callback {
    private static final String TAG = "FileCache";
    private static final int TIMEOUT = 10000;
    private static final int DOWNLOAD_POOL_SIZE = 2;

    private final ExecutorService downloadPool = Executors.newFixedThreadPool(DOWNLOAD_POOL_SIZE);
    protected OkHttpClient httpClient;

    private final CacheHandler cacheHandler;

    private List<FileCacheDownloader> downloaders = new ArrayList<>();

    public FileCache(File directory) {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                // Disable SPDY, causes reproducible timeouts, only one download at the same time and other fun stuff
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();

        cacheHandler = new CacheHandler(directory);
    }

    public void clearCache() {
        for (FileCacheDownloader downloader : downloaders) {
            downloader.cancel();
        }

        cacheHandler.clearCache();
    }

    @MainThread
    public FileCacheDownloader downloadFile(
            Loadable loadable,
            @NonNull PostImage postImage,
            FileCacheListener listener) {
        if (loadable.isSavedCopy) {
            String filename = ThreadSaveManager.formatOriginalImageName(
                    postImage.originalName, postImage.extension);

            String imageDir = ThreadSaveManager.getImagesSubDir(loadable, loadable.no);
            File fullImagePath = new File(ChanSettings.saveLocation.get(), imageDir);
            File imageOnDiskFile = new File(fullImagePath, filename);

            if (imageOnDiskFile.exists()
                    && imageOnDiskFile.isFile()
                    && imageOnDiskFile.canRead()) {
                handleFileImmediatelyAvailable(listener, imageOnDiskFile);
            } else {
                Logger.e(TAG, "Cannot load saved image from the disk, path: "
                        + imageOnDiskFile.getAbsolutePath());
            }

            return null;
        } else {
            return downloadFile(postImage.imageUrl.toString(), listener);
        }
    }

    /**
     * Start downloading the file located at the url.<br>
     * If the file is in the cache then the callback is executed immediately and null is
     * returned.<br>
     * Otherwise if the file is downloading or has not yet started downloading a
     * {@link FileCacheDownloader} is returned.<br>
     *
     * @param url      the url to download.
     * @param listener listener to execute callbacks on.
     * @return {@code null} if in the cache, {@link FileCacheDownloader} otherwise.
     */
    @MainThread
    public FileCacheDownloader downloadFile(@NonNull String url, FileCacheListener listener) {
        FileCacheDownloader runningDownloaderForKey = getDownloaderByKey(url);
        if (runningDownloaderForKey != null) {
            if (listener != null) {
                runningDownloaderForKey.addListener(listener);
            }
            return runningDownloaderForKey;
        }

        File file = get(url);
        if (file.exists()) {
            handleFileImmediatelyAvailable(listener, file);
            return null;
        } else {
            return handleStartDownload(listener, file, url);
        }
    }

    public FileCacheDownloader getDownloaderByKey(String key) {
        for (FileCacheDownloader downloader : downloaders) {
            if (downloader.getUrl().equals(key)) {
                return downloader;
            }
        }
        return null;
    }

    @Override
    public void downloaderFinished(FileCacheDownloader fileCacheDownloader) {
        downloaders.remove(fileCacheDownloader);
    }

    @Override
    public void downloaderAddedFile(File file) {
        cacheHandler.fileWasAdded(file);
    }

    public boolean exists(String key) {
        return cacheHandler.exists(key);
    }

    public File get(String key) {
        return cacheHandler.get(key);
    }

    public long getFileCacheSize() {
        return cacheHandler.getSize().get();
    }

    private void handleFileImmediatelyAvailable(FileCacheListener listener, File file) {
        // TODO: setLastModified doesn't seem to work on Android...
        if (!file.setLastModified(System.currentTimeMillis())) {
            Logger.e(TAG, "Could not set last modified time on file");
        }
        if (listener != null) {
            listener.onSuccess(file);
            listener.onEnd();
        }
    }

    private FileCacheDownloader handleStartDownload(
            FileCacheListener listener, File file, String url) {
        FileCacheDownloader downloader = FileCacheDownloader.fromCallbackClientUrlOutputUserAgent(
                this, httpClient, url, file);
        if (listener != null) {
            downloader.addListener(listener);
        }
        downloader.execute(downloadPool);
        downloaders.add(downloader);
        return downloader;
    }
}
