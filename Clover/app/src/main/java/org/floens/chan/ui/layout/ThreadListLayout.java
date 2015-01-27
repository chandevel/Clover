package org.floens.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;
import android.widget.RelativeLayout;

import com.android.volley.VolleyError;

import org.floens.chan.core.model.ChanThread;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.view.PostView;

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

    public void showError(VolleyError error) {

    }
}
