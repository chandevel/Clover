package org.floens.chan.imageview.activity;

import java.util.ArrayList;

import org.floens.chan.R;
import org.floens.chan.adapter.PostAdapter;
import org.floens.chan.imageview.ImageSaver;
import org.floens.chan.imageview.adapter.ImageViewAdapter;
import org.floens.chan.model.Post;
import org.floens.chan.utils.Logger;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

/**
 * An fragment pager that contains images. Call setPosts first, 
 * and then start the activity with startActivity()
 */
public class ImageViewActivity extends Activity implements ViewPager.OnPageChangeListener {
    private static final String TAG = "ImageViewActivity";
    
    private static PostAdapter postAdapter;
    private static int selectedId = -1;
    
    private ImageViewAdapter adapter;
    private int currentPosition;
    private boolean[] progressData;
    
    /**
     * Set the posts to show
     * @param other the posts to get image data from
     * @param selected the no that the user clicked on
     */
    public static void setAdapter(PostAdapter adapter, int selected) {
        postAdapter = adapter;
        selectedId = selected;
    }
   
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_HOME_AS_UP);
        
        super.onCreate(savedInstanceState);
        
        if (postAdapter != null) {
            // Get the posts with images
            ArrayList<Post> imagePosts = new ArrayList<Post>();
            for (Post post : postAdapter.getList()) {
                if (post.hasImage){
                    imagePosts.add(post);
                }
            }
            
            // Setup our pages and adapter
            setContentView(R.layout.image_pager);
            ViewPager viewPager = (ViewPager) findViewById(R.id.image_pager);
            adapter = new ImageViewAdapter(getFragmentManager(), this);
            adapter.addToList(imagePosts);
            viewPager.setAdapter(adapter);
            viewPager.setOnPageChangeListener(this);
            
            progressData = new boolean[imagePosts.size()];
            
            // Select the right image
            for (int i = 0; i < imagePosts.size(); i++) {
                if (imagePosts.get(i).no == selectedId) {
                    viewPager.setCurrentItem(i);
                    onPageSelected(i);
                    break;
                }
            }
        } else {
            Logger.e(TAG, "Posts in imageview list was null");
            finish();
        }
    }
    
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        postAdapter = null;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_image_save) {
            Post post = adapter.getPost(currentPosition);
            ImageSaver.save(this, post.imageUrl, post.filename, post.ext);
            
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.image_view, menu);
        return true;
    }
    
    /**
     * Show the progressbar in the actionbar at the specified position.
     * @param e
     * @param position
     */
    public void showProgressBar(boolean e, int position) {
        progressData[position] = e;
        
        if (position == currentPosition) {
            setProgressBarIndeterminateVisibility(e);
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        // Avoid things like out of sync, since this is an activity.
        finish();
    }
    
    @Override
    public void onPageSelected(int position) {
        currentPosition = position;
        
        setProgressBarIndeterminateVisibility(progressData[position]);
        
        Post post = adapter.getPost(position);
        
        if (post != null) {
            String filename = post.filename + "." + post.ext;
            String text = "(" + (position + 1) + "/" + progressData.length +  ") " + filename;
            
            getActionBar().setTitle(text);
        }
        
        if (postAdapter != null) {
            postAdapter.scrollToPost(post);
        }
    }
    
    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }
}





