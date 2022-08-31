package com.github.adamantcheese.chan.core.net;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Bitmap;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.NoFailResponseResult;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import okhttp3.Call;
import okhttp3.HttpUrl;

/**
 * A simple image loader that loads an image into an image view
 */
public interface ImageLoadable {
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
        if (url == null || imageView == null) return;

        // in progress check, in case of a re-bind without recycling
        if (getImageCall() != null) {
            if (getImageCall().request().url().equals(url)) {
                return;
            } else {
                cancelLoad(imageView); // cancel before set again
            }
        }

        // completed load check
        if (getLastHttpUrl() != null && url.equals(getLastHttpUrl())) return;

        // request the image
        Call imageCall = NetUtils.makeBitmapRequest(url, new NetUtilsClasses.BitmapResult() {
            @Override
            public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                if (doStandardActions(source)) return;
                // for a chained load, this means that the last successful load will remain
                if (getLastHttpUrl() != null) return;
                setLastHttpUrl(null); // fail, nullify the last url
                Bitmap res = BitmapRepository.paddedError;
                // if this has an error code associated with it, draw it up all fancy-like
                if (e instanceof NetUtilsClasses.HttpCodeException) {
                    if (((NetUtilsClasses.HttpCodeException) e).isServerErrorNotFound()) {
                        // for this case, never try and load again and treat it as though it loaded fully
                        setLastHttpUrl(source);
                    }
                    res = BitmapRepository.getHttpExceptionBitmap(imageView.getContext(), e);
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
                // success, save the last url as good
                // for a chained load, this means that the last successful load will remain
                setLastHttpUrl(url);
                if (callback != null) {
                    callback.onSuccess(null);
                }

                // if not from cache, fade the view in; if set, fade out first
                if (!fromCache) {
                    if (imageView.getDrawable() != null) {
                        imageView.animate()
                                .setInterpolator(new AccelerateInterpolator(2f))
                                .alpha(0f)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        imageView.setImageBitmap(bitmap);
                                        imageView.animate().setInterpolator(new DecelerateInterpolator(2f)).alpha(1f);
                                    }
                                });
                    } else {
                        imageView.setImageBitmap(bitmap);
                        imageView.animate().setInterpolator(new DecelerateInterpolator(2f)).alpha(1f);
                    }
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
        HttpUrl loadUrl = postImage.getThumbnailUrl();
        HttpUrl secondUrl = postImage.spoiler() ? postImage.getThumbnailUrl() : postImage.imageUrl;
        if (ChanSettings.shouldUseFullSizeImage(postImage) && NetUtils.isCached(secondUrl)) {
            loadUrl = secondUrl;
        }
        loadUrl(loadUrl, imageView, (NoFailResponseResult<Void>) result -> {
            if (ChanSettings.shouldUseFullSizeImage(postImage)) {
                loadUrl(secondUrl, imageView);
            }
        });
    }

    /**
     * Call this whenever your view is going away, like being recycled in a RecyclerView.
     *
     * @param imageView The imageview that would otherwise be loaded.
     */
    default void cancelLoad(ImageView imageView) {
        imageView.animate().cancel();
        imageView.setImageBitmap(null);
        imageView.setAlpha(0f);
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
}
