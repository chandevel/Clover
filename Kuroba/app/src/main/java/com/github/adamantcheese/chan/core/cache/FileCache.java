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
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.FileSegment;
import com.github.k1rakishou.fsaf.file.RawFile;
import com.github.k1rakishou.fsaf.file.Segment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileCache implements FileCacheDownloader.Callback {
    private static final String TAG = "FileCache";
    private static final String FILE_CACHE_DIR = "filecache";

    private final ExecutorService downloadPool = Executors.newCachedThreadPool();
    private final CacheHandler cacheHandler;
    private final FileManager fileManager;

    private List<FileCacheDownloader> downloaders = new ArrayList<>();

    public FileCache(File cacheDir, FileManager fileManager) {
        this.fileManager = fileManager;

        RawFile cacheDirFile = fileManager.fromRawFile(
                new File(cacheDir, FILE_CACHE_DIR));

        cacheHandler = new CacheHandler(fileManager, cacheDirFile);
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
        if (loadable.isLocal()) {
            String filename = ThreadSaveManager.formatOriginalImageName(
                    postImage.originalName,
                    postImage.extension);

            if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
                Logger.e(TAG, "Base local threads directory does not exist");
                return null;
            }

            AbstractFile baseDirFile = fileManager.newBaseDirectoryFile(
                    LocalThreadsBaseDirectory.class
            );

            if (baseDirFile == null) {
                Logger.e(TAG, "downloadFile() fileManager.newLocalThreadFile() returned null");
                return null;
            }

            // TODO: double check, may not work as expected
            List<Segment> segments = new ArrayList<>(ThreadSaveManager.getImagesSubDir(loadable));
            segments.add(new FileSegment(filename));

            AbstractFile imageOnDiskFile = baseDirFile.clone(segments);

            if (fileManager.exists(imageOnDiskFile)
                    && fileManager.isFile(imageOnDiskFile)
                    && fileManager.canRead(imageOnDiskFile)) {
                handleFileImmediatelyAvailable(listener, imageOnDiskFile);
            } else {
                Logger.e(TAG, "Cannot load saved image from the disk, path: "
                        + imageOnDiskFile.getFullPath());

                if (listener != null) {
                    listener.onFail(true);
                    listener.onEnd();
                }
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

        RawFile file = get(url);
        if (fileManager.exists(file)) {
            handleFileImmediatelyAvailable(listener, file);
            return null;
        } else {
            return handleStartDownload(fileManager, listener, file, url);
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
    public void downloaderAddedFile(long fileLen) {
        cacheHandler.fileWasAdded(fileLen);
    }

    public boolean exists(String key) {
        return cacheHandler.exists(key);
    }

    public RawFile get(String key) {
        return cacheHandler.get(key);
    }

    public long getFileCacheSize() {
        return cacheHandler.getSize().get();
    }

    private void handleFileImmediatelyAvailable(FileCacheListener listener, AbstractFile file) {
        // TODO: setLastModified doesn't seem to work on Android...
//        if (!file.setLastModified(System.currentTimeMillis())) {
//            Logger.e(TAG, "Could not set last modified time on file");
//        }

        if (listener != null) {
            if (file instanceof RawFile) {
                listener.onSuccess((RawFile) file);
            } else {
                try {
                    RawFile resultFile = fileManager.fromRawFile(cacheHandler.randomCacheFile());
                    if (!fileManager.copyFileContents(file, resultFile)) {
                        throw new IOException("Could not copy external SAF file into internal " +
                                "cache file, externalFile = " + file.getFullPath() +
                                ", resultFile = " + resultFile.getFullPath());
                    }

                    listener.onSuccess(resultFile);
                } catch (IOException e) {
                    Logger.e(TAG, "Error while trying to create a new random cache file", e);
                    listener.onFail(false);
                }
            }

            listener.onEnd();
        }
    }

    private FileCacheDownloader handleStartDownload(
            FileManager fileManager,
            FileCacheListener listener,
            RawFile file,
            String url
    ) {
        FileCacheDownloader downloader = new FileCacheDownloader(fileManager, this, url, file);
        if (listener != null) {
            downloader.addListener(listener);
        }
        downloadPool.submit(downloader);
        downloaders.add(downloader);
        return downloader;
    }
}
