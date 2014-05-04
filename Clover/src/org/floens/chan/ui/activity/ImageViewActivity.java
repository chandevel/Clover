/*
 * Chan - 4chan browser https://github.com/Floens/Chan/
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
package org.floens.chan.ui.activity;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.R;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.adapter.ImageViewAdapter;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.fragment.ImageViewFragment;
import org.floens.chan.utils.ImageSaver;
import org.floens.chan.utils.Logger;

import android.app.ActionBar;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

/**
 * An fragment pager that contains images. Call setPosts first, and then start
 * the activity with startActivity()
 */
public class ImageViewActivity extends Activity implements ViewPager.OnPageChangeListener {
    private static final String TAG = "ImageViewActivity";

    private static PostAdapter postAdapter;
    private static int selectedId = -1;

    private ViewPager viewPager;
    private ImageViewAdapter adapter;
    private int currentPosition;

    /**
     * Set the posts to show
     * 
     * @param other
     *            the posts to get image data from
     * @param selected
     *            the no that the user clicked on
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
                if (post.hasImage) {
                    imagePosts.add(post);
                }
            }

            // Setup our pages and adapter
            setContentView(R.layout.image_pager);
            viewPager = (ViewPager) findViewById(R.id.image_pager);
            adapter = new ImageViewAdapter(getFragmentManager(), this);
            adapter.setList(imagePosts);
            viewPager.setAdapter(adapter);
            viewPager.setOnPageChangeListener(this);

            // Select the right image
            for (int i = 0; i < imagePosts.size(); i++) {
                if (imagePosts.get(i).no == selectedId) {
                    viewPager.setCurrentItem(i);
                    onPageSelected(i);
                    break;
                }
            }
        } else {
            Logger.e(TAG, "Posts in ImageViewActivity was null");
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Avoid things like out of sync, since this is an activity.
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        postAdapter = null;
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        currentPosition = position;

        for (int i = -1; i <= 1; i++) {
            ImageViewFragment fragment = getFragment(i);
            if (fragment != null) {
                fragment.onDeselected();
            }
        }

        ImageViewFragment fragment = getFragment(currentPosition);
        if (fragment != null) {
            fragment.onSelected(adapter, position);
        }

        Post post = adapter.getPost(position);
        if (postAdapter != null) {
            postAdapter.scrollToPost(post);
        }
    }

    public void invalidateActionBar() {
        invalidateOptionsMenu();
    }

    public void callOnSelect() {
        ImageViewFragment fragment = getFragment(currentPosition);
        if (fragment != null) {
            fragment.onSelected(adapter, currentPosition);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_download_album) {
            List<Uri> uris = new ArrayList<Uri>();
            Post aPost = null;
            for (Post post : adapter.getList()) {
                uris.add(Uri.parse(post.imageUrl));
                aPost = post;
            }

            if (uris.size() > 0) {
                String name = "downloaded";
                if (aPost != null) {
                    name = aPost.board + "_" + aPost.resto;
                }

                ImageSaver.saveAll(this, name, uris);
            }

            return true;
        } else {
            ImageViewFragment fragment = getFragment(currentPosition);
            if (fragment != null) {
                fragment.customOnOptionsItemSelected(item);
            }

            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.image_view, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        ImageViewFragment fragment = getFragment(currentPosition);
        if (fragment != null) {
            fragment.onPrepareOptionsMenu(currentPosition, adapter, menu);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private ImageViewFragment getFragment(int i) {
        if (i >= 0 && i < adapter.getCount()) {
            Object o = adapter.instantiateItem(viewPager, i);
            if (o instanceof ImageViewFragment) {
                return (ImageViewFragment) o;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}
