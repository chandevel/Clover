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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.MediaSourceCallback;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.cache.downloader.DownloadRequestExtraInfo;
import com.github.adamantcheese.chan.core.cache.stream.WebmStreamingDataSource;
import com.github.adamantcheese.chan.core.cache.stream.WebmStreamingSource;
import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.BitmapLruImageCache;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.widget.CancellableToast;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.PostUtils;
import com.github.k1rakishou.fsaf.file.RawFile;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Locale;

import javax.inject.Inject;

import okhttp3.Call;
import okhttp3.HttpUrl;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppFileProvider;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAudioManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForMeasure;
import static com.github.adamantcheese.chan.utils.NetUtils.BitmapResult;

public class MultiImageView
        extends FrameLayout
        implements MultiImageViewGestureDetector.MultiImageViewGestureDetectorCallbacks, AudioListener,
                   LifecycleObserver {

    public enum Mode {
        UNLOADED,
        LOWRES,
        BIGIMAGE,
        GIFIMAGE,
        VIDEO,
        OTHER
    }

    @Inject
    FileCacheV2 fileCacheV2;
    @Inject
    WebmStreamingSource webmStreamingSource;

    private PostImage postImage;
    private Callback callback;
    private boolean op;

    private Mode mode = Mode.UNLOADED;
    private Call thumbnailRequest;
    private CancelableDownload bigImageRequest;
    private CancelableDownload gifRequest;
    private CancelableDownload videoRequest;
    private SimpleExoPlayer exoPlayer;
    private CancellableToast cancellableToast;

    private boolean hasContent = false;
    private boolean mediaSourceCancel = false;
    private boolean transparentBackground = ChanSettings.transparencyOn.get();
    private boolean imageAlreadySaved = false;
    private GestureDetector gestureDetector;
    private View exoClickHandler;

    public MultiImageView(Context context) {
        this(context, null);
    }

    public MultiImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MultiImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.cancellableToast = new CancellableToast();
        this.gestureDetector = new GestureDetector(context, new MultiImageViewGestureDetector(this));

        exoClickHandler = new View(getContext());
        exoClickHandler.setOnClickListener(null);
        exoClickHandler.setId(Integer.MAX_VALUE);
        exoClickHandler.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));

        inject(this);
        setOnClickListener(null);

        if (context instanceof StartActivity) {
            ((StartActivity) context).getLifecycle().addObserver(this);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
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

    public void setMode(Loadable loadable, final Mode newMode, boolean center) {
        this.mode = newMode;
        hasContent = false;
        waitForMeasure(this, view -> {
            if (getWidth() == 0 || getHeight() == 0 || !isLaidOut()) {
                Logger.e(MultiImageView.this,
                        "getWidth() or getHeight() returned 0, or view not laid out, not loading"
                );
                return false;
            }
            switch (newMode) {
                case LOWRES:
                    setThumbnail(postImage, center);
                    transparentBackground = ChanSettings.transparencyOn.get();
                    break;
                case BIGIMAGE:
                    setBigImage(loadable, postImage);
                    break;
                case GIFIMAGE:
                    setGif(loadable, postImage);
                    break;
                case VIDEO:
                    setVideo(loadable, postImage);
                    break;
                case OTHER:
                    setOther(loadable, postImage);
                    break;
            }
            return true;
        });
    }

    public Mode getMode() {
        return mode;
    }

    @NonNull
    @Override
    public View getActiveView() {
        View ret = null;
        if (!hasContent) return new View(getContext());
        switch (mode) {
            case LOWRES:
            case OTHER:
                ret = findView(ThumbnailImageView.class);
                break;
            case BIGIMAGE:
                ret = findView(CustomScaleImageView.class);
                break;
            case GIFIMAGE:
                ret = findView(GifImageView.class);
                break;
            case VIDEO:
                ret = findView(PlayerView.class);
                break;
        }
        return ret == null ? new View(getContext()) : ret;
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

    private boolean getDefaultMuteState() {
        return ChanSettings.videoDefaultMuted.get() && (ChanSettings.headsetDefaultMuted.get()
                || !getAudioManager().isWiredHeadsetOn());
    }

    public void setVolume(boolean muted) {
        if (exoPlayer != null) {
            exoPlayer.setVolume(muted ? 0f : 1f);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLoad();

        if (getContext() instanceof StartActivity) {
            ((StartActivity) getContext()).getLifecycle().removeObserver(this);
        }
    }

    private void setThumbnail(PostImage postImage, boolean center) {
        BackgroundUtils.ensureMainThread();

        if (thumbnailRequest != null) {
            return;
        }

        final HttpUrl thumbnailURL = postImage.spoiler() ? postImage.spoilerThumbnailUrl : postImage.thumbnailUrl;
        thumbnailRequest = NetUtils.makeBitmapRequest(thumbnailURL, new BitmapResult() {
            @Override
            public void onBitmapFailure(Bitmap errormap, Exception e) {
                thumbnailRequest = null;
                if (center) onError(e);
            }

            @Override
            public void onBitmapSuccess(Bitmap bitmap) {
                thumbnailRequest = null;
                onThumbnailBitmap(bitmap);
            }
        }, getWidth(), getHeight());
    }

    private void onThumbnailBitmap(Bitmap bitmap) {
        if (!hasContent || mode == Mode.LOWRES) {
            ThumbnailImageView thumbnail = new ThumbnailImageView(getContext());
            thumbnail.setType(postImage.type);
            thumbnail.setImageBitmap(bitmap);
            thumbnail.setOnClickListener(null);
            thumbnail.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));

            onModeLoaded(Mode.LOWRES, thumbnail);
        }
    }

    private void setBigImage(Loadable loadable, PostImage postImage) {
        BackgroundUtils.ensureMainThread();

        if (bigImageRequest != null) {
            return;
        }

        DownloadRequestExtraInfo extraInfo = new DownloadRequestExtraInfo(postImage.size, postImage.fileHash);

        bigImageRequest =
                fileCacheV2.enqueueChunkedDownloadFileRequest(loadable, postImage, extraInfo, new FileCacheListener() {

                    @Override
                    public void onStart(int chunksCount) {
                        BackgroundUtils.ensureMainThread();

                        callback.onStartDownload(MultiImageView.this, chunksCount);
                    }

                    @Override
                    public void onProgress(int chunkIndex, long downloaded, long total) {
                        BackgroundUtils.ensureMainThread();

                        callback.onProgress(MultiImageView.this, chunkIndex, downloaded, total);
                    }

                    @Override
                    public void onSuccess(RawFile file) {
                        BackgroundUtils.ensureMainThread();

                        setBitImageFileInternal(new File(file.getFullPath()), true);

                        callback.onDownloaded(postImage);
                    }

                    @Override
                    public void onNotFound() {
                        BackgroundUtils.ensureMainThread();

                        onNotFoundError();
                    }

                    @Override
                    public void onFail(Exception exception) {
                        BackgroundUtils.ensureMainThread();

                        onError(exception);
                    }

                    @Override
                    public void onEnd() {
                        BackgroundUtils.ensureMainThread();

                        bigImageRequest = null;
                        callback.hideProgress(MultiImageView.this);
                    }
                });
    }

    private void setGif(Loadable loadable, PostImage postImage) {
        BackgroundUtils.ensureMainThread();

        if (gifRequest != null) {
            return;
        }

        DownloadRequestExtraInfo extraInfo = new DownloadRequestExtraInfo(postImage.size, postImage.fileHash);

        gifRequest =
                fileCacheV2.enqueueChunkedDownloadFileRequest(loadable, postImage, extraInfo, new FileCacheListener() {

                    @Override
                    public void onStart(int chunksCount) {
                        BackgroundUtils.ensureMainThread();

                        callback.onStartDownload(MultiImageView.this, chunksCount);
                    }

                    @Override
                    public void onProgress(int chunkIndex, long downloaded, long total) {
                        BackgroundUtils.ensureMainThread();

                        callback.onProgress(MultiImageView.this, chunkIndex, downloaded, total);
                    }

                    @Override
                    public void onSuccess(RawFile file) {
                        BackgroundUtils.ensureMainThread();

                        if (!hasContent || mode == Mode.GIFIMAGE) {
                            setGifFile(new File(file.getFullPath()));
                        }

                        callback.onDownloaded(postImage);
                    }

                    @Override
                    public void onNotFound() {
                        BackgroundUtils.ensureMainThread();

                        onNotFoundError();
                    }

                    @Override
                    public void onFail(Exception exception) {
                        BackgroundUtils.ensureMainThread();

                        onError(exception);
                    }

                    @Override
                    public void onEnd() {
                        BackgroundUtils.ensureMainThread();

                        gifRequest = null;
                        callback.hideProgress(MultiImageView.this);
                    }
                });
    }

    private void setGifFile(File file) {
        GifDrawable drawable;
        try {
            drawable = new GifDrawable(file.getAbsolutePath());

            // For single frame gifs, use the scaling image instead
            // The region decoder doesn't work for gifs, so we unfortunately
            // have to use the more memory intensive non tiling mode.
            if (drawable.getNumberOfFrames() == 1) {
                drawable.recycle();
                setBitImageFileInternal(file, false);
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
        toggleTransparency();
    }

    private void setVideo(Loadable loadable, PostImage postImage) {
        BackgroundUtils.ensureMainThread();

        if (ChanSettings.videoStream.get()) {
            openVideoInternalStream(loadable, postImage);
        } else {
            openVideoExternal(loadable, postImage);
        }
    }

    private void openVideoInternalStream(Loadable loadable, PostImage postImage) {
        webmStreamingSource.createMediaSource(loadable, postImage, new MediaSourceCallback() {
            @Override
            public void onMediaSourceReady(@Nullable MediaSource source) {
                BackgroundUtils.ensureMainThread();

                if (source == null) {
                    onError(new IllegalArgumentException("Source is null"));
                    return;
                }

                synchronized (MultiImageView.this) {
                    if (mediaSourceCancel) {
                        return;
                    }

                    if (!hasContent || mode == Mode.VIDEO) {
                        PlayerView exoVideoView = new PlayerView(getContext());
                        exoPlayer = new SimpleExoPlayer.Builder(getContext()).build();
                        exoVideoView.setPlayer(exoPlayer);

                        exoPlayer.setRepeatMode(ChanSettings.videoAutoLoop.get()
                                ? Player.REPEAT_MODE_ALL
                                : Player.REPEAT_MODE_OFF);

                        exoPlayer.prepare(source);
                        exoPlayer.setVolume(0f);
                        exoPlayer.addAudioListener(MultiImageView.this);
                        exoVideoView.setOnClickListener(null);
                        exoVideoView.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
                        exoVideoView.setUseController(false);
                        exoVideoView.setControllerHideOnTouch(false);
                        exoVideoView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
                        exoVideoView.setUseArtwork(true);
                        exoVideoView.setDefaultArtwork(getAppContext().getDrawable(R.drawable.ic_volume_up_white_24dp));
                        exoPlayer.setVolume(getDefaultMuteState() ? 0 : 1);
                        exoPlayer.setPlayWhenReady(true);
                        onModeLoaded(Mode.VIDEO, exoVideoView);
                        callback.onVideoLoaded(MultiImageView.this);
                        callback.onDownloaded(postImage);
                    }
                }
            }

            @Override
            public void onError(@NotNull Throwable error) {
                BackgroundUtils.ensureMainThread();

                Logger.e(this, "Error while trying to stream a webm", error);
                showToast(getContext(), "Couldn't open webm in streaming mode, error = " + error.getMessage());
            }
        });
    }

    private void openVideoExternal(Loadable loadable, PostImage postImage) {
        BackgroundUtils.ensureMainThread();

        if (videoRequest != null) {
            return;
        }

        DownloadRequestExtraInfo extraInfo = new DownloadRequestExtraInfo(postImage.size, postImage.fileHash);

        videoRequest =
                fileCacheV2.enqueueChunkedDownloadFileRequest(loadable, postImage, extraInfo, new FileCacheListener() {

                    @Override
                    public void onStart(int chunksCount) {
                        BackgroundUtils.ensureMainThread();

                        callback.onStartDownload(MultiImageView.this, chunksCount);
                    }

                    @Override
                    public void onProgress(int chunkIndex, long downloaded, long total) {
                        BackgroundUtils.ensureMainThread();

                        callback.onProgress(MultiImageView.this, chunkIndex, downloaded, total);
                    }

                    @Override
                    public void onSuccess(RawFile file) {
                        BackgroundUtils.ensureMainThread();

                        if (!hasContent || mode == Mode.VIDEO) {
                            setVideoFile(new File(file.getFullPath()));
                        }

                        callback.onDownloaded(postImage);
                    }

                    @Override
                    public void onNotFound() {
                        BackgroundUtils.ensureMainThread();

                        onNotFoundError();
                    }

                    @Override
                    public void onFail(Exception exception) {
                        BackgroundUtils.ensureMainThread();

                        onError(exception);
                    }

                    @Override
                    public void onEnd() {
                        BackgroundUtils.ensureMainThread();

                        videoRequest = null;
                        callback.hideProgress(MultiImageView.this);
                    }
                });
    }

    private void setVideoFile(final File file) {
        if (ChanSettings.videoOpenExternal.get()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);

            Uri uriForFile = FileProvider.getUriForFile(getAppContext(), getAppFileProvider(), file);

            intent.setDataAndType(uriForFile, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            openIntent(intent);

            onModeLoaded(Mode.VIDEO, null);
        } else {
            PlayerView exoVideoView = new PlayerView(getContext());
            exoPlayer = new SimpleExoPlayer.Builder(getContext()).build();
            exoVideoView.setPlayer(exoPlayer);
            String userAgent = Util.getUserAgent(getAppContext(), NetModule.USER_AGENT);
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getContext(), userAgent);
            ProgressiveMediaSource.Factory progressiveFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
            MediaSource videoSource = progressiveFactory.createMediaSource(Uri.fromFile(file));

            exoPlayer.setRepeatMode(ChanSettings.videoAutoLoop.get() ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);

            exoPlayer.prepare(videoSource);
            exoPlayer.addAudioListener(this);
            exoVideoView.setOnClickListener(null);
            exoVideoView.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
            exoVideoView.setUseController(false);
            exoVideoView.setControllerHideOnTouch(false);
            exoVideoView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
            exoVideoView.setUseArtwork(true);
            exoVideoView.setDefaultArtwork(getAppContext().getDrawable(R.drawable.ic_volume_up_white_24dp));
            exoPlayer.setVolume(getDefaultMuteState() ? 0 : 1);
            exoPlayer.setPlayWhenReady(true);
            onModeLoaded(Mode.VIDEO, exoVideoView);
            callback.onVideoLoaded(this);
        }
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        if (exoPlayer != null && exoPlayer.getAudioFormat() != null) {
            callback.onAudioLoaded(this);
        }
    }

    private void setOther(Loadable loadable, PostImage image) {
        if (image.type == PostImage.Type.PDF) {
            cancellableToast.showToast(getContext(), R.string.pdf_not_viewable);
            //this lets the user download the PDF, even though we haven't actually downloaded anything
            callback.onDownloaded(image);
        } else if (image.type == PostImage.Type.SWF) {
            cancellableToast.showToast(getContext(), R.string.swf_not_viewable);
            callback.onDownloaded(image);
        }
    }

    public void toggleTransparency() {
        transparentBackground = !transparentBackground;
        // these colors are specific to 4chan for the time being
        final int BACKGROUND_COLOR_SFW = Color.argb(255, 214, 218, 240);
        final int BACKGROUND_COLOR_SFW_OP = Color.argb(255, 238, 242, 255);
        final int BACKGROUND_COLOR_NSFW = Color.argb(255, 240, 224, 214);
        final int BACKGROUND_COLOR_NSFW_OP = Color.argb(255, 255, 255, 238);
        int boardColor = callback.getLoadable().board.workSafe
                ? (op ? BACKGROUND_COLOR_SFW_OP : BACKGROUND_COLOR_SFW)
                : (op ? BACKGROUND_COLOR_NSFW_OP : BACKGROUND_COLOR_NSFW);
        View activeView = getActiveView();
        if (!(activeView instanceof CustomScaleImageView || activeView instanceof GifImageView)) return;
        boolean isImage = activeView instanceof CustomScaleImageView;
        int backgroundColor = !transparentBackground ? Color.TRANSPARENT : boardColor;
        if (isImage) {
            ((CustomScaleImageView) activeView).setTileBackgroundColor(backgroundColor);
        } else {
            ((GifImageView) activeView).getDrawable().setColorFilter(backgroundColor, PorterDuff.Mode.DST_OVER);
        }
    }

    public void rotateImage(int degrees) {
        View activeView = getActiveView();
        if (!(activeView instanceof CustomScaleImageView)) return;
        CustomScaleImageView imageView = (CustomScaleImageView) activeView;
        if (degrees % 90 != 0 && degrees >= -90 && degrees <= 180)
            throw new IllegalArgumentException("Degrees must be a multiple of 90 and in the range -90 < deg < 180");
        //swap the current scale to the opposite one every 90 degree increment
        //0 degrees is X scale, 90 is Y, 180 is X, 270 is Y
        float curScale = imageView.getScale();
        float scaleX = imageView.getWidth() / (float) imageView.getSWidth();
        float scaleY = imageView.getHeight() / (float) imageView.getSHeight();
        imageView.setScaleAndCenter(curScale == scaleX ? scaleY : scaleX, imageView.getCenter());
        //apply the rotation through orientation rather than rotation, as
        //orientation is internal to the subsamplingimageview's internal bitmap while rotation is on the entire view
        switch (imageView.getAppliedOrientation()) {
            case SubsamplingScaleImageView.ORIENTATION_0:
                //rotate from 0 (0 is 0, 90 is 90, 180 is 180, -90 is 270)
                imageView.setOrientation(degrees >= 0 ? degrees : 360 + degrees);
                break;
            case SubsamplingScaleImageView.ORIENTATION_90:
                //rotate from 90 (0 is 90, 90 is 180, 180 is 270, -90 is 0)
                imageView.setOrientation(90 + degrees);
                break;
            case SubsamplingScaleImageView.ORIENTATION_180:
                //rotate from 180 (0 is 180, 90 is 270, 180 is 0, -90 is 90)
                imageView.setOrientation(degrees == 180 ? 0 : 180 + degrees);
                break;
            case SubsamplingScaleImageView.ORIENTATION_270:
                //rotate from 270 (0 is 270, 90 is 0, 180 is 90, -90 is 180)
                imageView.setOrientation(degrees >= 90 ? degrees - 90 : 270 + degrees);
                break;
        }
    }

    private void setBitImageFileInternal(File file, boolean tiling) {
        final CustomScaleImageView image = new CustomScaleImageView(getContext());
        image.setImage(ImageSource.uri(file.getAbsolutePath()).tiling(tiling));
        //this is required because unlike the other views, if we don't have layout dimensions, the callback won't be called
        //see https://github.com/davemorrissey/subsampling-scale-image-view/issues/143
        addView(image, 0, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        image.setCallback(new CustomScaleImageView.Callback() {
            @Override
            public void onReady() {
                if (!hasContent || mode == Mode.BIGIMAGE) {
                    callback.hideProgress(MultiImageView.this);
                    onModeLoaded(Mode.BIGIMAGE, image);
                    toggleTransparency();
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

    private void onError(Exception exception) {
        String message = String.format(Locale.ENGLISH,
                "%s: %s",
                getString(R.string.image_preview_failed),
                exception.getMessage()
        );

        cancellableToast.showToast(getContext(), message);
        callback.hideProgress(MultiImageView.this);
    }

    private void onNotFoundError() {
        cancellableToast.showToast(getContext(), R.string.image_not_found);
        callback.hideProgress(MultiImageView.this);
    }

    private void onOutOfMemoryError() {
        cancellableToast.showToast(getContext(), R.string.image_preview_failed_oom);
        callback.hideProgress(MultiImageView.this);
    }

    private void onBigImageError(boolean wasInitial) {
        if (wasInitial) {
            cancellableToast.showToast(getContext(), R.string.image_failed_big_image);
            callback.hideProgress(MultiImageView.this);
        }
    }

    private void cancelLoad() {
        if (thumbnailRequest != null) {
            thumbnailRequest.cancel();
            thumbnailRequest = null;
        }
        if (bigImageRequest != null) {
            bigImageRequest.cancel();
            bigImageRequest = null;
        }
        if (gifRequest != null) {
            gifRequest.cancel();
            gifRequest = null;
        }
        if (videoRequest != null) {
            videoRequest.cancel();
            videoRequest = null;
        }

        synchronized (this) {
            mediaSourceCancel = true;
        }

        if (exoPlayer != null) {
            // ExoPlayer will keep loading resources if we don't release it here.
            releaseStreamCallbacks();
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    private void onModeLoaded(Mode mode, View view) {
        if (view != null) {
            // Remove all other views
            boolean alreadyAttached = false;
            for (int i = getChildCount() - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child != view) {
                    if (child instanceof PlayerView) {
                        releaseStreamCallbacks();
                        ((PlayerView) child).getPlayer().release();
                    }
                    removeViewAt(i);
                } else {
                    alreadyAttached = true;
                }
            }

            if (!alreadyAttached) {
                addView(view, 0, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
                if (view instanceof PlayerView) {
                    addView(exoClickHandler);
                }
            }
        }

        hasContent = true;
        callback.onModeLoaded(this, mode);
    }

    private void releaseStreamCallbacks() {
        if (ChanSettings.videoStream.get()) {
            try {
                Field mediaSource = exoPlayer.getClass().getDeclaredField("mediaSource");
                mediaSource.setAccessible(true);
                if (mediaSource.get(exoPlayer) != null) {
                    ProgressiveMediaSource source = (ProgressiveMediaSource) mediaSource.get(exoPlayer);
                    Field dataSource = source.getClass().getDeclaredField("dataSourceFactory");
                    dataSource.setAccessible(true);
                    DataSource.Factory factory = (DataSource.Factory) dataSource.get(source);
                    ((WebmStreamingDataSource) factory.createDataSource()).clearListeners();
                    dataSource.setAccessible(false);
                }
                mediaSource.setAccessible(false);
            } catch (Exception ignored) {
                // data source likely is from a file rather than a stream, ignore any exceptions
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child instanceof GifImageView) {
            GifImageView gif = (GifImageView) child;
            if (gif.getDrawable() instanceof GifDrawable) {
                GifDrawable drawable = (GifDrawable) gif.getDrawable();
                if (drawable.getFrameByteCount() > 100 * 1024 * 1024) { // max size from RecordingCanvas
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

        void onStartDownload(MultiImageView multiImageView, int chunksCount);

        void onProgress(MultiImageView multiImageView, int chunkIndex, long current, long total);

        void onDownloaded(PostImage postImage);

        void onVideoLoaded(MultiImageView multiImageView);

        void onModeLoaded(MultiImageView multiImageView, Mode mode);

        void onAudioLoaded(MultiImageView multiImageView);

        void hideProgress(MultiImageView multiImageView);

        Loadable getLoadable();
    }
}
