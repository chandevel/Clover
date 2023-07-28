/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.view;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.net.NetUtils.MB;
import static com.github.adamantcheese.chan.core.net.NetUtilsClasses.BUFFER_CONVERTER;
import static com.github.adamantcheese.chan.core.net.NetUtilsClasses.BitmapResult;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDefaultMuteState;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.View;
import android.webkit.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.*;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.widget.CustomScaleImageView;
import com.github.adamantcheese.chan.ui.widget.ShapeablePostImageView;
import com.github.adamantcheese.chan.utils.*;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.source.*;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.cache.*;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okio.Buffer;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class MultiImageView
        extends FrameLayout
        implements MultiImageViewGestureDetector.MultiImageViewGestureDetectorCallback, DefaultLifecycleObserver,
                   ProgressResponseBody.ProgressListener {

    public enum Mode {
        UNLOADED,
        LOWRES,
        BIGIMAGE,
        GIFIMAGE,
        VIDEO,
        WEBVIEW,
        OTHER
    }

    private PostImage postImage;
    private Callback callback;
    private boolean op;

    private Mode mode = Mode.UNLOADED;
    private Call thumbnailRequest;
    private Call request;

    private boolean hasContent = false;
    private boolean requestedBackgroundOpacity = ChanSettings.useOpaqueBackgrounds.get();
    private boolean imageAlreadySaved = false;
    private final GestureDetector gestureDetector;
    private final View exoClickHandler;

    public MultiImageView(Context context) {
        this(context, null);
    }

    public MultiImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MultiImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.gestureDetector = new GestureDetector(context, new MultiImageViewGestureDetector(this));

        exoClickHandler = new View(getContext());
        exoClickHandler.setOnClickListener(null);
        exoClickHandler.setId(Integer.MAX_VALUE);
        exoClickHandler.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));

        setOnClickListener(null);

        if (context instanceof LifecycleOwner) {
            ((LifecycleOwner) context).getLifecycle().addObserver(this);
        }
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        Player exoplayer = getExoplayerPlayer();
        if (exoplayer != null) {
            exoplayer.pause();
        }
    }

    public void bindPostImage(PostImage postImage, Callback callback, boolean op) {
        this.postImage = postImage;
        this.callback = callback;
        this.op = op;
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setMode(final Mode newMode, boolean center) {
        this.mode = newMode;
        hasContent = false;
        post(() -> {
            switch (newMode) {
                case LOWRES:
                    setThumbnail(center);
                    requestedBackgroundOpacity = ChanSettings.useOpaqueBackgrounds.get();
                    break;
                case BIGIMAGE:
                    setBigImage();
                    break;
                case GIFIMAGE:
                    setGif();
                    break;
                case VIDEO:
                    setVideo();
                    break;
                case WEBVIEW:
                    setWebview();
                    break;
                case OTHER:
                    setOther();
                    break;
            }
        });
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public View getActiveView() {
        View ret = null;
        if (!hasContent) return new View(getContext());
        switch (mode) {
            case LOWRES:
            case OTHER:
                ret = findView(ShapeablePostImageView.class);
                break;
            case BIGIMAGE:
                ret = findView(CustomScaleImageView.class);
                break;
            case GIFIMAGE:
                ret = findView(GifImageView.class);
                if (ret == null) {
                    // possible that this is a single frame gif that was converted internally
                    ret = findView(CustomScaleImageView.class);
                }
                break;
            case VIDEO:
                ret = findView(StyledPlayerView.class);
                break;
            case WEBVIEW:
                ret = findView(WebView.class);
                break;
        }
        return ret;
    }

    @Nullable
    private View findView(Class<? extends View> classType) {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getClass().equals(classType)) {
                return getChildAt(i);
            }
        }
        return null;
    }

    @Nullable
    private Player getExoplayerPlayer() {
        View activeView = getActiveView();
        if (activeView instanceof StyledPlayerView) {
            StyledPlayerView activePlayerView = (StyledPlayerView) activeView;
            return activePlayerView.getPlayer();
        }
        return null;
    }

    @Override
    public boolean isImageAlreadySaved() {
        return imageAlreadySaved;
    }

    @Override
    public void setImageAlreadySaved() {
        imageAlreadySaved = true;
    }

    @Override
    public void onTap() {
        callback.onTap();
    }

    @Override
    public void checkImmersive() {
        callback.checkImmersive();
    }

    @Override
    public void setClickHandler(boolean set) {
        if (set) {
            addView(exoClickHandler);
        } else {
            removeView(exoClickHandler);
        }
    }

    @Override
    public void togglePlayState() {
        Player exoPlayer = getExoplayerPlayer();
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(!exoPlayer.getPlayWhenReady());
        }
    }

    @Override
    public void onSwipeToCloseImage() {
        callback.onSwipeToCloseImage();
    }

    @Override
    public void onSwipeToSaveImage() {
        callback.onSwipeToSaveImage();
    }

    public void setVolume(boolean muted) {
        Player exoPlayer = getExoplayerPlayer();
        if (exoPlayer != null) {
            exoPlayer.setVolume(muted ? 0f : 1f);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (thumbnailRequest != null) {
            thumbnailRequest.cancel();
            thumbnailRequest = null;
        }

        if (request != null) {
            request.cancel();
            request = null;
        }

        Player exoPlayer = getExoplayerPlayer();
        if (exoPlayer != null) {
            // ExoPlayer will keep loading resources if we don't release it here.
            exoPlayer.release();
        }

        if (getContext() instanceof LifecycleOwner) {
            ((LifecycleOwner) getContext()).getLifecycle().removeObserver(this);
        }
    }

    @Override
    public void onDownloadProgress(
            HttpUrl source, long bytesRead, long contentLength, boolean firstUpdate, boolean done
    ) {
        if (request != null) {
            if (!request.request().url().equals(source)) return;
            BackgroundUtils.runOnMainThread(() -> {
                if (done) {
                    callback.hideProgress();
                    return;
                }
                if (firstUpdate) {
                    callback.onProgress(MultiImageView.this, 0, 1);
                    return;
                }

                if (contentLength != -1) {
                    callback.onProgress(MultiImageView.this, bytesRead, contentLength);
                }
            });
        }
    }

    private void setThumbnail(boolean center) {
        BackgroundUtils.ensureMainThread();

        if (thumbnailRequest != null) {
            thumbnailRequest.cancel();
            thumbnailRequest = null;
        }

        final HttpUrl thumbnailURL = postImage.getThumbnailUrl();
        thumbnailRequest = NetUtils.makeBitmapRequest(thumbnailURL, new BitmapResult() {
            @Override
            public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                thumbnailRequest = null;
                callback.hideProgress();
                if (center) onError(e);
            }

            @Override
            public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                thumbnailRequest = null;
                callback.hideProgress();
                onThumbnailBitmap(bitmap);
            }
        });
    }

    private void onThumbnailBitmap(Bitmap bitmap) {
        if (!hasContent || mode == Mode.LOWRES) {
            ShapeablePostImageView thumbnail = new ShapeablePostImageView(getContext());
            thumbnail.setType(postImage);
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbnail.setImageBitmap(bitmap);
            thumbnail.setOnClickListener(null);
            thumbnail.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
            thumbnail.disableRipple();

            onModeLoaded(Mode.LOWRES, thumbnail);
        }
    }

    private void processError(Exception e) {
        if (request != null) {
            request.cancel();
            request = null;
        }

        if (NetUtils.isCancelledException(e)) return;

        if (e instanceof NetUtilsClasses.HttpCodeException) {
            if (((NetUtilsClasses.HttpCodeException) e).isServerErrorNotFound()) {
                onNotFoundError();
                return;
            }
        }

        onError(e);
    }

    private void setBigImage() {
        // if this image has a sound file attached to the filename, set this to be a video instead and use exoplayer to display it
        if (ChanSettings.enableSoundposts.get() && SOUND_URL_PATTERN.matcher(postImage.filename).find()) {
            mode = Mode.VIDEO;
            setVideo();
            return;
        }

        if (request != null) {
            request.cancel();
            request = null;
        }

        request = NetUtils.makeRequest(NetUtils.applicationClient.getHttpRedirectClient(),
                postImage.imageUrl,
                BUFFER_CONVERTER,
                new NetUtilsClasses.MainThreadResponseResult<>(new NetUtilsClasses.ResponseResult<Buffer>() {
                    @Override
                    public void onFailure(Exception e) {
                        processError(e);
                    }

                    @Override
                    public void onSuccess(Buffer result) {
                        request = null;
                        setBitImageFileInternal(result, true);
                    }
                }),
                this,
                NetUtilsClasses.ONE_DAY_CACHE
        );
    }

    private void setGif() {
        // gifs are not playable by exoplayer, so unfortunately soundposts for gifs don't work

        if (request != null) {
            request.cancel();
            request = null;
        }

        request = NetUtils.makeRequest(NetUtils.applicationClient,
                postImage.imageUrl,
                BUFFER_CONVERTER,
                new NetUtilsClasses.MainThreadResponseResult<>(new NetUtilsClasses.ResponseResult<Buffer>() {
                    @Override
                    public void onFailure(Exception e) {
                        processError(e);
                    }

                    @Override
                    public void onSuccess(Buffer result) {
                        request = null;
                        if (!hasContent || mode == Mode.GIFIMAGE) {
                            setGifFile(result);
                        }
                    }
                }),
                this,
                NetUtilsClasses.ONE_DAY_CACHE
        );
    }

    private void setGifFile(Buffer buffer) {
        GifDrawable drawable;
        try {
            drawable = new GifDrawable(buffer.peek().readByteArray());

            // For single frame gifs, use the scaling image instead
            // The region decoder doesn't work for gifs, so we unfortunately
            // have to use the more memory intensive non tiling mode.
            if (drawable.getNumberOfFrames() == 1) {
                drawable.recycle();
                setBitImageFileInternal(buffer, false);
                return;
            }
        } catch (IOException e) {
            Logger.e(this, "Error while trying to set a gif file", e);
            onError(e);
            return;
        } catch (OutOfMemoryError e) {
            Runtime.getRuntime().gc();
            Logger.e(this, "OOM while trying to set a gif file", e);
            onOutOfMemoryError();
            return;
        } catch (Exception e) {
            Logger.e(this, "GifDrawable likely threw error, exception: ", e);
            onError(e);
            return;
        }

        GifImageView view = new GifImageView(getContext());
        view.setImageDrawable(drawable);
        view.setOnClickListener(null);
        view.setOnTouchListener((view1, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
        onModeLoaded(Mode.GIFIMAGE, view);
    }

    private static final ProgressiveMediaSource.Factory MEDIA_FACTORY;

    static {
        OkHttpDataSource.Factory okHttpFactory =
                new OkHttpDataSource.Factory((Call.Factory) NetUtils.applicationClient);
        okHttpFactory.setCacheControl(NetUtilsClasses.ONE_DAY_CACHE);
        CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory();
        cacheFactory.setUpstreamDataSourceFactory(okHttpFactory);
        cacheFactory.setCache(new SimpleCache(new File(getCacheDir(), "exoplayer"),
                new LeastRecentlyUsedCacheEvictor(50 * MB)
        ));
        MEDIA_FACTORY = new ProgressiveMediaSource.Factory(cacheFactory);
    }

    private static final Pattern SOUND_URL_PATTERN = Pattern.compile(".*\\[sound=(.*)\\]", Pattern.CASE_INSENSITIVE);

    private void setVideo() {
        if (!hasContent || mode == Mode.VIDEO) {
            StyledPlayerView exoVideoView = new StyledPlayerView(getContext());
            exoVideoView.setUseController(!ChanSettings.neverShowWebmControls.get());
            ExoPlayer.Builder exoPlayerBuilder = new ExoPlayer.Builder(getContext());
            ExoPlayer exoPlayer;
            MediaSource videoSource = MEDIA_FACTORY.createMediaSource(MediaItem.fromUri(postImage.imageUrl.toString()));
            try {
                if (ChanSettings.enableSoundposts.get()) {
                    Matcher m = SOUND_URL_PATTERN.matcher(postImage.filename);
                    if (!m.find()) {
                        throw new Exception("Fallback to no soundpost");
                    }
                    String soundURL = URLDecoder.decode(m.group(1), "UTF-8");
                    if (!StringUtils.startsWithAny(soundURL, "http://", "https://")) {
                        soundURL = "https://" + soundURL;
                    }
                    MediaSource soundSource = MEDIA_FACTORY.createMediaSource(MediaItem.fromUri(soundURL));

                    if (postImage.type == PostImage.Type.STATIC) {
                        // for static images, we want to display the image and overlay the sound
                        // exoplayer will attempt to play video contained inside of soundpost link, so we need to disable that
                        // we also need to disable any custom artwork so it doesn't override when we load the image as the default artwork
                        TrackSelector soundpostSelector = new DefaultTrackSelector(getContext());
                        soundpostSelector.setParameters(soundpostSelector
                                .getParameters()
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                                .setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, true)
                                .build());
                        exoPlayer = exoPlayerBuilder.setTrackSelector(soundpostSelector).build();
                        exoPlayer.setMediaSource(soundSource);
                    } else {
                        // for non-static images, we just play the video as normal, because the video source will be selected
                        // for video display, and the sound source for sound only, ignoring any video it has
                        exoPlayer = exoPlayerBuilder.build();
                        exoPlayer.setMediaSource(new MergingMediaSource(videoSource, soundSource));
                    }
                } else {
                    throw new Exception("Fallback to no soundpost");
                }
            } catch (Exception e) {
                exoPlayer = exoPlayerBuilder.build();
                exoPlayer.setMediaSource(videoSource);
            }
            exoPlayer.prepare();

            exoPlayer.setRepeatMode(ChanSettings.videoAutoLoop.get() ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);

            exoPlayer.addAnalyticsListener(new AnalyticsListener() {
                @Override
                public void onAudioEnabled(@NonNull EventTime eventTime, @NonNull DecoderCounters decoderCounters) {
                    callback.onAudioLoaded(MultiImageView.this);
                }
            });
            exoVideoView.setOnClickListener(null);
            exoVideoView.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
            exoVideoView.setUseController(false);
            exoVideoView.setControllerHideOnTouch(false);
            exoVideoView.setShowBuffering(StyledPlayerView.SHOW_BUFFERING_WHEN_PLAYING);
            exoVideoView.setUseArtwork(true);
            NetUtils.makeBitmapRequest(postImage.imageUrl, new BitmapResult() {
                @Override
                public void onBitmapFailure(
                        @NonNull HttpUrl source, Exception e
                ) {
                    exoVideoView.setDefaultArtwork(getContext().getDrawable(R.drawable.ic_fluent_speaker_2_24_filled));
                }

                @Override
                public void onBitmapSuccess(
                        @NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache
                ) {
                    exoVideoView.setDefaultArtwork(new BitmapDrawable(getContext().getResources(), bitmap));
                }
            });
            setVolume(getDefaultMuteState());
            exoVideoView.setPlayer(exoPlayer);
            exoPlayer.play();
            onModeLoaded(Mode.VIDEO, exoVideoView);
        }
    }

    private void setOther() {
        if (request != null) {
            request.cancel();
            request = null;
        }

        if (!hasContent || mode == Mode.OTHER) {
            AndroidUtils.buildCommonSnackbar(this,
                    R.string.open_link_confirmation,
                    R.string.open,
                    v -> openLink(postImage.imageUrl.toString())
            );
            onModeLoaded(Mode.OTHER, null);
        }
    }

    // these colors are specific to 4chan for the time being
    private static final int BACKGROUND_COLOR_SFW = Color.argb(255, 214, 218, 240);
    private static final int BACKGROUND_COLOR_SFW_OP = Color.argb(255, 238, 242, 255);
    private static final int BACKGROUND_COLOR_NSFW = Color.argb(255, 240, 224, 214);
    private static final int BACKGROUND_COLOR_NSFW_OP = Color.argb(255, 255, 255, 238);

    public void toggleOpacity() {
        View activeView = getActiveView();
        if (!(activeView instanceof CustomScaleImageView || activeView instanceof GifImageView)) {
            callback.onOpacityChanged(this, false, requestedBackgroundOpacity);
            return;
        }

        int boardColor = callback.getLoadable().board.workSafe
                ? (op ? BACKGROUND_COLOR_SFW_OP : BACKGROUND_COLOR_SFW)
                : (op ? BACKGROUND_COLOR_NSFW_OP : BACKGROUND_COLOR_NSFW);
        int newBackgroundColor = requestedBackgroundOpacity ? boardColor : Color.TRANSPARENT;

        if (activeView instanceof CustomScaleImageView) {
            ((CustomScaleImageView) activeView).setTileBackgroundColor(newBackgroundColor);
        } else {
            ((GifImageView) activeView)
                    .getDrawable()
                    .setColorFilter(new PorterDuffColorFilter(newBackgroundColor, PorterDuff.Mode.DST_OVER));
        }

        callback.onOpacityChanged(this, true, requestedBackgroundOpacity);
        requestedBackgroundOpacity = !requestedBackgroundOpacity;
    }

    private void setBitImageFileInternal(Buffer buffer, boolean tiling) {
        final CustomScaleImageView image = new CustomScaleImageView(getContext());
        image.setImage(ImageSource.buffer(buffer).tiling(tiling));
        //this is required because unlike the other views, if we don't have layout dimensions, the callback won't be called
        //see https://github.com/davemorrissey/subsampling-scale-image-view/issues/143
        addView(image, 0, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        image.setCallback(new CustomScaleImageView.Callback() {
            @Override
            public void onReady() {
                if (!hasContent || mode == Mode.BIGIMAGE) {
                    callback.hideProgress();
                    onModeLoaded(Mode.BIGIMAGE, image);
                }
            }

            @Override
            public void onError(Exception e, boolean wasInitial) {
                if (e.getCause() instanceof OutOfMemoryError) {
                    Logger.e(this, "OOM while trying to set a big image file", e);
                    Runtime.getRuntime().gc();
                    onOutOfMemoryError();
                } else {
                    onBigImageError(wasInitial);
                }
            }
        });
        image.setOnClickListener(null);
        image.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setWebview() {
        final WebView webView = new WebView(getContext());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Logger.i(MultiImageView.this,
                        consoleMessage.lineNumber() + ":" + consoleMessage.message() + " " + consoleMessage.sourceId()
                );
                if (consoleMessage.message().contains("WARNING")) {
                    showToast(getContext(), consoleMessage.message(), Toast.LENGTH_LONG);
                }
                return true;
            }
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl(postImage.imageUrl.toString());
        webView.setBackgroundColor(Color.TRANSPARENT);
        if (!hasContent || mode == Mode.WEBVIEW) {
            callback.hideProgress();
            onModeLoaded(Mode.WEBVIEW, webView);
        }
    }

    private void onError(Exception exception) {
        String message = String.format(Locale.ENGLISH,
                "%s: %s",
                getString(R.string.image_preview_failed),
                exception.getMessage()
        );

        showToast(getContext(), message);
        callback.hideProgress();
    }

    private void onNotFoundError() {
        showToast(getContext(), R.string.image_not_found);
        callback.hideProgress();
    }

    private void onOutOfMemoryError() {
        showToast(getContext(), R.string.image_preview_failed_oom);
        callback.hideProgress();
    }

    private void onBigImageError(boolean wasInitial) {
        if (wasInitial) {
            showToast(getContext(), R.string.image_failed_big_image);
            callback.hideProgress();
        }
    }

    private void onModeLoaded(Mode mode, View view) {
        if (mode != Mode.LOWRES) {
            if (thumbnailRequest != null) {
                thumbnailRequest.cancel();
                thumbnailRequest = null;
            }
        }

        if (view != null) {
            // Remove all other views
            boolean alreadyAttached = false;
            for (int i = getChildCount() - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child != view) {
                    if (child instanceof StyledPlayerView) {
                        ((StyledPlayerView) child).getPlayer().release();
                    } else if (child instanceof WebView) {
                        ((WebView) child).destroy();
                    }
                    removeViewAt(i);
                } else {
                    alreadyAttached = true;
                }
            }

            if (!alreadyAttached) {
                addView(view, 0, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
                if (view instanceof StyledPlayerView) {
                    addView(exoClickHandler);
                }
            }
        }

        hasContent = true;
        callback.onModeLoaded(this, mode);
        toggleOpacity();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child instanceof GifImageView) {
            GifImageView gif = (GifImageView) child;
            if (gif.getDrawable() instanceof GifDrawable) {
                GifDrawable drawable = (GifDrawable) gif.getDrawable();
                if (drawable.getFrameByteCount() > 100 * MB) { // max size from RecordingCanvas
                    onError(new Exception("Uncompressed GIF too large (>100MB), " + PostUtils.getReadableFileSize(
                            drawable.getFrameByteCount())));
                    return false;
                }
            }
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public interface Callback {
        void onTap();

        void checkImmersive();

        void onSwipeToCloseImage();

        void onSwipeToSaveImage();

        void onProgress(MultiImageView multiImageView, long current, long total);

        void onModeLoaded(MultiImageView multiImageView, Mode mode);

        void onAudioLoaded(MultiImageView multiImageView);

        void onOpacityChanged(MultiImageView multiImageView, boolean hasOpacity, boolean opaque);

        void hideProgress();

        Loadable getLoadable();
    }
}
