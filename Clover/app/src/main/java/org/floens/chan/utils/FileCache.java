package org.floens.chan.utils;

import android.content.Context;
import android.util.Log;

import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.ProgressCallback;
import com.koushikdutta.ion.Response;

import org.floens.chan.ChanApplication;

import java.io.File;
import java.util.concurrent.CancellationException;

public class FileCache {
    private static final String TAG = "FileCache";

    private final File directory;
    private final long maxSize;

    private long size;

    public FileCache(File directory, long maxSize) {
        this.directory = directory;
        this.maxSize = maxSize;

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

    public Future<Response<File>> downloadFile(Context context, String url, final DownloadedCallback callback) {
        File file = get(url);
        if (file.exists()) {
            file.setLastModified(Time.get());
            callback.onProgress(0, 0, true);
            callback.onSuccess(file);
            return null;
        } else {
            return ChanApplication.getIon()
                    .load(url)
                    .progress(new ProgressCallback() {
                        @Override
                        public void onProgress(final long downloaded, final long total) {
                            AndroidUtils.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onProgress(downloaded, total, false);
                                }
                            });
                        }
                    })
                    .write(file)
                    .withResponse()
                    .setCallback(new FutureCallback<Response<File>>() {
                        @Override
                        public void onCompleted(Exception e, Response<File> result) {
                            callback.onProgress(0, 0, true);

                            if (result != null && result.getHeaders() != null && result.getHeaders().code() / 100 != 2) {
                                if (result.getResult() != null) {
                                    delete(result.getResult());
                                }
                                callback.onFail(true);
                                return;
                            }

                            if (e != null && !(e instanceof CancellationException)) {
                                e.printStackTrace();
                                if (result != null && result.getResult() != null) {
                                    delete(result.getResult());
                                }
                                callback.onFail(false);
                                return;
                            }

                            if (result != null && result.getResult() != null) {
                                put(result.getResult());
                                callback.onSuccess(result.getResult());
                            }
                        }
                    });
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
}
