package com.github.adamantcheese.chan.core.net;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static android.graphics.Color.BLUE;
import static android.graphics.Color.CYAN;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.MAGENTA;
import static android.graphics.Color.RED;
import static android.graphics.Color.YELLOW;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

/**
 * A simple image loader that loads an image into an image view
 */
public interface ImageLoadable {
    int[] RAINBOW_COLORS =
            {RED, 0xFF7F00, YELLOW, 0x7FFF00, GREEN, 0x00FF7F, CYAN, 0x007FFF, BLUE, 0x7F00FF, MAGENTA, 0xFF007F};

    /**
     * Set an arbitrary URL for the provided image view. Also adds a callback for when a load finishes.
     * Note that all arguments are null-safe, but you won't get anything if you don't have a URL to set or an imageview
     * to load into.
     *
     * @param url       The image URL to set.
     * @param imageView The image view to load into.
     * @param callback  An optional callback to be called after a load finishes.
     *                  You can use the wrapper method below instead of putting null here.
     */
    default void loadUrl(
            @Nullable HttpUrl url, @Nullable ImageView imageView, @Nullable ResponseResult<Void> callback
    ) {
        if (url == null || imageView == null || (getLastHttpUrl() != null && url.equals(getLastHttpUrl())))
            return; //nothing to do!
        // setup a loading drawable if this is a first time load (ie no existing image)
        // todo maybe use a LayerListDrawable to combine the loading image with the thumbnail for full-size loads?
        if (getLastHttpUrl() == null) {
            CircularProgressDrawable loading = new CircularProgressDrawable(imageView.getContext());
            if (AndroidUtils.isAprilFoolsDay()) {
                loading.setColorSchemeColors(RAINBOW_COLORS);
            } else {
                loading.setColorSchemeColors(getAttrColor(imageView.getContext(), R.attr.colorAccent));
            }
            loading.start();
            loading.setStyle(CircularProgressDrawable.LARGE);
            imageView.setImageDrawable(loading);
        }

        // request the image
        Call imageCall = NetUtils.makeBitmapRequest(url, new NetUtilsClasses.BitmapResult() {
            @Override
            public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                if (doStandardActions(source)) return;
                // for a chained load, this means that the last successful load will remain
                if (getLastHttpUrl() != null) return;
                setLastHttpUrl(null);
                Bitmap res = BitmapRepository.paddedError;
                // if this has an error code associated with it, draw it up all fancy-like
                if (e instanceof NetUtilsClasses.HttpCodeException) {
                    String code = String.valueOf(((NetUtilsClasses.HttpCodeException) e).code);
                    res = res.copy(BitmapRepository.paddedError.getConfig(), true);
                    Canvas temp = new Canvas(res);
                    RectF bounds = new RectF(0, 0, temp.getWidth(), temp.getHeight());

                    TextPaint errorTextPaint = new TextPaint();
                    errorTextPaint.setAntiAlias(true);
                    errorTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
                    errorTextPaint.setTextAlign(Paint.Align.CENTER);
                    errorTextPaint.setTextSize(sp(imageView.getContext(), 24));
                    errorTextPaint.setColor(0xFFDD3333);

                    TextPaint errorBorderTextPaint = new TextPaint(errorTextPaint);
                    errorBorderTextPaint.setStyle(Paint.Style.STROKE);
                    errorBorderTextPaint.setStrokeWidth(sp(imageView.getContext(), 3));
                    errorBorderTextPaint.setColor(0xFFFFFFFF);

                    float textHeight = errorTextPaint.descent() - errorTextPaint.ascent();
                    float textOffset = (textHeight / 2) - errorTextPaint.descent();

                    temp.drawText(code, bounds.centerX(), bounds.centerY() + textOffset, errorBorderTextPaint);
                    temp.drawText(code, bounds.centerX(), bounds.centerY() + textOffset, errorTextPaint);
                } else {
                    Logger.d(this, "Failed to load image for " + StringUtils.maskImageUrl(url), e);
                }
                imageView.setImageBitmap(res);
                if (callback != null) {
                    callback.onFailure(e);
                }
            }

            @Override
            public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                if (doStandardActions(source)) return;
                // only on a success do we save the last URL
                // for a chained load, this means that the last successful load will remain
                setLastHttpUrl(url);
                if (callback != null) {
                    callback.onSuccess(null);
                }
                // if not from cache, fade the view out then in
                if (!fromCache) {
                    ViewPropertyAnimator fadeOut =
                            imageView.animate().setInterpolator(new AccelerateInterpolator(2f)).alpha(0f);
                    fadeOut.setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            imageView.setImageBitmap(bitmap);
                            imageView.animate().setInterpolator(new DecelerateInterpolator(2f)).alpha(1f);
                        }
                    });
                } else {
                    imageView.setImageBitmap(bitmap);
                    imageView.setAlpha(1f);
                }
            }

            /**
             *
             * @param source source URL to check against requested URL
             * @return true if the source does NOT match the requested URL
             */
            private boolean doStandardActions(HttpUrl source) {
                if (!source.equals(url)) return true; // sanity check, don't load incorrect images into the view
                setImageCall(null); // set afterwards, otherwise image loading might mess up
                return false;
            }
        }, (int) getMaxImageSize(), (int) getMaxImageSize(), (source, bytesRead, contentLength, start, done) -> {
            if (imageView.getDrawable() instanceof CircularProgressDrawable) {
                CircularProgressDrawable loading = (CircularProgressDrawable) imageView.getDrawable();
                if (start) loading.stop();
                loading.setProgressRotation(0.75f);
                loading.setStartEndTrim(0f, Math.min(1f, (float) bytesRead / contentLength));
            }
        });
        setImageCall(imageCall);
    }

    /**
     * Set an arbitrary URL for the provided image view.
     *
     * @param url       The image URL to set.
     * @param imageView The image view to load into.
     */
    default void loadUrl(HttpUrl url, ImageView imageView) {
        loadUrl(url, imageView, null);
    }

    /**
     * Set a post image for the provided image view. Takes care of getting the proper thumbnail or full-size image.
     *
     * @param postImage The post image to set.
     * @param imageView The image view to load into.
     */
    default void loadPostImage(PostImage postImage, ImageView imageView) {
        if (ChanSettings.shouldUseFullSizeImage(postImage)) {
            HttpUrl secondLoadUrl = postImage.spoiler() ? postImage.getThumbnailUrl() : postImage.imageUrl;
            if (getLastHttpUrl() == null) {
                // nothing loaded yet, do a full load
                loadUrl(postImage.getThumbnailUrl(), imageView, new ResponseResult<Void>() {
                    @Override
                    public void onFailure(Exception e) {
                        // just try and load the bigger image outright
                        loadUrl(secondLoadUrl, imageView);
                    }

                    @Override
                    public void onSuccess(Void result) {
                        loadUrl(secondLoadUrl, imageView);
                    }
                });
            } else if (getLastHttpUrl().equals(postImage.getThumbnailUrl())) {
                // thumbnail loaded, load the second url
                loadUrl(secondLoadUrl, imageView);
            }
        } else {
            if (getLastHttpUrl() == null) {
                loadUrl(postImage.getThumbnailUrl(), imageView);
            }
        }
    }

    /**
     * Call this whenever your view is going away, like being recycled in a RecyclerView.
     *
     * @param imageView The imageview that would otherwise be loaded.
     */
    default void cancelLoad(ImageView imageView) {
        imageView.animate().cancel();
        imageView.setImageBitmap(null);
        setLastHttpUrl(null);
        if (getImageCall() != null) {
            getImageCall().cancel();
            setImageCall(null);
        }
    }

    /*

    Because this is an interface and not a class, in order to enforce the data requirements of this interface to
    provide proper, consistent image loading, it needs to have access to local fields in the implementer class. These
    include a last-good URL and a place for an ongoing OkHttp call. If a view is re-bound without being recycled, the
    last URL field ensures that an image isn't set into a loading state first and will keep the existing image in-place.
    This prevents flickering of images when used with swapAdapter during a bind operation (see PostCell).

    */
    HttpUrl getLastHttpUrl();

    void setLastHttpUrl(HttpUrl url);

    Call getImageCall();

    void setImageCall(Call call);

    float getMaxImageSize();
}
