package com.davemorrissey.labs.subscaleview;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import okio.Buffer;

/**
 * Helper class used to set the source and additional attributes from a variety of sources. Supports
 * use of a bitmap, asset, resource, external file or any other URI.
 * <p>
 * When you are using a preview image, you must set the dimensions of the full size image on the
 * ImageSource object for the full size image using the {@link #dimensions(int, int)} method.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class ImageSource {

    public static final String FILE_PREFIX = "file://";
    public static final String ASSET_PREFIX = FILE_PREFIX + "/android_asset/";
    public static final String RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://";

    private final Uri uri;
    private final Bitmap bitmap;
    private final Buffer buffer;

    private boolean tile;
    private int sWidth;
    private int sHeight;
    private Rect sRegion;
    private boolean cached;

    private ImageSource(Bitmap bitmap, boolean cached) {
        this.bitmap = bitmap;
        this.uri = null;
        this.buffer = null;
        this.tile = false;
        this.sWidth = bitmap.getWidth();
        this.sHeight = bitmap.getHeight();
        this.cached = cached;
    }

    private ImageSource(@NonNull Uri uri) {
        // #114 If file doesn't exist, attempt to url decode the URI and try again
        String uriString = uri.toString();
        if (uriString.startsWith(FILE_PREFIX)) {
            File uriFile = new File(uriString.substring(FILE_PREFIX.length() - 1));
            if (!uriFile.exists()) {
                try {
                    uri = Uri.parse(URLDecoder.decode(uriString, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    // Fallback to encoded URI. This exception is not expected.
                }
            }
        }
        this.bitmap = null;
        this.uri = uri;
        this.buffer = null;
        this.tile = true;
    }

    private ImageSource(Context context, int resource) {
        this.bitmap = null;
        this.uri =
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + resource);
        this.buffer = null;
        this.tile = true;
    }

    private ImageSource(Buffer buffer) {
        this.bitmap = null;
        this.uri = null;
        this.buffer = buffer;
        this.tile = true;
    }

    /**
     * Create an instance from a resource. The correct resource for the device screen resolution will be used.
     *
     * @param resId resource ID.
     * @return an {@link ImageSource} instance.
     */
    @NonNull
    public static ImageSource resource(Context context, int resId) {
        return new ImageSource(context, resId);
    }

    /**
     * Create an instance from an asset name.
     *
     * @param assetName asset name.
     * @return an {@link ImageSource} instance.
     */
    @NonNull
    public static ImageSource asset(@NonNull String assetName) {
        if (assetName == null) {
            throw new NullPointerException("Asset name must not be null");
        }
        return uri(ASSET_PREFIX + assetName);
    }

    /**
     * Create an instance from a URI. If the URI does not start with a scheme, it's assumed to be the URI
     * of a file.
     *
     * @param uri image URI.
     * @return an {@link ImageSource} instance.
     */
    @NonNull
    public static ImageSource uri(@NonNull String uri) {
        if (uri == null) {
            throw new NullPointerException("Uri must not be null");
        }
        if (!uri.contains("://")) {
            if (uri.startsWith("/")) {
                uri = uri.substring(1);
            }
            uri = FILE_PREFIX + uri;
        }
        return new ImageSource(Uri.parse(uri));
    }

    /**
     * Create an instance from a URI.
     *
     * @param uri image URI.
     * @return an {@link ImageSource} instance.
     */
    @NonNull
    public static ImageSource uri(@NonNull Uri uri) {
        if (uri == null) {
            throw new NullPointerException("Uri must not be null");
        }
        return new ImageSource(uri);
    }

    /**
     * Provide a loaded bitmap for display.
     *
     * @param bitmap bitmap to be displayed.
     * @return an {@link ImageSource} instance.
     */
    @NonNull
    public static ImageSource bitmap(@NonNull Bitmap bitmap) {
        if (bitmap == null) {
            throw new NullPointerException("Bitmap must not be null");
        }
        return new ImageSource(bitmap, false);
    }

    /**
     * Provide a loaded and cached bitmap for display. This bitmap will not be recycled when it is no
     * longer needed. Use this method if you loaded the bitmap with an image loader such as Picasso
     * or Volley.
     *
     * @param bitmap bitmap to be displayed.
     * @return an {@link ImageSource} instance.
     */
    @NonNull
    public static ImageSource cachedBitmap(@NonNull Bitmap bitmap) {
        if (bitmap == null) {
            throw new NullPointerException("Bitmap must not be null");
        }
        return new ImageSource(bitmap, true);
    }

    /**
     * Create an instance from a byte buffer
     *
     * @param buffer Byte buffer
     * @return an {@link ImageSource} instance.
     */
    @NonNull
    public static ImageSource buffer(@NonNull Buffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("Buffer cannot be null");
        }
        return new ImageSource(buffer);
    }

    /**
     * Enable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap.,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource tilingEnabled() {
        return tiling(true);
    }

    /**
     * Disable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource tilingDisabled() {
        return tiling(false);
    }

    /**
     * Enable or disable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @param tile whether tiling should be enabled.
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource tiling(boolean tile) {
        this.tile = tile;
        return this;
    }

    /**
     * Use a region of the source image. Region must be set independently for the full size image and the preview if
     * you are using one.
     *
     * @param sRegion the region of the source image to be displayed.
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource region(Rect sRegion) {
        this.sRegion = sRegion;
        setInvariants();
        return this;
    }

    /**
     * Declare the dimensions of the image. This is only required for a full size image, when you are specifying a URI
     * and also a preview image. When displaying a bitmap object, or not using a preview, you do not need to declare
     * the image dimensions. Note if the declared dimensions are found to be incorrect, the view will reset.
     *
     * @param sWidth  width of the source image.
     * @param sHeight height of the source image.
     * @return this instance for chaining.
     */
    @NonNull
    public ImageSource dimensions(int sWidth, int sHeight) {
        if (bitmap == null) {
            this.sWidth = sWidth;
            this.sHeight = sHeight;
        }
        setInvariants();
        return this;
    }

    private void setInvariants() {
        if (this.sRegion != null) {
            this.tile = true;
            this.sWidth = this.sRegion.width();
            this.sHeight = this.sRegion.height();
        }
    }

    public final Uri getUri() {
        return uri;
    }

    public final Bitmap getBitmap() {
        return bitmap;
    }

    public final @Nullable InputStream getBufferStream() {
        if (buffer != null) {
            return buffer.peek().inputStream();
        } else {
            return null;
        }
    }

    public final void clearBuffer() {
        if (buffer != null) {
            buffer.close();
        }
    }

    public final boolean getTile() {
        return tile;
    }

    public final int getSWidth() {
        return sWidth;
    }

    public final int getSHeight() {
        return sHeight;
    }

    public final Rect getSRegion() {
        return sRegion;
    }

    public final boolean isCached() {
        return cached;
    }
}
