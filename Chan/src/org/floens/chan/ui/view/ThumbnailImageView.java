package org.floens.chan.ui.view;

import java.io.File;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.net.FileRequest;
import org.floens.chan.core.net.GIFRequest;
import org.floens.chan.utils.Utils;

import uk.co.senab.photoview.PhotoViewAttacher;
import uk.co.senab.photoview.PhotoViewAttacher.OnViewTapListener;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;

public class ThumbnailImageView extends LoadView implements OnViewTapListener, View.OnClickListener {
    private ThumbnailImageViewCallback callback;

    /**
     * Max amount to scale the image inside the view
     */
    private final float maxScale = 3f;

    private boolean thumbnailNeeded = true;
    
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
                }
            }
        }, getWidth(), getHeight());
    }

    public void setBigImage(String imageUrl) {
        callback.setProgress(true);

        ChanApplication.getImageLoader().get(imageUrl, new ImageListener() {
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
                }
            }
        }, (int) (getWidth() * maxScale), (int) (getHeight() * maxScale));
    }

    public void setGif(String gifUrl) {
        callback.setProgress(true);

        ChanApplication.getVolleyRequestQueue().add(new GIFRequest(gifUrl, new Response.Listener<GIFView>() {
            @Override
            public void onResponse(GIFView view) {
                view.setLayoutParams(Utils.MATCH_PARAMS);

                setView(view, false);
                callback.setProgress(false);
                thumbnailNeeded = false;
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                onError();
            }
        }, getContext()));
    }

    public void setVideo(String videoUrl) {
        callback.setProgress(true);
        
        ChanApplication.getVolleyRequestQueue().add(new FileRequest(videoUrl, new Response.Listener<File>() {
            @Override
            public void onResponse(File file) {
                if (file != null) {
                    videoView = new VideoView(getContext());
                    videoView.setZOrderOnTop(true);
                    videoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
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

                    videoView.setVideoPath(file.getAbsolutePath());
                    videoView.start();

                    setView(videoView, false);
                    callback.setProgress(false);
                    thumbnailNeeded = false;
                } else {
                    onError();
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                onError();
            }
        }));
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

    @Override
    public void onViewTap(View view, float x, float y) {
        callback.onTap();
    }

    @Override
    public void onClick(View v) {
        callback.onTap();
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
