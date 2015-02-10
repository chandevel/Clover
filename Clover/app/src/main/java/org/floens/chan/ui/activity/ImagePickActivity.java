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
package org.floens.chan.ui.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.widget.Toast;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImagePickActivity extends Activity {
    private static final int IMAGE_RESULT = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        try {
            startActivityForResult(intent, IMAGE_RESULT);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.file_open_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        finish();

        if (requestCode == IMAGE_RESULT && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                final Uri uri = data.getData();

                String name = "file";

                Cursor returnCursor = getContentResolver().query(uri, null, null, null, null);
                if (returnCursor != null) {
                    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    returnCursor.moveToFirst();
                    if (nameIndex > -1) {
                        name = returnCursor.getString(nameIndex);
                    }

                    returnCursor.close();
                }

                ChanApplication.getReplyManager()._onPickedFileLoading();

                final String finalName = name;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final File cacheFile = new File(getCacheDir() + File.separator + "picked_file");

                            if (cacheFile.exists()) {
                                cacheFile.delete();
                            }

                            ParcelFileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");

                            FileInputStream is = new FileInputStream(fileDescriptor.getFileDescriptor());
                            FileOutputStream os = new FileOutputStream(cacheFile);
                            IOUtils.copy(is, os);
                            IOUtils.closeQuietly(is);
                            IOUtils.closeQuietly(os);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ChanApplication.getReplyManager()._onPickedFile(finalName, cacheFile);
                                }

                            });
                        } catch (IOException | SecurityException e) {
                            e.printStackTrace();

                            AndroidUtils.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ChanApplication.getReplyManager()._onPickedFile("", null);
                                    Toast.makeText(ImagePickActivity.this, R.string.file_open_failed, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        }
    }
}
