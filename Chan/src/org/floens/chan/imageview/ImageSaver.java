package org.floens.chan.imageview;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.net.ByteArrayRequest;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;

public class ImageSaver {
    public static void save(final Context context, String imageUrl, final String name, final String extension) {
        ByteArrayRequest request = new ByteArrayRequest(imageUrl, new Response.Listener<byte[]>() {
            @Override
            public void onResponse(byte[] array) {
                storeImage(context, array, name, extension);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, R.string.image_preview_failed, Toast.LENGTH_LONG).show();
            }
        });
       
        ChanApplication.getVolleyRequestQueue().add(request);
    }
    
    private static void storeImage(final Context context, byte[] data, String name, String extension) {
        String errorReason = null;
        
        try {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                errorReason = context.getString(R.string.image_save_not_mounted);
                throw new IOException(errorReason);
            }
            
            File path = new File(Environment.getExternalStorageDirectory() + File.separator + context.getString(R.string.image_save_folder_name));
            
            if (!path.exists()) {
                if (!path.mkdirs()) {
                    errorReason = context.getString(R.string.image_save_directory_error);
                    throw new IOException(errorReason);
                }
            }
            
            File file = new File(path, name + "." + extension);
            int nextFileNameNumber = 0;
            String newName;
            while(file.exists()) {
                newName = name + "_" + Integer.toString(nextFileNameNumber) + "." + extension;
                file = new File(path, newName);
                nextFileNameNumber++;
            }
            
            Log.i("Chan", "Saving image to: " + file.getPath()); 
            
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(data);
            outputStream.close();
            
            MediaScannerConnection.scanFile(context, new String[] { file.toString() }, null, new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted(String path, Uri uri) {
                    Log.i("Chan", "Media scan succeeded: " + uri);
                }
            });
            
            String message = context.getResources().getString(R.string.image_save_succeeded) + " " + file.getName();
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            
            if (errorReason == null) errorReason = context.getString(R.string.image_save_failed);
            
            Toast.makeText(context, errorReason, Toast.LENGTH_LONG).show();
        }
    }
}





