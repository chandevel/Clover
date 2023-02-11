package com.github.adamantcheese.chan.core.net;

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
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import kotlin.Triple;
import okhttp3.*;

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
            @Nullable HttpUrl url, @NonNull ImageView imageView, @Nullable ResponseResult<Void> callback
    ) {
        if (url == null) {
            cancelLoad(imageView);
            return;
        }

        if (getImageLoadableData() == null) {
            setImageLoadableData(new ImageLoadableData());
        }
        ImageLoadableData data = getImageLoadableData();

        Call currentCall = data.getImageCall();
        // in progress check, in case of a re-bind without recycling
        if (currentCall != null) {
            if (currentCall.request().url().equals(url)) {
                return;
            } else {
                cancelLoad(imageView); // cancel before set again
            }
        }

        // completed load check
        if (url.equals(data.getLoadedUrl())) return;

        // request the image
        Triple<Call, Callback, Runnable> networkInfo =
                NetUtils.makeBitmapRequest(url, new NetUtilsClasses.BitmapResult() {
                    @Override
                    public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                        data.setImageCall(null);
                        data.setRunnable(null);
                        // for a chained load, this means that the last successful load will remain
                        if (data.getLoadedUrl() != null) return;
                        data.setLoadedUrl(null); // fail, nullify the last url

                        // if this has an error code associated with it, draw it up all fancy-like
                        if (e instanceof NetUtilsClasses.HttpCodeException) {
                            if (((NetUtilsClasses.HttpCodeException) e).isServerErrorNotFound()) {
                                // for this case, never try and load again and treat it as though it loaded fully
                                data.setLoadedUrl(source);
                            }
                        } else {
                            Logger.d(this, "Failed to load image for " + source, e);
                        }
                        imageView.setImageBitmap(BitmapRepository.getHttpExceptionBitmap(imageView.getContext(), e));

                        if (callback != null) {
                            callback.onFailure(e);
                        }
                    }

                    @Override
                    public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                        // success, save the last url as good
                        // for a chained load, this means that the last successful load will remain
                        data.setImageCall(null);
                        data.setRunnable(null);
                        data.setLoadedUrl(source);

                        // if not from cache, fade the view in; if set, fade out first
                        if (!fromCache) {
                            if (imageView.getDrawable() != null) {
                                imageView
                                        .animate()
                                        .setInterpolator(new AccelerateInterpolator(2f))
                                        .alpha(0f)
                                        .withEndAction(() -> {
                                            imageView.setImageBitmap(bitmap);
                                            imageView
                                                    .animate()
                                                    .alpha(1f)
                                                    .setInterpolator(new DecelerateInterpolator(2f));
                                        });
                            } else {
                                imageView.setImageBitmap(bitmap);
                                imageView.animate().alpha(1f).setInterpolator(new DecelerateInterpolator(2f));
                            }
                        } else {
                            imageView.setImageBitmap(bitmap);
                            imageView.setAlpha(1f);
                        }

                        if (callback != null) {
                            callback.onSuccess(null);
                        }
                    }
                }, 0, 0, -1, null, true, true);
        data.setImageCall(networkInfo == null ? null : networkInfo.getFirst());
        data.setRunnable(networkInfo == null ? null : networkInfo.getThird());
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

        HttpUrl firstLoadUrl = loadUrl;
        loadUrl(firstLoadUrl, imageView, (NoFailResponseResult<Void>) result -> {
            if (ChanSettings.shouldUseFullSizeImage(postImage) && !firstLoadUrl.equals(secondUrl)) {
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
        if (getImageLoadableData() == null) {
            setImageLoadableData(new ImageLoadableData());
        }
        getImageLoadableData().cancel();
    }

    ImageLoadableData getImageLoadableData();

    void setImageLoadableData(ImageLoadableData data);

    /**
     * This class contains information related to loading images, easier than having every class mange this data
     */
    class ImageLoadableData {
        private HttpUrl loadedUrl = null;
        private Call call = null;
        private Runnable runnable = null;

        public ImageLoadableData() {
        }

        public void cancel() {
            setLoadedUrl(null);
            Call currentCall = getImageCall();
            if (currentCall != null) {
                currentCall.cancel();
            }
            setImageCall(null);
            BackgroundUtils.cancel(runnable);
            setRunnable(null);
        }

        public HttpUrl getLoadedUrl() {
            return loadedUrl;
        }

        public void setLoadedUrl(HttpUrl loadedUrl) {
            this.loadedUrl = loadedUrl;
        }

        public Call getImageCall() {
            return call;
        }

        public void setImageCall(Call call) {
            this.call = call;
        }

        public Runnable getRunnable() {
            return runnable;
        }

        public void setRunnable(Runnable runnable) {
            this.runnable = runnable;
        }
    }
}
