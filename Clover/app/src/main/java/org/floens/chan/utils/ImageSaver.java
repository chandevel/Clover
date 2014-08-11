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

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.widget.Toast;

import com.koushikdutta.async.future.Future;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImageSaver {
    private static final String TAG = "ImageSaver";
    private static final int NOTIFICATION_ID = 1;

    private static ImageSaver instance;

    public static ImageSaver getInstance() {
        return instance;
    }

    static {
        instance = new ImageSaver();
    }

    private BroadcastReceiver receiver;

    public void saveAll(final Context context, String folderName, final List<DownloadPair> list) {
        folderName = filterName(folderName);

        final File subFolder = findUnused(new File(ChanPreferences.getImageSaveDirectory() + File.separator + folderName), true);

        String text = context.getString(R.string.download_confirm, Integer.toString(list.size()), subFolder.getAbsolutePath());

        new AlertDialog.Builder(context).setMessage(text).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listDownload(context, subFolder, list);
                    }
                }).show();
    }

    public void saveImage(final Context context, String imageUrl, final String name, final String extension, final boolean share) {
        Future<?> ionRequest = ChanApplication.getFileCache().downloadFile(context, imageUrl, new FileCache.DownloadedCallback() {
            @Override
            @SuppressWarnings("deprecation")
            public void onProgress(long downloaded, long total, boolean done) {
            }

            @Override
            public void onSuccess(final File source) {
                onFileDownloaded(context, name, extension, source, share);
            }

            @Override
            public void onFail(boolean notFound) {
                Toast.makeText(context, R.string.image_open_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onFileDownloaded(final Context context, final String name, final String extension, final File downloaded, boolean share) {
        if (share) {
            scanFile(context, downloaded, true);
        } else {
            /*if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                Toast.makeText(context, R.string.image_save_not_mounted, Toast.LENGTH_LONG).show();
                return;
            }*/

            new Thread(new Runnable() {
                @Override
                public void run() {
                    File saveDir = ChanPreferences.getImageSaveDirectory();

                    if (!saveDir.isDirectory() && !saveDir.mkdirs()) {
                        showToast(context, context.getString(R.string.image_save_directory_error));
                        return;
                    }

                    String fileName = filterName(name + "." + extension);
                    File destination = findUnused(new File(saveDir, fileName), false);

                    final boolean success = storeImage(downloaded, destination);
                    if (success) {
                        scanFile(context, destination, false);
                        showToast(context, context.getString(R.string.image_save_succeeded) + " " + destination.getAbsolutePath());
                    } else {
                        showToast(context, context.getString(R.string.image_save_failed));
                    }
                }
            }).start();
        }
    }

    private void showToast(final Context context, final String message) {
        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void listDownload(Context context, File subFolder, final List<DownloadPair> list) {
        final DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (!subFolder.isDirectory() && !subFolder.mkdirs()) {
            Toast.makeText(context, R.string.image_save_directory_error, Toast.LENGTH_LONG).show();
            return;
        }

        final List<Pair> files = new ArrayList<>(list.size());
        for (DownloadPair uri : list) {
            String name = filterName(uri.imageName);
            // Finding unused filenames won't actually work, because the file doesn't get
            // saved right away. The download manager will also prevent if there's a name collision.
            File destination = findUnused(new File(subFolder, name), false);

            Pair p = new Pair();
            p.uri = Uri.parse(uri.imageUrl);
            p.file = destination;
            files.add(p);
        }

        final AtomicBoolean stopped = new AtomicBoolean(false);
        final List<Long> ids = new ArrayList<>();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Pair pair : files) {
                    if (stopped.get()) {
                        break;
                    }

                    DownloadManager.Request request;
                    try {
                        request = new DownloadManager.Request(pair.uri);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }

                    request.setDestinationUri(Uri.fromFile(pair.file));
                    request.setVisibleInDownloadsUi(false);
                    request.allowScanningByMediaScanner();

                    synchronized (stopped) {
                        if (stopped.get()) {
                            break;
                        }
                        ids.add(dm.enqueue(request));
                    }
                }
            }
        }).start();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.getAction())) {
                    synchronized (stopped) {
                        stopped.set(true);

                        for (Long id : ids) {
                            dm.remove(id);
                        }
                    }
                } else if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    if (receiver != null) {
                        context.unregisterReceiver(receiver);
                        receiver = null;
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);
        filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

        context.registerReceiver(receiver, filter);
    }

    private String filterName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.]", "_");
    }

    private File findUnused(File start, boolean isDir) {
        String base;
        String extension;

        if (isDir) {
            base = start.getAbsolutePath();
            extension = null;
        } else {
            String[] splitted = start.getAbsolutePath().split("\\.(?=[^\\.]+$)");
            if (splitted.length == 2) {
                base = splitted[0];
                extension = "." + splitted[1];
            } else {
                base = splitted[0];
                extension = ".";
            }
        }

        File test;
        if (isDir) {
            test = new File(base);
        } else {
            test = new File(base + extension);
        }
        int index = 0;
        int tries = 0;
        while (test.exists() && tries++ < 100) {
            if (isDir) {
                test = new File(base + "_" + index);
            } else {
                test = new File(base + "_" + index + extension);
            }
            index++;
        }

        return test;
    }

    private boolean storeImage(final File source, final File destination) {
        boolean res = true;

        try {
            IOUtils.copy(new FileInputStream(source), new FileOutputStream(destination));
        } catch (IOException e) {
            res = false;
        }

        return res;
    }

    private void scanFile(final Context context, final File file, final boolean shareAfterwards) {
        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String unused, final Uri uri) {
                        Utils.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Logger.i(TAG, "Media scan succeeded: " + uri);

                                if (shareAfterwards) {
                                    Intent intent = new Intent(Intent.ACTION_SEND);
                                    intent.setType("image/*");
                                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)));
                                }
                            }
                        });
                    }
                }
        );
    }

    public static class DownloadPair {
        public String imageUrl;
        public String imageName;

        public DownloadPair(String uri, String name) {
            this.imageUrl = uri;
            this.imageName = name;
        }
    }

    private static class Pair {
        public Uri uri;
        public File file;
    }
}
