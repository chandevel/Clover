package org.floens.chan.imageview.fragment;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.imageview.activity.ImageViewActivity;
import org.floens.chan.imageview.view.NetworkPhotoView;
import org.floens.chan.model.Post;
import org.floens.chan.net.GIFRequest;
import org.floens.chan.view.GIFView;

import uk.co.senab.photoview.PhotoViewAttacher.OnViewTapListener;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;

public class ImageViewFragment extends Fragment implements View.OnLongClickListener, OnViewTapListener, OnClickListener {
    private Context context;
    private RelativeLayout wrapper;
    private Post post;
    private GIFRequest gifRequest;
    private ImageViewActivity activity;
    private int index;
    
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
        context = inflater.getContext();
        
        wrapper = new RelativeLayout(context);
        int padding = (int)context.getResources().getDimension(R.dimen.image_popup_padding);
        wrapper.setPadding(padding, padding, padding, padding);
        wrapper.setGravity(Gravity.CENTER);
        
        wrapper.setOnClickListener(this);
        
        return wrapper;
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        if (post == null || !post.hasImage) {
            throw new IllegalArgumentException("No post / post has no image");
        }
        
        if (post.ext.equals("gif")) {
            gifRequest = new GIFRequest(post.imageUrl, new Response.Listener<GIFView>() {
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
            }, context);
           
            ChanApplication.getVolleyRequestQueue().add(gifRequest);
            showProgressBar(true);
        } else {
            NetworkPhotoView imageView = new NetworkPhotoView(context);
            imageView.setImageViewFragment(this);
            imageView.setFadeIn(100);
            imageView.setImageUrl(post.imageUrl, ChanApplication.getImageLoader());
            imageView.setMaxScale(3f);
            imageView.setOnLongClickListenerToAttacher(this);
            imageView.setOnViewTapListenerToAttacher(this);
            
            wrapper.addView(imageView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            
            showProgressBar(true);
        }
    }

    @Override
    /*
     * TODO: figure out why adding an onLongClick listener removes the error:
     * "ImageView no longer exists. You should not use this PhotoViewAttacher any more.");
     * PhotoViewAttacher line 300
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





