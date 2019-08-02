package com.github.adamantcheese.chan.core.image;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

/**
 * Container object for all of the data surrounding an image request.
 */
public class ImageContainer {
    /**
     * The most relevant bitmap for the container. If the image was in cache, the
     * Holder to use for the final bitmap (the one that pairs to the requested URL).
     */
    private Bitmap mBitmap;

    private final ImageListener mListener;

    /**
     * The cache key that was associated with the request
     */
    private final String mCacheKey;

    /**
     * The request URL that was specified
     */
    private String mRequestUrl;

    /**
     * Constructs a BitmapContainer object.
     *
     * @param bitmap     The final bitmap (if it exists).
     * @param requestUrl The requested URL for this container.
     * @param cacheKey   The cache key that identifies the requested URL for this container.
     */
    public ImageContainer(Bitmap bitmap, String requestUrl,
                          String cacheKey, ImageListener listener) {
        mBitmap = bitmap;
        mRequestUrl = requestUrl;
        mCacheKey = cacheKey;
        mListener = listener;
    }

    public ImageContainer() {
        mBitmap = null;
        mRequestUrl = null;
        mCacheKey = null;
        mListener = null;
    }

    public String getCacheKey() {
        return mCacheKey;
    }

    @Nullable
    public ImageListener getListener() {
        return mListener;
    }

    /**
     * Returns the bitmap associated with the request URL if it has been loaded, null otherwise.
     */
    public Bitmap getBitmap() {
        return mBitmap;
    }

    /**
     * Returns the requested URL for this container.
     */
    public String getRequestUrl() {
        return mRequestUrl;
    }

    public void setBitmap(Bitmap mBitmap) {
        this.mBitmap = mBitmap;
    }

    public void setRequestUrl(String mRequestUrl) {
        this.mRequestUrl = mRequestUrl;
    }
}