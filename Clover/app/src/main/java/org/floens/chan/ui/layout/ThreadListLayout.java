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
package org.floens.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;

import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.ui.view.ThumbnailView;

/**
 * A layout that wraps around a listview to manage showing posts.
 */
public class ThreadListLayout extends RelativeLayout {
    private ListView listView;
    private PostAdapter postAdapter;
    private PostAdapter.PostAdapterCallback postAdapterCallback;
    private PostView.PostViewCallback postViewCallback;

    private int restoreListViewIndex;
    private int restoreListViewTop;

    public ThreadListLayout(Context context) {
        super(context);
        init();
    }

    public ThreadListLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThreadListLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        restoreListViewIndex = listView.getFirstVisiblePosition();
        restoreListViewTop = listView.getChildAt(0) == null ? 0 : listView.getChildAt(0).getTop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        listView.setSelectionFromTop(restoreListViewIndex, restoreListViewTop);
    }

    private void init() {
        listView = new ListView(getContext());
        addView(listView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    public void setCallbacks(PostAdapter.PostAdapterCallback postAdapterCallback, PostView.PostViewCallback postViewCallback) {
        this.postAdapterCallback = postAdapterCallback;
        this.postViewCallback = postViewCallback;

        postAdapter = new PostAdapter(getContext(), postAdapterCallback, postViewCallback);
        listView.setAdapter(postAdapter);
    }

    public void showPosts(ChanThread thread, boolean initial) {
        if (initial) {
            listView.setSelectionFromTop(0, 0);
            restoreListViewIndex = 0;
            restoreListViewTop = 0;
        }
        postAdapter.setThread(thread);
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        ThumbnailView thumbnail = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View view = listView.getChildAt(i);
            if (view instanceof PostView) {
                PostView postView = (PostView) view;
                Post post = postView.getPost();
                if (post.hasImage && post.imageUrl.equals(postImage.imageUrl)) {
                    thumbnail = postView.getThumbnail();
                    break;
                }
            }
        }
        return thumbnail;
    }

    public void scrollTo(int position) {
        listView.smoothScrollToPosition(position);
    }
}
