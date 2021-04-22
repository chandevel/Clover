package com.davemorrissey.labs.subscaleview.decoder;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.davemorrissey.labs.subscaleview.ImageSource;

/**
 * Interface for image decoding classes, allowing the default {@link android.graphics.BitmapFactory}
 * based on the Skia library to be replaced with a custom class.
 */
public interface ImageDecoder {

    /**
     * Decode an image. The URI of the image source can be in one of the following formats:
     * <br>
     * File: <code>file:///scard/picture.jpg</code>
     * <br>
     * Asset: <code>file:///android_asset/picture.png</code>
     * <br>
     * Resource: <code>android.resource://com.example.app/drawable/picture</code>
     *
     * @param context Application context
     * @param source  Image source of the image
     * @return the decoded bitmap
     *
     * @throws Exception if decoding fails.
     */
    @NonNull
    Bitmap decode(Context context, @NonNull ImageSource source)
            throws Exception;
}
