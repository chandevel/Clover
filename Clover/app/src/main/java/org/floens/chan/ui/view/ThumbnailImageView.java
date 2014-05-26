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

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.net.FileRequest;
import org.floens.chan.core.net.GIFRequest;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Utils;

import java.io.File;

import uk.co.senab.photoview.PhotoViewAttacher;
import uk.co.senab.photoview.PhotoViewAttacher.OnViewTapListener;

public class ThumbnailImageView extends LoadView implements OnViewTapListener, View.OnClickListener {
    private static final String TAG = "ThumbnailImageView";

    private ThumbnailImageViewCallback callback;

    /**
     * Max amount to scale the image inside the view
     */
    private final float maxScale = 3f;

    private boolean thumbnailNeeded = true;
    private boolean tapDismiss = false;

    private ImageContainer imageContainerRequest;
    private Request<?> imageRequest;
    private VideoView videoView;

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

        ChanApplication.getImageLoader().get(thumbnailUrl, new ImageListener() {
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
                    tapDismiss = true;
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

        // 4096 is the max GPU upload size
        int maxWidth = Math.min((int) (getWidth() * maxScale), 4096);
        int maxHeight = Math.min((int) (getHeight() * maxScale), 4096);

        imageContainerRequest = ChanApplication.getImageLoader().get(imageUrl, new ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                onError();
            }

            @Override
            public void onResponse(ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null) {
                    CleanupImageView image = new CleanupImageView(getContext());
                    image.setImageBitmap(response.getBitmap());

                    PhotoViewAttacher attacher = new PhotoViewAttacher(image);
                    attacher.setOnViewTapListener(ThumbnailImageView.this);
                    attacher.setMaximumScale(maxScale);

                    image.setAttacher(attacher);

                    setView(image, !isImmediate);
                    callback.setProgress(false);
                    thumbnailNeeded = false;
                    tapDismiss = true;
                }
            }
        }, maxWidth, maxHeight);
    }

    public void setGif(String gifUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        callback.setProgress(true);

        imageRequest = ChanApplication.getVolleyRequestQueue().add(
                new GIFRequest(gifUrl, new Response.Listener<GIFView>() {
                    @Override
                    public void onResponse(GIFView view) {
                        view.setLayoutParams(Utils.MATCH_PARAMS);

                        setView(view, false);
                        callback.setProgress(false);
                        thumbnailNeeded = false;
                        tapDismiss = true;
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onError();
                    }
                }, getContext())
        );
    }

    public void setVideo(String videoUrl) {
        callback.setProgress(true);

        imageRequest = ChanApplication.getVolleyRequestQueue().add(
                new FileRequest(videoUrl, new Response.Listener<File>() {
                    @Override
                    public void onResponse(final File file) {
                        if (file != null) {
                            videoView = new VideoView(getContext());
                            videoView.setZOrderOnTop(true);
                            videoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                                    LayoutParams.MATCH_PARENT));
                            videoView.setLayoutParams(Utils.MATCH_PARAMS);
                            LayoutParams par = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                            par.gravity = Gravity.CENTER;
                            videoView.setLayoutParams(par);

                            videoView.setOnPreparedListener(new OnPreparedListener() {
                                @Override
                                public void onPrepared(MediaPlayer mp) {
                                    mp.setLooping(true);
                                    callback.onVideoLoaded();
                                }
                            });

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.sleep(200);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }

                                    videoView.start();
                                }
                            }).start();

                            videoView.setVideoPath(file.getAbsolutePath());

                            setView(videoView, false);
                            callback.setProgress(false);
                            thumbnailNeeded = false;
                            tapDismiss = true;
                        } else {
                            onError();
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onError();
                    }
                })
        );
    }

    @Override
    public void setView(View view, boolean animation) {
        super.setView(view, animation && !thumbnailNeeded);
    }

    public VideoView getVideoView() {
        return videoView;
    }

    public void onError() {
        Toast.makeText(getContext(), R.string.image_preview_failed, Toast.LENGTH_LONG).show();
        callback.setProgress(false);
    }

    public void cancelLoad() {
        if (imageRequest != null) {
            imageRequest.cancel();
            imageRequest = null;
        }

        if (imageContainerRequest != null) {
            imageContainerRequest.cancelRequest();
            imageContainerRequest = null;
        }
    }

    @Override
    public void onViewTap(View view, float x, float y) {
        if (tapDismiss) {
            callback.onTap();
        }
    }

    @Override
    public void onClick(View v) {
        if (tapDismiss) {
            callback.onTap();
        }
    }

    public static interface ThumbnailImageViewCallback {
        public void onTap();

        public void setProgress(boolean progress);

        public void onVideoLoaded();
    }

    private static class CleanupImageView extends ImageView {
        private PhotoViewAttacher attacher;

        public CleanupImageView(Context context) {
            super(context);
        }

        public CleanupImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public CleanupImageView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        public void setAttacher(PhotoViewAttacher attacher) {
            this.attacher = attacher;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();

            if (attacher != null) {
                attacher.cleanup();
            }
        }
    }
}
