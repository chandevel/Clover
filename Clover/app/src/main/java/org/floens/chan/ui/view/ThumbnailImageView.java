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
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.koushikdutta.async.future.Future;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.utils.FileCache;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Utils;

import java.io.File;
import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class ThumbnailImageView extends LoadView implements View.OnClickListener {
    private static final String TAG = "ThumbnailImageView";

    private ThumbnailImageViewCallback callback;

    /**
     * Max amount to scale the image inside the view
     */
    private final float maxScale = 3f;

    private boolean thumbnailNeeded = true;

    private Request<?> imageRequest;
    private Future<?> ionRequest;
    private VideoView videoView;
    private GifDrawable gifDrawable;

    public ThumbnailImageView(Context context) {
        super(context);
        init();
    }

    public ThumbnailImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThumbnailImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnClickListener(this);
    }

    public void setCallback(ThumbnailImageViewCallback callback) {
        this.callback = callback;
    }


    public void setThumbnail(String thumbnailUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        // Also use volley for the thumbnails
        ChanApplication.getVolleyImageLoader().get(thumbnailUrl, new com.android.volley.toolbox.ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                onError();
            }

            @Override
            public void onResponse(ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null && thumbnailNeeded) {
                    ImageView thumbnail = new ImageView(getContext());
                    thumbnail.setImageBitmap(response.getBitmap());
                    thumbnail.setLayoutParams(Utils.MATCH_PARAMS);
                    setView(thumbnail, false);
                }
            }
        }, getWidth(), getHeight());
    }

    public void setBigImage(String imageUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        callback.setProgress(true);
        ionRequest = ChanApplication.getFileCache().downloadFile(getContext(), imageUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
                    callback.setLinearProgress(0, 0, true);
                    thumbnailNeeded = false;
                } else {
                    callback.setLinearProgress(downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                setBigImageFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
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
        image.setOnClickListener(ThumbnailImageView.this);

        addView(image);

        image.setInitCallback(new CustomScaleImageView.InitedCallback() {
            @Override
            public void onInit() {
                removeAllViews();
                addView(image);
                callback.setProgress(false);
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

        callback.setProgress(true);
        ionRequest = ChanApplication.getFileCache().downloadFile(getContext(), gifUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
                    callback.setProgress(false);
                    callback.setLinearProgress(0, 0, true);
                    thumbnailNeeded = false;
                } else {
                    callback.setLinearProgress(downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                setGifFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
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
        view.setLayoutParams(Utils.MATCH_PARAMS);
        setView(view, false);
    }

    public void setVideo(String videoUrl) {
        callback.setProgress(true);
        ionRequest = ChanApplication.getFileCache().downloadFile(getContext(), videoUrl, new FileCache.DownloadedCallback() {
            @Override
            public void onProgress(long downloaded, long total, boolean done) {
                if (done) {
                    callback.setProgress(false);
                    callback.setLinearProgress(0, 0, true);
                    thumbnailNeeded = false;
                } else {
                    callback.setLinearProgress(downloaded, total, false);
                }
            }

            @Override
            public void onSuccess(File file) {
                setVideoFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError();
                }
            }
        });
    }

    public void setVideoFile(final File file) {
        if (ChanPreferences.getVideoExternal()) {
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
            videoView.setLayoutParams(Utils.MATCH_PARAMS);
            LayoutParams par = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            par.gravity = Gravity.CENTER;
            videoView.setLayoutParams(par);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                    callback.onVideoLoaded();
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    callback.onVideoError(file);

                    return true;
                }
            });

            videoView.setVideoPath(file.getAbsolutePath());

            setView(videoView, false);

            videoView.start();
        }
    }

    public VideoView getVideoView() {
        return videoView;
    }

    public void onError() {
        Toast.makeText(getContext(), R.string.image_preview_failed, Toast.LENGTH_SHORT).show();
        callback.setProgress(false);
    }

    public void onNotFoundError() {
        Toast.makeText(getContext(), R.string.image_not_found, Toast.LENGTH_LONG).show();
        callback.setProgress(false);
    }

    public void onOutOfMemoryError() {
        Toast.makeText(getContext(), R.string.image_preview_failed_oom, Toast.LENGTH_SHORT).show();
        callback.setProgress(false);
    }

    public void cancelLoad() {
        if (imageRequest != null) {
            imageRequest.cancel();
            imageRequest = null;
        }

        if (ionRequest != null) {
            ionRequest.cancel(true);
        }
    }

    @Override
    public void onClick(View v) {
        callback.onTap();
    }

    public static interface ThumbnailImageViewCallback {
        public void onTap();

        public void setProgress(boolean progress);

        public void setLinearProgress(long current, long total, boolean done);

        public void onVideoLoaded();

        public void onVideoError(File video);
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
