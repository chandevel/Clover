package org.floens.chan.ui.adapter;

import java.util.ArrayList;

import org.floens.chan.core.model.Post;
import org.floens.chan.ui.activity.ImageViewActivity;
import org.floens.chan.ui.fragment.ImageViewFragment;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;


public class ImageViewAdapter extends FragmentStatePagerAdapter {
    private final ImageViewActivity activity;
    private int count = 0;
    private final ArrayList<Post> postList = new ArrayList<Post>();
    
    public ImageViewAdapter(FragmentManager fragmentManager, ImageViewActivity activity) {
        super(fragmentManager);
        this.activity = activity;
    }
    
    @Override
    public int getCount() {
        return count;
    }
    
    @Override
    public Fragment getItem(int position) {
        return ImageViewFragment.newInstance(postList.get(position), activity, position);
    }
    
    public Post getPost(int position) {
        if (position < 0 || position >= getCount()) return null;
        
        return postList.get(position);
    }
    
    @Override
    public void destroyItem(View collection, int position, Object o) {
        View view = (View)o;
        ((ViewPager) collection).removeView(view);
        view = null;
    }
    
    public void addToList(ArrayList<Post> list){
        count += list.size();
        postList.addAll(list);
        
        notifyDataSetChanged();
    }
}





