package org.floens.chan.adapter;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.R;
import org.floens.chan.manager.ThreadManager;
import org.floens.chan.model.Post;
import org.floens.chan.utils.Utils;
import org.floens.chan.view.PostView;
import org.floens.chan.view.ThreadWatchCounterView;

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
    private int count = 0;
    private final List<Post> postList = new ArrayList<Post>();

    public PostAdapter(Context activity, ThreadManager threadManager, ListView listView) {
        context = activity;
        this.threadManager = threadManager;
        this.listView = listView;
    }

    @Override
    public int getCount() {
        if (threadManager.getLoadable().isBoardMode() || threadManager.getLoadable().isThreadMode()) {
            return count + 1;
        } else {
            return count;
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

        if (position >= count) {
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
        if (threadManager.getLoadable().isThreadMode()) {
            ThreadWatchCounterView view = new ThreadWatchCounterView(context);
            Utils.setPressedDrawable(view);
            view.init(threadManager, listView, this);
            int padding = context.getResources().getDimensionPixelSize(R.dimen.general_padding);
            view.setPadding(padding, padding, padding, padding);
            int height = context.getResources().getDimensionPixelSize(R.dimen.dp48);
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

    public void addList(List<Post> list) {
        postList.addAll(list);
        count = postList.size();

        notifyDataSetChanged();
    }

    public void setList(List<Post> list) {
        postList.clear();
        postList.addAll(list);
        count = postList.size();

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
        for (int i = 0; i < postList.size(); i++) {
            if (postList.get(i).no == post.no) {
//                listView.smoothScrollToPosition(i); does not work when a view is taller than the container
                listView.setSelection(i);

                break;
            }
        }
    }
}





