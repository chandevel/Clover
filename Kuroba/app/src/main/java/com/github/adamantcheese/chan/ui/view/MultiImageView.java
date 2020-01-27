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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.content.FileProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.MediaSourceCallback;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.cache.downloader.DownloadRequestExtraInfo;
import com.github.adamantcheese.chan.core.cache.stream.WebmStreamingSource;
import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.image.ImageLoaderV2;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.widget.CancellableToast;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.file.RawFile;
import com.google.android.exoplayer2.ExoPlayerFactory;
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

import javax.inject.Inject;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppFileProvider;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForMeasure;

public class MultiImageView
        extends FrameLayout
        implements View.OnClickListener, AudioListener, LifecycleObserver {
    public enum Mode {
        UNLOADED,
        LOWRES,
        BIGIMAGE,
        GIFIMAGE,
        VIDEO,
        OTHER
    }

    private static final String TAG = "MultiImageView";

    @Inject
    FileCacheV2 fileCacheV2;
    @Inject
    WebmStreamingSource webmStreamingSource;
    @Inject
    ImageLoaderV2 imageLoaderV2;

    @Nullable
    private Context context;
    private ImageView playView;
    private GestureDetector exoDoubleTapDetector;
    private GestureDetector gifDoubleTapDetector;

    private PostImage postImage;
    private Callback callback;
    private Mode mode = Mode.UNLOADED;

    private ImageContainer thumbnailRequest;
    private CancelableDownload bigImageRequest;
    private CancelableDownload gifRequest;
    private CancelableDownload videoRequest;
    private SimpleExoPlayer exoPlayer;
    private CancellableToast cancellableToast;

    private boolean hasContent = false;
    private boolean mediaSourceCancel = false;
    private boolean backgroundToggle;

    public MultiImageView(Context context) {
        this(context, null);
    }

    public MultiImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MultiImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;
        this.cancellableToast = new CancellableToast();

        inject(this);
        setOnClickListener(this);

        playView = new ImageView(getContext());
        playView.setVisibility(GONE);
        playView.setImageResource(R.drawable.ic_play_circle_outline_white_48dp);
        addView(playView, new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER));

        if (context instanceof StartActivity) {
            ((StartActivity) context).getLifecycle().addObserver(this);
        }

        exoDoubleTapDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                callback.onDoubleTap();
                return true;
            }
        });

        gifDoubleTapDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                GifDrawable drawable = (GifDrawable) findGifImageView().getDrawable();
                if (drawable.isPlaying()) {
                    drawable.pause();
                } else {
                    drawable.start();
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                callback.onTap();
                return true;
            }
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }

    public void bindPostImage(PostImage postImage, Callback callback) {
        this.postImage = postImage;
        this.callback = callback;

        playView.setVisibility(postImage.type == PostImage.Type.MOVIE ? VISIBLE : GONE);
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setMode(Loadable loadable, final Mode newMode, boolean center) {
        this.mode = newMode;
        waitForMeasure(this, view -> {
            switch (newMode) {
                case LOWRES:
                    setThumbnail(loadable, postImage, center);
                    backgroundToggle = false;
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

    public CustomScaleImageView findScaleImageView() {
        CustomScaleImageView bigImage = null;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof CustomScaleImageView) {
                bigImage = (CustomScaleImageView) getChildAt(i);
            }
        }
        return bigImage;
    }

    public GifImageView findGifImageView() {
        GifImageView gif = null;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof GifImageView) {
                gif = (GifImageView) getChildAt(i);
            }
        }
        return gif;
    }

    public void setVolume(boolean muted) {
        final float volume = muted ? 0f : 1f;
        if (exoPlayer != null) {
            Player.AudioComponent audioComponent = exoPlayer.getAudioComponent();
            if (audioComponent != null) {
                audioComponent.setVolume(volume);
            }
        }
    }

    @Override
    public void onClick(View v) {
        callback.onTap();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLoad();

        if (context != null && context instanceof StartActivity) {
            ((StartActivity) context).getLifecycle().removeObserver(this);
        }

        context = null;
    }

    private void setThumbnail(Loadable loadable, PostImage postImage, boolean center) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        if (thumbnailRequest != null) {
            return;
        }

        thumbnailRequest =
                imageLoaderV2.getImage(true, loadable, postImage, getWidth(), getHeight(), new ImageListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        thumbnailRequest = null;
                        if (center) {
                            onError(error);
                        }
                    }

                    @Override
                    public void onResponse(
                            ImageContainer response, boolean isImmediate
                    ) {
                        thumbnailRequest = null;

                        if (response.getBitmap() != null && (!hasContent || mode == Mode.LOWRES)) {
                            ImageView thumbnail = new ImageView(getContext());
                            thumbnail.setImageBitmap(response.getBitmap());

                            onModeLoaded(Mode.LOWRES, thumbnail);
                        }
                    }
                });

        if (thumbnailRequest != null && thumbnailRequest.getBitmap() != null) {
            // Request was immediate and thumbnailRequest was first set to null in onResponse, and then set to the container
            // when the method returned
            // Still set it to null here
            thumbnailRequest = null;
        }
    }

    private void setBigImage(Loadable loadable, PostImage postImage) {
        BackgroundUtils.ensureMainThread();

        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading big image");
            return;
        }

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

                        setBitImageFileInternal(new File(file.getFullPath()), true, Mode.BIGIMAGE);
                        if (!ChanSettings.transparencyOn.get() && !backgroundToggle) {
                            toggleTransparency();
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

                        bigImageRequest = null;
                        callback.hideProgress(MultiImageView.this);
                    }
                });
    }

    private void setGif(Loadable loadable, PostImage postImage) {
        BackgroundUtils.ensureMainThread();

        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

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
                            if (!ChanSettings.transparencyOn.get() && !backgroundToggle) {
                                toggleTransparency();
                            }
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
                setBitImageFileInternal(file, false, Mode.GIFIMAGE);
                return;
            }
        } catch (IOException e) {
            Logger.e(TAG, "Error while trying to set a gif file", e);
            onError(e);
            return;
        } catch (OutOfMemoryError e) {
            Runtime.getRuntime().gc();
            Logger.e(TAG, "OOM while trying to set a gif file", e);
            onOutOfMemoryError();
            return;
        }

        GifImageView view = new GifImageView(getContext());
        view.setImageDrawable(drawable);
        view.setOnClickListener(null);
        view.setOnTouchListener((view1, motionEvent) -> gifDoubleTapDetector.onTouchEvent(motionEvent));
        onModeLoaded(Mode.GIFIMAGE, view);
    }

    private void setVideo(Loadable loadable, PostImage postImage) {
        BackgroundUtils.ensureMainThread();
        if (ChanSettings.videoStream.get()) {
            openVideoInternalStream(postImage.imageUrl.toString());
        } else {
            openVideoExternal(loadable, postImage);
        }
    }

    private void openVideoInternalStream(String videoUrl) {
        webmStreamingSource.createMediaSource(videoUrl, new MediaSourceCallback() {
            @Override
            public void onMediaSourceReady(@Nullable MediaSource source) {
                BackgroundUtils.ensureMainThread();

                synchronized (MultiImageView.this) {
                    if (mediaSourceCancel) {
                        return;
                    }

                    if (!hasContent || mode == Mode.VIDEO) {
                        PlayerView exoVideoView = new PlayerView(getContext());
                        exoPlayer = ExoPlayerFactory.newSimpleInstance(getContext());
                        exoVideoView.setPlayer(exoPlayer);

                        exoPlayer.setRepeatMode(ChanSettings.videoAutoLoop.get()
                                ? Player.REPEAT_MODE_ALL
                                : Player.REPEAT_MODE_OFF);

                        exoPlayer.prepare(source);
                        exoPlayer.setVolume(0f);
                        exoPlayer.addAudioListener(MultiImageView.this);

                        addView(exoVideoView);
                        exoPlayer.setPlayWhenReady(true);
                        onModeLoaded(Mode.VIDEO, exoVideoView);
                        callback.onVideoLoaded(MultiImageView.this);
                        callback.onDownloaded(postImage);
                    }
                }
            }

            @Override
            public void onError(@NotNull Throwable error) {
                Logger.e(TAG, "Error while trying to stream a webm", error);
                showToast("Couldn't open webm in streaming mode, error = " + error.getMessage());
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
            exoPlayer = ExoPlayerFactory.newSimpleInstance(getContext());
            exoVideoView.setPlayer(exoPlayer);
            String userAgent = Util.getUserAgent(getAppContext(), NetModule.USER_AGENT);
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getContext(), userAgent);
            ProgressiveMediaSource.Factory progressiveFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
            MediaSource videoSource = progressiveFactory.createMediaSource(Uri.fromFile(file));

            exoPlayer.setRepeatMode(ChanSettings.videoAutoLoop.get() ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);

            exoPlayer.prepare(videoSource);
            exoPlayer.addAudioListener(this);
            exoVideoView.setOnTouchListener((view, motionEvent) -> exoDoubleTapDetector.onTouchEvent(motionEvent));

            addView(exoVideoView);
            exoPlayer.setPlayWhenReady(true);
            onModeLoaded(Mode.VIDEO, exoVideoView);
            callback.onVideoLoaded(this);
        }
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        if (exoPlayer.getAudioFormat() != null) {
            callback.onAudioLoaded(this);
        }
    }

    private void setOther(Loadable loadable, PostImage image) {
        if (image.type == PostImage.Type.PDF) {
            cancellableToast.showToast(R.string.pdf_not_viewable);
        }
    }

    public void toggleTransparency() {
        final int BACKGROUND_COLOR = Color.argb(255, 211, 217, 241);
        CustomScaleImageView imageView = findScaleImageView();
        GifImageView gifView = findGifImageView();
        if (imageView == null && gifView == null) return;
        boolean isImage = imageView != null && gifView == null;
        int backgroundColor = backgroundToggle ? Color.TRANSPARENT : BACKGROUND_COLOR;
        if (isImage) {
            imageView.setTileBackgroundColor(backgroundColor);
        } else {
            gifView.getDrawable().setColorFilter(backgroundColor, PorterDuff.Mode.DST_OVER);
        }
        backgroundToggle = !backgroundToggle;
    }

    public void rotateImage(int degrees) {
        CustomScaleImageView imageView = findScaleImageView();
        if (imageView == null) return;
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

    private void setBitImageFileInternal(File file, boolean tiling, final Mode forMode) {
        final CustomScaleImageView image = new CustomScaleImageView(getContext());
        image.setImage(ImageSource.uri(file.getAbsolutePath()).tiling(tiling));
        image.setOnClickListener(MultiImageView.this);
        addView(image, 0, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        image.setCallback(new CustomScaleImageView.Callback() {
            @Override
            public void onReady() {
                if (!hasContent || mode == forMode) {
                    callback.hideProgress(MultiImageView.this);
                    onModeLoaded(Mode.BIGIMAGE, image);
                }
            }

            @Override
            public void onError(boolean wasInitial) {
                onBigImageError(wasInitial);
            }
        });
    }

    private void onError(Exception exception) {
        if (context != null) {
            String reason = exception.getMessage();
            if (reason == null) {
                reason = "Unknown reason";
            }

            String message = String.format("%s, reason: %s", context.getString(R.string.image_preview_failed), reason);

            cancellableToast.showToast(message);
            callback.hideProgress(MultiImageView.this);
        }
    }

    private void onNotFoundError() {
        cancellableToast.showToast(R.string.image_not_found);
        callback.hideProgress(MultiImageView.this);
    }

    private void onOutOfMemoryError() {
        cancellableToast.showToast(R.string.image_preview_failed_oom);
        callback.hideProgress(MultiImageView.this);
    }

    private void onBigImageError(boolean wasInitial) {
        if (wasInitial) {
            cancellableToast.showToast(R.string.image_failed_big_image);
            callback.hideProgress(MultiImageView.this);
        }
    }

    private void cancelLoad() {
        if (thumbnailRequest != null) {
            imageLoaderV2.cancelRequest(thumbnailRequest);
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
                if (child != playView) {
                    if (child != view) {
                        if (child instanceof PlayerView) {
                            ((PlayerView) child).getPlayer().release();
                        }
                        removeViewAt(i);
                    } else {
                        alreadyAttached = true;
                    }
                }
            }

            if (!alreadyAttached) {
                addView(view, 0, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
            }
        }

        hasContent = true;
        callback.onModeLoaded(this, mode);
    }

    public interface Callback {
        void onTap();

        void onDoubleTap();

        void onStartDownload(MultiImageView multiImageView, int chunksCount);

        void onProgress(MultiImageView multiImageView, int chunkIndex, long current, long total);

        void onDownloaded(PostImage postImage);

        void onVideoLoaded(MultiImageView multiImageView);

        void onModeLoaded(MultiImageView multiImageView, Mode mode);

        void onAudioLoaded(MultiImageView multiImageView);

        void hideProgress(MultiImageView multiImageView);
    }
}
