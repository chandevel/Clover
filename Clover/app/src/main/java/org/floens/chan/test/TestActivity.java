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
package org.floens.chan.test;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.android.volley.VolleyError;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.utils.FileCache;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.ThemeHelper;

import java.io.File;

// Poor mans unit testing.
// Move to proper unit testing when the gradle plugin fully supports it.
public class TestActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "FileCacheTest";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Button clearCache;
    private Button stats;
    private Button simpleTest;
    private Button cacheTest;
    private Button timeoutTest;

    private FileCache fileCache;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeHelper.getInstance().reloadPostViewColors(this);

        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        clearCache = new Button(this);
        clearCache.setText("Clear cache");
        clearCache.setOnClickListener(this);
        linearLayout.addView(clearCache);

        stats = new Button(this);
        stats.setText("Stats");
        stats.setOnClickListener(this);
        linearLayout.addView(stats);

        simpleTest = new Button(this);
        simpleTest.setText("Test download and cancel");
        simpleTest.setOnClickListener(this);
        linearLayout.addView(simpleTest);

        cacheTest = new Button(this);
        cacheTest.setText("Test cache size");
        cacheTest.setOnClickListener(this);
        linearLayout.addView(cacheTest);

        timeoutTest = new Button(this);
        timeoutTest.setText("Test multiple parallel");
        timeoutTest.setOnClickListener(this);
        linearLayout.addView(timeoutTest);

        setContentView(linearLayout);

        File cacheDir = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
        File fileCacheDir = new File(cacheDir, "filecache");
        fileCache = new FileCache(fileCacheDir, 50 * 1024 * 1024, ChanApplication.getInstance().getUserAgent());
    }

    @Override
    public void onClick(View v) {
        if (v == clearCache) {
            clearCache();
        } else if (v == stats) {
            stats();
        } else if (v == simpleTest) {
            testDownloadAndCancel();
        } else if (v == cacheTest) {
            testCache();
        } else if (v == timeoutTest) {
            testTimeout();
        }
    }

    public void clearCache() {
        fileCache.clearCache();
    }

    public void stats() {
        fileCache.logStats();
    }

    public void testDownloadAndCancel() {
        // 1.9MB file of the clover Logger.i(TAG,
        final String testImage = "http://a.pomf.se/ndbolc.png";
        final File cacheFile = fileCache.get(testImage);

        Logger.i(TAG, "Downloading " + testImage);
        final FileCache.FileCacheDownloader downloader = fileCache.downloadFile(testImage, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                Logger.i(TAG, "onProgress " + downloaded + "/" + total + " " + done);
            }

            @Override
            public void onSuccess(File file) {
                Logger.i(TAG, "onSuccess " + file.exists());
            }

            @Override
            public void onFail(boolean notFound) {
                Logger.i(TAG, "onFail Cachefile exists() = " + cacheFile.exists());
            }
        });

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fileCache.downloadFile(testImage, new FileCache.DownloadedCallback() {
                    @Override
                    public void onProgress(long downloaded, long total, boolean done) {
                        Logger.i(TAG, "2nd progress " + downloaded + "/" + total);
                    }

                    @Override
                    public void onSuccess(File file) {
                        Logger.i(TAG, "2nd onSuccess " + file.exists());
                    }

                    @Override
                    public void onFail(boolean notFound) {
                        Logger.i(TAG, "2nd onFail Cachefile exists() = " + cacheFile.exists());
                    }
                });
            }
        }, 200);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Logger.i(TAG, "Cancelling download!");
                downloader.cancel();
            }
        }, 500);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Logger.i(TAG, "File exists() = " + cacheFile.exists());
            }
        }, 600);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final File cache404File = fileCache.get(testImage + "404");
                fileCache.downloadFile(testImage + "404", new FileCache.DownloadedCallback() {
                    @Override
                    public void onProgress(long downloaded, long total, boolean done) {
                        Logger.i(TAG, "404 progress " + downloaded + "/" + total + " " + done);
                    }

                    @Override
                    public void onSuccess(File file) {
                        Logger.i(TAG, "404 onSuccess " + file.exists());
                    }

                    @Override
                    public void onFail(boolean notFound) {
                        Logger.i(TAG, "404 onFail " + cache404File.exists());
                    }
                });
            }
        }, 1000);
    }

    private void testCache() {
        Loadable loadable = new Loadable("g");
        loadable.mode = Loadable.Mode.CATALOG;
        Loader loader = new Loader(loadable);
        loader.addListener(new Loader.LoaderListener() {
            @Override
            public void onData(ChanThread result) {
                for (Post post : result.posts) {
                    if (post.hasImage) {
                        final String imageUrl = post.imageUrl;
                        fileCache.downloadFile(imageUrl, new FileCache.DownloadedCallback() {
                            @Override
                            public void onProgress(long downloaded, long total, boolean done) {
                                Logger.i(TAG, "Progress for " + imageUrl + " " + downloaded + "/" + total + " " + done);
                            }

                            @Override
                            public void onSuccess(File file) {
                                Logger.i(TAG, "onSuccess for " + imageUrl + " exists() = " + file.exists());
                            }

                            @Override
                            public void onFail(boolean notFound) {
                                Logger.i(TAG, "onFail for " + imageUrl);
                            }
                        });
                    }
                }
            }

            @Override
            public void onError(VolleyError error) {

            }
        });
        loader.requestData();
    }

    private void testTimeout() {
        testTimeoutInner("https://i.4cdn.org/hr/1429923649068.jpg", fileCache, 0);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                testTimeoutInner("https://i.4cdn.org/hr/1430058524427.jpg", fileCache, 0);
            }
        }, 200);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                testTimeoutInner("https://i.4cdn.org/hr/1430058627352.jpg", fileCache, 0);
            }
        }, 400);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                testTimeoutInner("https://i.4cdn.org/hr/1430058580015.jpg", fileCache, 0);
            }
        }, 600);
    }

    private void testTimeoutInner(final String url, final FileCache fileCache, final int tries) {
        final File cacheFile = fileCache.get(url);
        Logger.i(TAG, "Downloading " + url + " try " + tries);
        final FileCache.FileCacheDownloader downloader = fileCache.downloadFile(url, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                Logger.i(TAG, "onProgress " + url + " " + downloaded + "/" + total);
            }

            @Override
            public void onSuccess(File file) {
                Logger.i(TAG, "onSuccess " + file.exists());
            }

            @Override
            public void onFail(boolean notFound) {
                Logger.i(TAG, "onFail Cachefile exists() = " + cacheFile.exists());
            }
        });

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (downloader == null) {
                    Logger.i(TAG, "Downloader null, cannot cancel");
                } else {
                    downloader.cancel();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (tries < 10) {
                                testTimeoutInner(url, fileCache, tries + 1);
                            } else {
                                fileCache.logStats();
                            }
                        }
                    }, 500);
                }
            }
        }, 1000);
    }
}
