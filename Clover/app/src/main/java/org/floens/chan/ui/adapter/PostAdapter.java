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
package org.floens.chan.ui.adapter;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.ScrollerRunnable;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.floens.chan.utils.AndroidUtils.dp;

public class PostAdapter extends BaseAdapter implements Filterable {
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_STATUS = 1;

    private final Object lock = new Object();

    private final Context context;
    private final AbsListView listView;

    private final PostAdapterCallback postAdapterCallback;
    private final PostView.PostViewCallback postViewCallback;

    /**
     * The list with the original data
     */
    private final List<Post> sourceList = new ArrayList<>();

    /**
     * The list that is displayed (filtered)
     */
    private final List<Post> displayList = new ArrayList<>();

    private boolean endOfLine;
    private int lastPostCount = 0;
    private String statusMessage = null;
    private String filter = "";
    private int pendingScrollToPost = -1;
    private String statusPrefix = "";

    public PostAdapter(Context context, PostAdapterCallback postAdapterCallback, PostView.PostViewCallback postViewCallback) {
        this.postAdapterCallback = postAdapterCallback;
        this.context = context;
        this.postViewCallback = postViewCallback;
    }

    @Override
    public int getCount() {
        return displayList.size() + (showStatusView() ? 1 : 0);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == getCount() - 1) {
            return showStatusView() ? VIEW_TYPE_STATUS : VIEW_TYPE_ITEM;
        } else {
            return VIEW_TYPE_ITEM;
        }
    }

    @Override
    public Post getItem(int position) {
        if (position >= 0 && position < displayList.size()) {
            return displayList.get(position);
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= getCount() - 1) {
            onGetBottomView();
        }

        switch (getItemViewType(position)) {
            case VIEW_TYPE_ITEM: {
                if (convertView == null || convertView.getTag() == null || (Integer) convertView.getTag() != VIEW_TYPE_ITEM) {
                    convertView = new PostView(context);
                    convertView.setTag(VIEW_TYPE_ITEM);
                }

                PostView postView = (PostView) convertView;
                postView.setPost(getItem(position), postViewCallback);

                return postView;
            }
            case VIEW_TYPE_STATUS: {
                return new StatusView(context);
            }
        }

        return null;
    }

    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraintRaw) {
                FilterResults results = new FilterResults();

                if (TextUtils.isEmpty(constraintRaw)) {
                    ArrayList<Post> tmp;
                    synchronized (lock) {
                        tmp = new ArrayList<>(sourceList);
                    }
                    results.values = tmp;
                } else {
                    List<Post> all;
                    synchronized (lock) {
                        all = new ArrayList<>(sourceList);
                    }

                    List<Post> accepted = new ArrayList<>();
                    String constraint = constraintRaw.toString().toLowerCase(Locale.ENGLISH);

                    for (Post post : all) {
                        if (post.comment.toString().toLowerCase(Locale.ENGLISH).contains(constraint) ||
                                post.subject.toLowerCase(Locale.ENGLISH).contains(constraint)) {
                            accepted.add(post);
                        }
                    }

                    results.values = accepted;
                }

                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, final FilterResults results) {
                filter = constraint.toString();
                synchronized (lock) {
                    displayList.clear();
                    displayList.addAll((List<Post>) results.values);
                }
                notifyDataSetChanged();
                postAdapterCallback.onFilteredResults(filter, ((List<Post>) results.values).size(), TextUtils.isEmpty(filter));
                if (pendingScrollToPost >= 0) {
                    final int to = pendingScrollToPost;
                    pendingScrollToPost = -1;
                    postAdapterCallback.scrollTo(to);
                }
            }
        };
    }

    public void setFilter(String filter) {
        getFilter().filter(filter);
        notifyDataSetChanged();
    }

    public void setThread(ChanThread thread) {
        synchronized (lock) {
            if (thread.archived) {
                statusPrefix = context.getString(R.string.thread_archived) + " - ";
            } else if (thread.closed) {
                statusPrefix = context.getString(R.string.thread_closed) + " - ";
            } else {
                statusPrefix = "";
            }

            sourceList.clear();
            sourceList.addAll(thread.posts);

            if (!isFiltering()) {
                displayList.clear();
                displayList.addAll(sourceList);
            } else {
                setFilter(filter);
            }
        }

        notifyDataSetChanged();
    }

    public List<Post> getList() {
        return sourceList;
    }

    public void setEndOfLine(boolean endOfLine) {
        this.endOfLine = endOfLine;

        notifyDataSetChanged();
    }

    /* TODO
    public void scrollToPost(int no) {
        if (isFiltering()) {
            pendingScrollToPost = no;
        } else {
            notifyDataSetChanged();

            synchronized (lock) {
                for (int i = 0; i < displayList.size(); i++) {
                    if (displayList.get(i).no == no) {
                        if (Math.abs(i - listView.getFirstVisiblePosition()) > 20 || listView.getChildCount() == 0) {
                            listView.setSelection(i);
                        } else {
                            ScrollerRunnable r = new ScrollerRunnable(listView);
                            r.start(i);
                        }

                        break;
                    }
                }
            }
        }
    }*/

    public void setStatusMessage(String loadMessage) {
        this.statusMessage = loadMessage;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    private void onGetBottomView() {
        /*if (postAdapterCallback.getLoadable().isBoardMode() && !endOfLine) {
            // Try to load more posts
            threadManager.requestNextData();
        }*/

        if (lastPostCount != sourceList.size()) {
            lastPostCount = sourceList.size();
            postAdapterCallback.onListScrolledToBottom();
            notifyDataSetChanged();
        }
    }

    private boolean showStatusView() {
        Loadable l = postAdapterCallback.getLoadable();
        if (l != null) {
            return l.isBoardMode() || l.isThreadMode();
        } else {
            return false;
        }
    }

    private boolean isFiltering() {
        return !TextUtils.isEmpty(filter);
    }

    public interface PostAdapterCallback {
        public void onFilteredResults(String filter, int count, boolean all);

        public Loadable getLoadable();

        public void onListScrolledToBottom();

        public void onListStatusClicked();

        public void scrollTo(int position);
    }

    public class StatusView extends LinearLayout {
        boolean detached = false;

        public StatusView(Context activity) {
            super(activity);
            init();
        }

        public StatusView(Context activity, AttributeSet attr) {
            super(activity, attr);
            init();
        }

        public StatusView(Context activity, AttributeSet attr, int style) {
            super(activity, attr, style);
            init();
        }

        public void init() {
            // TODO
            /*
            ChanLoader chanLoader = threadManager.getChanLoader();
            if (chanLoader == null)
                return;

            setGravity(Gravity.CENTER);

            Loadable loadable = chanLoader.getLoadable();
            if (loadable.isThreadMode()) {
                String error = getStatusMessage();
                if (error != null) {
                    setText(error);
                } else {
                    if (threadManager.isWatching()) {
                        long time = chanLoader.getTimeUntilLoadMore() / 1000L;
                        if (time == 0) {
                            setText(statusPrefix + context.getString(R.string.thread_refresh_now));
                        } else {
                            setText(statusPrefix + context.getString(R.string.thread_refresh_countdown, time));
                        }

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!detached) {
                                    notifyDataSetChanged();
                                }
                            }
                        }, 1000);
                    } else {
                        if (chanLoader.getTimeUntilLoadMore() == 0) {
                            setText(statusPrefix + context.getString(R.string.thread_refresh_now));
                        } else {
                            setText(statusPrefix + context.getString(R.string.thread_refresh_bar_inactive));
                        }
                    }

                    setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ChanLoader chanLoader = threadManager.getChanLoader();
                            if (chanLoader != null) {
                                chanLoader.requestMoreDataAndResetTimer();
                                setText(context.getString(R.string.thread_refresh_now));
                            }

                            notifyDataSetChanged();
                        }
                    });
                }

                Utils.setPressedDrawable(this);
            } else if (loadable.isBoardMode()) {
                if (endOfLine) {
                    setText(context.getString(R.string.thread_load_end_of_line));
                } else {
                    setProgressBar();
                }
            }*/
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            detached = true;
        }

        private void setText(String string) {
            TextView text = new TextView(context);
            text.setText(string);
            text.setGravity(Gravity.CENTER);
            addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
        }

        private void setProgressBar() {
            addView(new ProgressBar(context));
        }
    }
}
