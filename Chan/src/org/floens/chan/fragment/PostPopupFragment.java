package org.floens.chan.fragment;

import org.floens.chan.R;
import org.floens.chan.entity.Post;
import org.floens.chan.manager.ThreadManager;
import org.floens.chan.view.PostView;

import android.app.DialogFragment;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

public class PostPopupFragment extends DialogFragment {
    private Context context;
    private ScrollView wrapper;
    
    private Post post;
    private ThreadManager manager;
    
    public static PostPopupFragment newInstance(Post post, ThreadManager manager) {
        PostPopupFragment fragment = new PostPopupFragment();
        fragment.post = post;
        fragment.manager = manager;
        
        return fragment;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setStyle(STYLE_NO_TITLE, 0);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        context = inflater.getContext();
        
        Resources resources = context.getResources();
        int minWidth = resources.getDimensionPixelSize(R.dimen.post_popup_min_width);
        int minHeight = resources.getDimensionPixelSize(R.dimen.post_popup_min_height);
        
        wrapper = new ScrollView(context);
        wrapper.setMinimumWidth(minWidth);
        wrapper.setMinimumHeight(minHeight);
        
        return wrapper;
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        if (post == null) {
            throw new IllegalArgumentException("No post specified");
        }
        
        PostView postView = new PostView(getActivity());
        postView.setPost(post, manager);
        
        wrapper.addView(postView);
    }
}





