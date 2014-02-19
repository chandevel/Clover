package org.floens.chan.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.floens.chan.R;
import org.floens.chan.manager.ReplyManager;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class ImagePickActivity extends Activity {
    private static final int IMAGE_RESULT = 1;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        
        try {
            startActivityForResult(intent, IMAGE_RESULT);            
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.image_open_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        finish();
        
        if (requestCode == IMAGE_RESULT && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                final Uri uri = data.getData();
                
                ReplyManager.getInstance()._onPickedFileLoading();
                
                // Async load the stream into "pickedFileCache", an file in the cache root
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            InputStream is = getContentResolver().openInputStream(uri);
                            final File cacheFile = new File(getCacheDir() + File.separator + "pickedFileCache");
                            
                            if (cacheFile.exists()) {
                                cacheFile.delete();
                            }
                            
                            FileOutputStream fis = new FileOutputStream(cacheFile);
                            
                            byte[] buffer = new byte[1024];
                            int length = 0;
                            while((length = is.read(buffer)) != -1) {
                                fis.write(buffer, 0, length);
                            }
                            
                            is.close();
                            fis.close();
                            
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ReplyManager.getInstance()._onPickedFile(cacheFile);
                                }
                                
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        }
    }
}





