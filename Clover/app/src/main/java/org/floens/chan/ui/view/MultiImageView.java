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
package org.floens.chan.ui.view;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.FileCache;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class MultiImageView extends FrameLayout implements View.OnClickListener {
    public enum Mode {
        UNLOADED, LOWRES, BIGIMAGE
    }

    private static final String TAG = "MultiImageView";

    private PostImage postImage;
    private Callback callback;

    private Mode mode = Mode.UNLOADED;

    private boolean hasContent = false;
    private ImageContainer thumbnailRequest;
    private Future bigImageRequest;
    private Future gifRequest;
    private Future videoRequest;

    private VideoView videoView;

    public MultiImageView(Context context) {
        super(context);
        init();
    }

    public MultiImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MultiImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnClickListener(this);
    }

    public void bindPostImage(PostImage postImage, Callback callback) {
        this.postImage = postImage;
        this.callback = callback;
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setMode(Mode mode) {
        if (this.mode != mode) {
            final Mode previousTargetMode = this.mode;
            this.mode = mode;
            Logger.d(TAG, "Changing mode from " + previousTargetMode + "to " + mode + " for " + postImage.thumbnailUrl);
            if (mode == Mode.LOWRES) {
                AndroidUtils.waitForMeasure(this, new AndroidUtils.OnMeasuredCallback() {
                    @Override
                    public boolean onMeasured(View view) {
                        setThumbnail(postImage.thumbnailUrl);
                        return false;
                    }
                });
            } else if (mode == Mode.BIGIMAGE) {
                if (postImage.type == PostImage.Type.STATIC) {
                    AndroidUtils.waitForMeasure(this, new AndroidUtils.OnMeasuredCallback() {
                        @Override
                        public boolean onMeasured(View view) {
                            setBigImage(postImage.imageUrl);
                            return false;
                        }
                    });
                } else {
                    Logger.e(TAG, "postImage type not STATIC, not changing to BIGIMAGE mode!");
                }
            }
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setThumbnail(String thumbnailUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        // Also use volley for the thumbnails
        thumbnailRequest = ChanApplication.getVolleyImageLoader().get(thumbnailUrl, new com.android.volley.toolbox.ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                thumbnailRequest = null;
                onError();
            }

            @Override
            public void onResponse(ImageContainer response, boolean isImmediate) {
                thumbnailRequest = null;
                if (response.getBitmap() != null && (!hasContent || mode == Mode.LOWRES)) {
                    ImageView thumbnail = new ImageView(getContext());
                    thumbnail.setImageBitmap(response.getBitmap());
                    onModeLoaded(Mode.LOWRES, thumbnail);
                }
            }
        }, getWidth(), getHeight());
    }

    public void setBigImage(String imageUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading big image");
            return;
        }

        callback.setProgress(this, true);
        bigImageRequest = ChanApplication.getFileCache().downloadFile(imageUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
//                    callback.setLinearProgress(0, 0, true);
                } else {
                    callback.setLinearProgress(MultiImageView.this, downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                bigImageRequest = null;
                setBigImageFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                bigImageRequest = null;
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setBigImageFile(File file) {
        final CustomScaleImageView image = new CustomScaleImageView(getContext());
        image.setImageFile(file.getAbsolutePath());
        image.setOnClickListener(MultiImageView.this);

        addView(image);

        image.setInitCallback(new CustomScaleImageView.InitedCallback() {
            @Override
            public void onInit() {
                if (!hasContent || mode == Mode.BIGIMAGE) {
                    callback.setProgress(MultiImageView.this, false);
                    onModeLoaded(Mode.BIGIMAGE, image);
                }
            }

            @Override
            public void onOutOfMemory() {
                onOutOfMemoryError();
            }
        });
    }

    public void setGif(String gifUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        callback.setProgress(this, true);
        gifRequest = ChanApplication.getFileCache().downloadFile(gifUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
                    callback.setProgress(MultiImageView.this, false);
                    callback.setLinearProgress(MultiImageView.this, 0, 0, true);
                } else {
                    callback.setLinearProgress(MultiImageView.this, downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                gifRequest = null;
                setGifFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                gifRequest = null;
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setGifFile(File file) {
        GifDrawable drawable;
        try {
            drawable = new GifDrawable(file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            onError();
            return;
        } catch (OutOfMemoryError e) {
            System.gc();
            e.printStackTrace();
            onOutOfMemoryError();
            return;
        }

        GifImageView view = new GifImageView(getContext());
        view.setImageDrawable(drawable);
        view.setLayoutParams(AndroidUtils.MATCH_PARAMS);
        setView(view);
    }

    public void setVideo(String videoUrl) {
        callback.setProgress(this, true);
        videoRequest = ChanApplication.getFileCache().downloadFile(videoUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
                    callback.setProgress(MultiImageView.this, false);
                    callback.setLinearProgress(MultiImageView.this, 0, 0, true);
                } else {
                    callback.setLinearProgress(MultiImageView.this, downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                videoRequest = null;
                setVideoFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                videoRequest = null;
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setVideoFile(final File file) {
        if (ChanSettings.getVideoExternal()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), "video/*");

            try {
                getContext().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(), R.string.open_link_failed, Toast.LENGTH_SHORT).show();
            }
        } else {
            Context proxyContext = new NoMusicServiceCommandContext(getContext());

            videoView = new VideoView(proxyContext);
            videoView.setZOrderOnTop(true);
            videoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            videoView.setLayoutParams(AndroidUtils.MATCH_PARAMS);
            LayoutParams par = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            par.gravity = Gravity.CENTER;
            videoView.setLayoutParams(par);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                    callback.onVideoLoaded(MultiImageView.this);
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    callback.onVideoError(MultiImageView.this, file);

                    return true;
                }
            });

            videoView.setVideoPath(file.getAbsolutePath());

            setView(videoView);

            videoView.start();
        }
    }

    public VideoView getVideoView() {
        return videoView;
    }

    public void onError() {
        Toast.makeText(getContext(), R.string.image_preview_failed, Toast.LENGTH_SHORT).show();
        callback.setProgress(this, false);
    }

    public void onNotFoundError() {
        callback.setProgress(this, false);
        Toast.makeText(getContext(), R.string.image_not_found, Toast.LENGTH_SHORT).show();
    }

    public void onOutOfMemoryError() {
        Toast.makeText(getContext(), R.string.image_preview_failed_oom, Toast.LENGTH_SHORT).show();
        callback.setProgress(this, false);
    }

    public void cancelLoad() {
        if (bigImageRequest != null) {
            bigImageRequest.cancel(true);
        }
        if (gifRequest != null) {
            gifRequest.cancel(true);
        }
        if (videoRequest != null) {
            videoRequest.cancel(true);
        }
    }

    @Override
    public void onClick(View v) {
        callback.onTap(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLoad();
    }

    private void setView(View view) {
        removeAllViews();
        addView(view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private void onModeLoaded(Mode mode, View view) {
        removeAllViews();
        addView(view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        hasContent = true;
        callback.onModeLoaded(this, mode);
    }

    public static interface Callback {
        public void onTap(MultiImageView multiImageView);

        public void setProgress(MultiImageView multiImageView, boolean progress);

        public void setLinearProgress(MultiImageView multiImageView, long current, long total, boolean done);

        public void onVideoLoaded(MultiImageView multiImageView);

        public void onVideoError(MultiImageView multiImageView, File video);

        public void onModeLoaded(MultiImageView multiImageView, Mode mode);
    }

    public static class NoMusicServiceCommandContext extends ContextWrapper {
        public NoMusicServiceCommandContext(Context base) {
            super(base);
        }

        @Override
        public void sendBroadcast(Intent intent) {
            // Only allow broadcasts when it's not a music service command
            // Prevents pause intents from broadcasting
            if (!"com.android.music.musicservicecommand".equals(intent.getAction())) {
                super.sendBroadcast(intent);
            }
        }
    }
}
