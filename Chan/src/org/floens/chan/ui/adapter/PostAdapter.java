package org.floens.chan.ui.adapter;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.R;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.ScrollerRunnable;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.ui.view.ThreadWatchCounterView;
import org.floens.chan.utils.Utils;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;


public class PostAdapter extends BaseAdapter {
    private final Context context;
    private final ThreadManager threadManager;
    private final ListView listView;
    private boolean endOfLine;
    private final List<Post> postList = new ArrayList<Post>();
    private long lastViewedTime = 0;

    public PostAdapter(Context activity, ThreadManager threadManager, ListView listView) {
        context = activity;
        this.threadManager = threadManager;
        this.listView = listView;
    }

    @Override
    public int getCount() {
        if (threadManager.getLoadable().isBoardMode() || threadManager.shouldWatch()) {
            return postList.size() + 1;
        } else {
            return postList.size();
        }
    }

    @Override
    public Post getItem(int position) {
        return postList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= getCount() - 1 && !endOfLine && threadManager.getLoadable().isBoardMode()) {
            // Try to load more posts
            threadManager.requestNextData();
        }

        if (position >= postList.size()) {
            if (System.currentTimeMillis() - lastViewedTime > 10000L) {
                lastViewedTime = System.currentTimeMillis();
                threadManager.bottomPostViewed();
            }

            return createThreadEndView();
        } else {
            PostView postView = null;

            if (position >= 0 && position < postList.size()) {
                if (convertView != null && convertView instanceof PostView) {
                    postView = (PostView) convertView;
                } else {
                    postView = new PostView(context);
                }

                postView.setPost(postList.get(position), threadManager);
            }

            return postView;
        }
    }

    private View createThreadEndView() {
        if (threadManager.shouldWatch()) {
            ThreadWatchCounterView view = new ThreadWatchCounterView(context);
            Utils.setPressedDrawable(view);
            view.init(threadManager, listView, this);
            int padding = context.getResources().getDimensionPixelSize(R.dimen.general_padding);
            view.setPadding(padding, padding, padding, padding);
            int height = Utils.dp(context, 48f);
            view.setHeight(height);
            view.setGravity(Gravity.CENTER);
            return view;
        } else {
            if (endOfLine) {
                TextView textView = new TextView(context);
                textView.setText(context.getString(R.string.thread_load_end_of_line));
                int padding = context.getResources().getDimensionPixelSize(R.dimen.general_padding);
                textView.setPadding(padding, padding, padding, padding);
                return textView;
            } else {
                return new ProgressBar(context);
            }
        }
    }

    public void appendList(List<Post> list) {
        for (Post post : list) {
            boolean flag = true;
            for (Post own : postList) {
                if (post.no == own.no) {
                    flag = false;
                    break;
                }
            }

            if (flag) {
                postList.add(post);
            }
        }

        notifyDataSetChanged();
    }

    public void setList(List<Post> list) {
        postList.clear();
        postList.addAll(list);

        notifyDataSetChanged();
    }

    public List<Post> getList() {
        return postList;
    }

    public void setEndOfLine(boolean endOfLine) {
        this.endOfLine = endOfLine;

        notifyDataSetChanged();
    }

    public void scrollToPost(Post post) {
        notifyDataSetChanged();

        for (int i = 0; i < postList.size(); i++) {
            if (postList.get(i).no == post.no) {
                ScrollerRunnable r = new ScrollerRunnable(listView);
                r.start(i);

                break;
            }
        }
    }
}





