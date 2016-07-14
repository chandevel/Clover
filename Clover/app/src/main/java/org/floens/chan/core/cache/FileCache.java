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
package org.floens.chan.core.cache;

import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Time;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;
import okio.BufferedSource;

public class FileCache {
    private static final String TAG = "FileCache";
    private static final int TIMEOUT = 10000;
    private static final int TRIM_TRIES = 20;
    private static final int THREAD_COUNT = 2;

    private static final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    private String userAgent;
    private OkHttpClient httpClient;

    private final File directory;
    private final long maxSize;
    private long size;

    private List<FileCacheDownloader> downloaders = new ArrayList<>();

    public FileCache(File directory, long maxSize, String userAgent) {
        this.directory = directory;
        this.maxSize = maxSize;
        this.userAgent = userAgent;

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                // Disable SPDY, causes reproducible timeouts, only one download at the same time and other fun stuff
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();

        makeDir();
        calculateSize();
    }

    public void logStats() {
        Logger.i(TAG, "Cache size = " + size + "/" + maxSize);
        Logger.i(TAG, "downloaders.size() = " + downloaders.size());
        for (FileCacheDownloader downloader : downloaders) {
            Logger.i(TAG, "url = " + downloader.getUrl() + " cancelled = " + downloader.cancelled);
        }
    }

    public void clearCache() {
        Logger.d(TAG, "Clearing cache");
        for (FileCacheDownloader downloader : downloaders) {
            downloader.cancel();
        }

        if (directory.exists() && directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                if (!file.delete()) {
                    Logger.d(TAG, "Could not delete cache file while clearing cache " + file.getName());
                }
            }
        }
        calculateSize();
    }

    /**
     * Start downloading the file located at the url.<br>
     * If the file is in the cache then the callback is executed immediately and null is returned.<br>
     * Otherwise if the file is downloading or has not yet started downloading an {@link FileCacheDownloader} is returned.<br>
     * Only call this method on the UI thread.<br>
     *
     * @param urlString the url to download.
     * @param callback  callback to execute callbacks on.
     * @return null if in the cache, {@link FileCacheDownloader} otherwise.
     */
    public FileCacheDownloader downloadFile(final String urlString, final DownloadedCallback callback) {
        FileCacheDownloader downloader = null;
        for (FileCacheDownloader downloaderItem : downloaders) {
            if (downloaderItem.getUrl().equals(urlString)) {
                downloader = downloaderItem;
                break;
            }
        }

        if (downloader != null) {
            downloader.addCallback(callback);
            return downloader;
        } else {
            File file = get(urlString);
            if (file.exists()) {
                // TODO: setLastModified doesn't seem to work on Android...
                if (!file.setLastModified(Time.get())) {
//                    Logger.e(TAG, "Could not set last modified time on file");
                }
                callback.onProgress(0, 0, true);
                callback.onSuccess(file);
                return null;
            } else {
                FileCacheDownloader newDownloader = new FileCacheDownloader(this, urlString, file, userAgent);
                newDownloader.addCallback(callback);
                Future<?> future = executor.submit(newDownloader);
                newDownloader.setFuture(future);
                downloaders.add(newDownloader);
                return newDownloader;
            }
        }
    }

    public boolean exists(String key) {
        return get(key).exists();
    }

    public File get(String key) {
        makeDir();

        return new File(directory, Integer.toString(key.hashCode()));
    }

    private void put(File file) {
        size += file.length();

        trim();
    }

    private boolean delete(File file) {
        size -= file.length();

        return file.delete();
    }

    private void makeDir() {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Logger.e(TAG, "Unable to create file cache dir " + directory.getAbsolutePath());
            } else {
                calculateSize();
            }
        }
    }

    private void trim() {
        int tries = 0;
        while (size > maxSize && tries++ < TRIM_TRIES) {
            File[] files = directory.listFiles();
            if (files == null || files.length <= 1) {
                break;
            }
            long age = Long.MAX_VALUE;
            long last;
            File oldest = null;
            for (File file : files) {
                last = file.lastModified();
                if (last < age && last != 0L) {
                    age = last;
                    oldest = file;
                }
            }

            if (oldest == null) {
                Logger.e(TAG, "No files to trim");
                break;
            } else {
                Logger.d(TAG, "Deleting " + oldest.getAbsolutePath());
                if (!delete(oldest)) {
                    Logger.e(TAG, "Cannot delete cache file while trimming");
                    calculateSize();
                    break;
                }
            }

            calculateSize();
        }
    }

    private void calculateSize() {
        size = 0;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.length();
            }
        }
    }

    private void removeFromDownloaders(FileCacheDownloader downloader) {
        downloaders.remove(downloader);
    }

    public interface DownloadedCallback {
        void onProgress(long downloaded, long total, boolean done);

        void onSuccess(File file);

        void onFail(boolean notFound);
    }

    public static class FileCacheDownloader implements Runnable {
        private final FileCache fileCache;
        private final String url;
        private final File output;
        private final String userAgent;

        // Modify the callbacks list on the UI thread only!
        private final List<DownloadedCallback> callbacks = new ArrayList<>();

        private AtomicBoolean running = new AtomicBoolean(false);
        private AtomicBoolean userCancelled = new AtomicBoolean(false);

        private Closeable downloadInput;
        private Closeable downloadOutput;
        private Call call;
        private ResponseBody body;
        private boolean cancelled = false;
        private Future<?> future;

        private FileCacheDownloader(FileCache fileCache, String url, File output, String userAgent) {
            this.fileCache = fileCache;
            this.url = url;
            this.output = output;
            this.userAgent = userAgent;
        }

        public String getUrl() {
            return url;
        }

        public void addCallback(DownloadedCallback callback) {
            callbacks.add(callback);
        }

        /**
         * Cancel this download by interrupting the downloading thread. No callbacks will be executed.
         */
        public void cancel() {
            if (userCancelled.compareAndSet(false, true)) {
                future.cancel(true);
                // Did not start running yet, call cancelDueToCancellation manually to remove from downloaders list.
                if (!running.get()) {
                    cancelDueToCancellation();
                }
            }
        }

        public void run() {
            Logger.d(TAG, "Start load of " + url);
            try {
                running.set(true);
                execute();
            } catch (Exception e) {
                if (userCancelled.get()) {
                    cancelDueToCancellation();
                } else {
                    cancelDueToException(e);
                }
            } finally {
                cleanup();
            }
        }

        public Future<?> getFuture() {
            return future;
        }

        private void setFuture(Future<?> future) {
            this.future = future;
        }

        private void cancelDueToException(Exception e) {
            if (cancelled) return;
            cancelled = true;

            Logger.w(TAG, "IOException downloading url " + url, e);

            post(new Runnable() {
                @Override
                public void run() {
                    purgeOutput();
                    removeFromDownloadersList();
                    for (DownloadedCallback callback : callbacks) {
                        callback.onProgress(0, 0, true);
                        callback.onFail(false);
                    }
                }
            });
        }

        private void cancelDueToHttpError(final int code) {
            if (cancelled) return;
            cancelled = true;

            Logger.w(TAG, "Cancel " + url + " due to http error, code: " + code);

            post(new Runnable() {
                @Override
                public void run() {
                    purgeOutput();
                    removeFromDownloadersList();
                    for (DownloadedCallback callback : callbacks) {
                        callback.onProgress(0, 0, true);
                        callback.onFail(code == 404);
                    }
                }
            });
        }

        private void cancelDueToCancellation() {
            if (cancelled) return;
            cancelled = true;

            Logger.d(TAG, "Cancel " + url + " due to cancellation");

            post(new Runnable() {
                @Override
                public void run() {
                    purgeOutput();
                    removeFromDownloadersList();
                }
            });
        }

        private void success() {
            Logger.d(TAG, "Success downloading " + url);

            post(new Runnable() {
                @Override
                public void run() {
                    fileCache.put(output);
                    removeFromDownloadersList();
                    for (DownloadedCallback callback : callbacks) {
                        callback.onProgress(0, 0, true);
                        callback.onSuccess(output);
                    }
                }
            });
            call = null;
        }

        /**
         * Always called before any cancelDueTo method or success on the downloading thread.
         */
        private void cleanup() {
            Util.closeQuietly(downloadInput);
            Util.closeQuietly(downloadOutput);

            if (call != null) {
                call.cancel();
                call = null;
            }

            if (body != null) {
                Util.closeQuietly(body);
                body = null;
            }
        }

        private void removeFromDownloadersList() {
            fileCache.removeFromDownloaders(this);
        }

        private void purgeOutput() {
            if (output.exists()) {
                if (!output.delete()) {
                    Logger.w(TAG, "Could not delete the file in purgeOutput");
                }
            }
        }

        private void postProgress(final long downloaded, final long total, final boolean done) {
            post(new Runnable() {
                @Override
                public void run() {
                    for (DownloadedCallback callback : callbacks) {
                        callback.onProgress(downloaded, total, done);
                    }
                }
            });
        }

        private void post(Runnable runnable) {
            AndroidUtils.runOnUiThread(runnable);
        }

        private void execute() throws Exception {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build();

            call = fileCache.httpClient.newBuilder()
                    .proxy(ChanSettings.getProxy())
                    .build()
                    .newCall(request);

            Response response = call.execute();
            if (!response.isSuccessful()) {
                cancelDueToHttpError(response.code());
                return;
            }

            body = response.body();
            long contentLength = body.contentLength();
            BufferedSource source = body.source();
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(output));

            downloadInput = source;
            downloadOutput = outputStream;

            Logger.d(TAG, "Got input stream for " + url);

            int read;
            long total = 0;
            long totalLast = 0;
            byte[] buffer = new byte[4096];
            while ((read = source.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                total += read;

                if (total >= totalLast + 16384) {
                    totalLast = total;
                    postProgress(total, contentLength <= 0 ? total : contentLength, false);
                }
            }

            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();

            success();
        }
    }
}
