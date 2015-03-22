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
package org.floens.chan.utils;

import android.util.Log;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.Util;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okio.BufferedSource;

public class FileCache {
    private static final String TAG = "FileCache";

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    private OkHttpClient httpClient;

    private final File directory;
    private final long maxSize;

    private long size;

    public FileCache(File directory, long maxSize) {
        this.directory = directory;
        this.maxSize = maxSize;

        httpClient = new OkHttpClient();

        makeDir();
        calculateSize();
    }

    public File get(String key) {
        makeDir();

        return new File(directory, Integer.toString(key.hashCode()));
    }

    public void put(File file) {
        size += file.length();

        trim();
    }

    public boolean delete(File file) {
        size -= file.length();

        return file.delete();
    }

    public Future<?> downloadFile(final String urlString, final DownloadedCallback callback) {
        File file = get(urlString);
        if (file.exists()) {
            file.setLastModified(Time.get());
            callback.onProgress(0, 0, true);
            callback.onSuccess(file);
            return null;
        } else {
            FileCacheDownloader downloader = new FileCacheDownloader(this, urlString, file, callback);
            return executor.submit(downloader);
        }
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
        while (size > maxSize && tries++ < 10) {
            File[] files = directory.listFiles();
            if (files == null) {
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
                Log.e(TAG, "No files to trim");
                break;
            } else {
                Log.d(TAG, "Deleting " + oldest.getAbsolutePath());
                if (!delete(oldest)) {
                    Log.e(TAG, "Cannot delete cache file");
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

    public interface DownloadedCallback {
        public void onProgress(long downloaded, long total, boolean done);

        public void onSuccess(File file);

        public void onFail(boolean notFound);
    }

    private static class FileCacheDownloader implements Runnable {
        private final FileCache fileCache;
        private final String url;
        private final File output;
        private final DownloadedCallback callback;
        private boolean cancelled = false;

        private Closeable downloadInput;
        private Closeable downloadOutput;
        private Call call;
        private ResponseBody body;

        public FileCacheDownloader(FileCache fileCache, String url, File output, DownloadedCallback callback) {
            this.fileCache = fileCache;
            this.url = url;
            this.output = output;
            this.callback = callback;
        }

        public void run() {
            try {
                execute();
            } catch (InterruptedIOException | InterruptedException e) {
                cancelDueToCancellation(e);
            } catch (Exception e) {
                cancelDueToException(e);
            } finally {
                finish();
            }
        }

        private void cancelDueToException(Exception e) {
            if (cancelled) return;
            cancelled = true;

            Log.w(TAG, "IOException downloading file", e);

            purgeOutput();

            post(new Runnable() {
                @Override
                public void run() {
                    callback.onProgress(0, 0, true);
                    callback.onFail(false);
                }
            });
        }

        private void cancelDueToHttpError(final int code) {
            if (cancelled) return;
            cancelled = true;

            Log.w(TAG, "Cancel due to http error, code: " + code);

            purgeOutput();

            post(new Runnable() {
                @Override
                public void run() {
                    callback.onProgress(0, 0, true);
                    callback.onFail(code == 404);
                }
            });
        }

        private void cancelDueToCancellation(Exception e) {
            if (cancelled) return;
            cancelled = true;

            Log.d(TAG, "Cancel due to cancellation");

            purgeOutput();

            // No callback
        }

        private void success() {
            fileCache.put(output);

            post(new Runnable() {
                @Override
                public void run() {
                    callback.onProgress(0, 0, true);
                    callback.onSuccess(output);
                }
            });
            call = null;
        }

        private void finish() {
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

        private void purgeOutput() {
            if (output.exists()) {
                if (!output.delete()) {
                    Log.w(TAG, "Could not delete the file in purgeOutput");
                }
            }
        }

        private long progressDownloaded;
        private long progressTotal;
        private boolean progressDone;
        private final Runnable progressRunnable = new Runnable() {
            @Override
            public void run() {
                callback.onProgress(progressDownloaded, progressTotal, progressDone);
            }
        };

        private void progress(long downloaded, long total, boolean done) {
            progressDownloaded = downloaded;
            progressTotal = total;
            progressDone = done;
            post(progressRunnable);
        }

        private void post(Runnable runnable) {
            AndroidUtils.runOnUiThread(runnable);
        }

        private void execute() throws Exception {
            Request request = new Request.Builder().url(url).build();

            call = fileCache.httpClient.newCall(request);
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

            int read;
            long total = 0;
            long totalLast = 0;
            byte[] buffer = new byte[4096];
            while ((read = source.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                total += read;

                if (total >= totalLast + 16384) {
                    totalLast = total;
                    progress(total, contentLength, false);
                }
            }

            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();

            success();
        }
    }
}
