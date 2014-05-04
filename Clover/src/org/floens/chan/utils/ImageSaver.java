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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.net.ByteArrayRequest;

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

public class ImageSaver {
    private static final String TAG = "ImageSaver";

    public static void saveAll(Context context, String folderName, final List<Uri> list) {
        final DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        String folderPath = ChanPreferences.getImageSaveDirectory() + File.separator + folderName;
        File folder = Environment.getExternalStoragePublicDirectory(folderPath);
        int nextFileNameNumber = 0;
        while (folder.exists() || folder.isFile()) {
            folderPath = ChanPreferences.getImageSaveDirectory() + File.separator + folderName + "_"
                    + Integer.toString(nextFileNameNumber++);
            folder = Environment.getExternalStoragePublicDirectory(folderPath);
        }

        final String finalFolderPath = folderPath;
        String text = context.getString(R.string.download_confirm).replace("COUNT", Integer.toString(list.size()))
                .replace("FOLDER", folderPath);
        new AlertDialog.Builder(context).setMessage(text).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                for (Uri uri : list) {
                                    DownloadManager.Request request = null;
                                    try {
                                        request = new DownloadManager.Request(uri);
                                    } catch (IllegalArgumentException e) {
                                        continue;
                                    }

                                    request.setDestinationInExternalPublicDir(finalFolderPath, uri.getLastPathSegment());
                                    request.setVisibleInDownloadsUi(false);
                                    request.allowScanningByMediaScanner();

                                    dm.enqueue(request);
                                }
                            }
                        }).start();
                    }
                }).show();
    }

    public static void save(final Context context, String imageUrl, final String name, final String extension,
            final boolean share) {
        ChanApplication.getVolleyRequestQueue().add(new ByteArrayRequest(imageUrl, new Response.Listener<byte[]>() {
            @Override
            public void onResponse(byte[] data) {
                storeImage(context, data, name, extension, new SaveCallback() {
                    @Override
                    public void onUri(String name, Uri uri) {
                        if (!share) {
                            String message = context.getResources().getString(R.string.image_save_succeeded) + " "
                                    + name;
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                        } else {
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            intent.setType("image/*");
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            context.startActivity(Intent.createChooser(intent, "WAT"));
                        }
                    }
                });
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, R.string.image_open_failed, Toast.LENGTH_LONG).show();
            }
        }));
    }

    private static void storeImage(final Context context, byte[] data, String name, String extension,
            final SaveCallback callback) {
        String errorReason = null;

        try {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                errorReason = context.getString(R.string.image_save_not_mounted);
                throw new IOException(errorReason);
            }

            File path = new File(Environment.getExternalStorageDirectory() + File.separator
                    + ChanPreferences.getImageSaveDirectory());

            if (!path.exists()) {
                if (!path.mkdirs()) {
                    errorReason = context.getString(R.string.image_save_directory_error);
                    throw new IOException(errorReason);
                }
            }

            final File savedFile = saveFile(path, data, name, extension);
            if (savedFile == null) {
                errorReason = context.getString(R.string.image_save_failed);
                throw new IOException(errorReason);
            }

            MediaScannerConnection.scanFile(context, new String[] { savedFile.toString() }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, final Uri uri) {
                            Utils.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Logger.i(TAG, "Media scan succeeded: " + uri);
                                    callback.onUri(savedFile.toString(), uri);
                                }
                            });
                        }
                    });

        } catch (IOException e) {
            e.printStackTrace();

            if (errorReason == null)
                errorReason = context.getString(R.string.image_save_failed);

            Toast.makeText(context, errorReason, Toast.LENGTH_LONG).show();
        }
    }

    private static File saveFile(File path, byte[] source, String name, String extension) {
        File destination = new File(path, name + "." + extension);
        int nextFileNameNumber = 0;
        String newName;
        while (destination.exists()) {
            newName = name + "_" + Integer.toString(nextFileNameNumber++) + "." + extension;
            destination = new File(path, newName);
        }

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(destination);
            outputStream.write(source);
            outputStream.close();
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

        Logger.i(TAG, "Saved image to: " + destination.getPath());

        return destination;
    }

    private static interface SaveCallback {
        public void onUri(String name, Uri uri);
    }
}
