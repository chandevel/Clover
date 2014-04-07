package org.floens.chan.ui.fragment;

import java.io.File;
import java.io.IOException;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.net.CachingRequest;
import org.floens.chan.core.net.GIFRequest;
import org.floens.chan.ui.activity.ImageViewActivity;
import org.floens.chan.ui.view.GIFView;
import org.floens.chan.ui.view.NetworkPhotoView;
import org.floens.chan.utils.Logger;

import uk.co.senab.photoview.PhotoViewAttacher.OnViewTapListener;
import android.app.Fragment;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.DiskBasedCache;

public class ImageViewFragment extends Fragment implements View.OnLongClickListener, OnViewTapListener, OnClickListener {
    private static final String TAG = "ImageViewFragment";

    private Context context;
    private RelativeLayout wrapper;
    private Post post;
    private ImageViewActivity activity;
    private int index;

    private CachingRequest movieRequest;

    public static ImageViewFragment newInstance(Post post, ImageViewActivity activity, int index) {
        ImageViewFragment imageViewFragment = new ImageViewFragment();
        imageViewFragment.post = post;
        imageViewFragment.activity = activity;
        imageViewFragment.index = index;

        return imageViewFragment;
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        // https://code.google.com/p/android/issues/detail?id=19917
        bundle.putString("bug_19917", "bug_19917");
        super.onSaveInstanceState(bundle);
    }

    public void showProgressBar(boolean e) {
        activity.showProgressBar(e, index);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (post == null) {
            // No restoring
            return null;
        } else {
            context = inflater.getContext();

            wrapper = new RelativeLayout(context);
            int padding = (int) context.getResources().getDimension(R.dimen.image_popup_padding);
            wrapper.setPadding(padding, padding, padding, padding);
            wrapper.setGravity(Gravity.CENTER);

            wrapper.setOnClickListener(this);

            return wrapper;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (post == null) {
            // No restoring
        } else {
            if (!post.hasImage) {
                throw new IllegalArgumentException("No post / post has no image");
            }

            if (post.ext.equals("gif")) {
                loadGif();
            } else if (post.ext.equals("webm")) {
                if (ChanPreferences.getVideosEnabled()) {
                    loadMovie();                    
                } else {
                    loadOtherImage(post.thumbnailUrl);
                }
            } else {
                loadOtherImage(post.imageUrl);
            }
        }
    }

    private void loadMovie() {
        movieRequest = new CachingRequest(post.imageUrl, new Response.Listener<Void>() {
            @Override
            public void onResponse(Void empty) {
                if (movieRequest != null) {
                    try {
                        handleMovieResponse(movieRequest);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(context, R.string.image_preview_failed, Toast.LENGTH_LONG).show();
                    }
                }

                showProgressBar(false);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, R.string.image_preview_failed, Toast.LENGTH_LONG).show();
                showProgressBar(false);
            }
        });

        movieRequest.setShouldCache(true);

        ChanApplication.getVolleyRequestQueue().add(movieRequest);

        showProgressBar(true);
    }

    private void handleMovieResponse(CachingRequest request) throws IOException {
        DiskBasedCache cache = (DiskBasedCache) ChanApplication.getVolleyRequestQueue().getCache();
        File file = cache.getFileForKey(movieRequest.getCacheKey());

        if (file.exists()) {
            Logger.test("Showing video from " + file.getAbsolutePath());

            final VideoView view = new VideoView(context);
            view.setZOrderOnTop(true);
            
            view.setOnLongClickListener(this);
            view.setOnClickListener(this);

            wrapper.addView(view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

            view.setOnPreparedListener(new OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                    view.start();

                }
            });

            view.setVideoPath(file.getAbsolutePath());
        } else {
            Logger.e(TAG, "Cache file doesn't exist");
            Toast.makeText(context, R.string.image_preview_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void loadOtherImage(String url) {
        NetworkPhotoView imageView = new NetworkPhotoView(context);
        imageView.setImageViewFragment(this);
        imageView.setFadeIn(100);
        imageView.setImageUrl(url, ChanApplication.getImageLoader());
        imageView.setMaxScale(3f);
        imageView.setOnLongClickListenerToAttacher(this);
        imageView.setOnViewTapListenerToAttacher(this);

        wrapper.addView(imageView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        showProgressBar(true);
    }

    private void loadGif() {
        ChanApplication.getVolleyRequestQueue().add(new GIFRequest(post.imageUrl, new Response.Listener<GIFView>() {
            @Override
            public void onResponse(GIFView view) {
                wrapper.addView(view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                view.setOnLongClickListener(ImageViewFragment.this);
                view.setOnClickListener(ImageViewFragment.this);
                showProgressBar(false);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, R.string.image_preview_failed, Toast.LENGTH_LONG).show();
                showProgressBar(false);
            }
        }, context));

        showProgressBar(true);
    }

    @Override
    /*
     * TODO: figure out why adding an onLongClick listener removes the error:
     * "ImageView no longer exists. You should not use this PhotoViewAttacher any more."
     * ); PhotoViewAttacher line 300
     */
    public boolean onLongClick(View v) {
        return false;
    }

    @Override
    public void onViewTap(View view, float x, float y) {
        activity.finish();
    }

    @Override
    public void onClick(View v) {
        activity.finish();
    }
}
