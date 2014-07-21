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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.net.ByteArrayRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageSaver {
    private static final String TAG = "ImageSaver";

    public static void saveAll(final Context context, String folderName, final List<DownloadPair> list) {
        final DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        folderName = filterName(folderName);

        final File subFolder = findUnused(new File(ChanPreferences.getImageSaveDirectory() + File.separator + folderName), true);

        String text = context.getString(R.string.download_confirm)
                .replace("COUNT", Integer.toString(list.size()))
                .replace("FOLDER", subFolder.getAbsolutePath());

        new AlertDialog.Builder(context).setMessage(text).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
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

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                for (Pair pair : files) {
                                    DownloadManager.Request request;
                                    try {
                                        request = new DownloadManager.Request(pair.uri);
                                    } catch (IllegalArgumentException e) {
                                        continue;
                                    }

                                    request.setDestinationUri(Uri.fromFile(pair.file));
                                    request.setVisibleInDownloadsUi(false);
                                    request.allowScanningByMediaScanner();

                                    dm.enqueue(request);
                                }
                            }
                        }).start();
                    }
                }).show();
    }

    public static void saveImage(final Context context, String imageUrl, String name, final String extension, final boolean share) {
        File saveDir = ChanPreferences.getImageSaveDirectory();

        if (!saveDir.isDirectory() && !saveDir.mkdirs()) {
            Toast.makeText(context, R.string.image_save_directory_error, Toast.LENGTH_LONG).show();
            return;
        }

        String fileName = filterName(name + "." + extension);
        final File file = findUnused(new File(saveDir, fileName), false);

        Logger.test(file.getAbsolutePath());

        ChanApplication.getVolleyRequestQueue().add(new ByteArrayRequest(imageUrl, new Response.Listener<byte[]>() {
            @Override
            public void onResponse(byte[] data) {
                storeImage(context, data, file, share);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, R.string.image_open_failed, Toast.LENGTH_LONG).show();
            }
        }));
    }

    private static String filterName(String name) {
        return name.replaceAll("[^\\w.]", "");
    }

    private static File findUnused(File start, boolean isDir) {
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

    private static void storeImage(final Context context, final byte[] data, final File destination, final boolean share) {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(context, R.string.image_save_not_mounted, Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean res = saveByteArray(destination, data);

                Utils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!res) {
                            Toast.makeText(context, R.string.image_save_failed, Toast.LENGTH_LONG).show();
                        } else {
                            scanFile(context, destination.getAbsolutePath(), share);
                        }
                    }
                });
            }
        }).start();
    }

    private static boolean saveByteArray(File destination, byte[] source) {
        boolean res = false;
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(destination);
            outputStream.write(source);
            outputStream.close();
            res = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                }
            }
        }
        return res;
    }

    private static void scanFile(final Context context, final String path, final boolean shareAfterwards) {
        Logger.test("Scan: " + path);

        MediaScannerConnection.scanFile(context, new String[]{path}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String unused, final Uri uri) {
                        Utils.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Logger.i(TAG, "File saved & media scan succeeded: " + uri);

                                if (shareAfterwards) {
                                    Intent intent = new Intent(Intent.ACTION_SEND);
                                    intent.setType("image/*");
                                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)));
                                } else {
                                    String message = context.getResources().getString(R.string.image_save_succeeded) + " " + path;
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
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
