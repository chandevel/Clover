package org.floens.chan.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.net.ByteArrayRequest;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;

public class ImageSaver {
    private static final String TAG = "ImageSaver";

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
