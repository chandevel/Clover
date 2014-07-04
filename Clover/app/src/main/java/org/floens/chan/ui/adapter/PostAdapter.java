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
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.ScrollerRunnable;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.utils.Time;
import org.floens.chan.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class PostAdapter extends BaseAdapter {
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_STATUS = 1;

    private final Context context;
    private final ThreadManager threadManager;
    private final AbsListView listView;
    private boolean endOfLine;
    private final List<Post> postList = new ArrayList<>();
    private int lastPostCount = 0;
    private long lastViewedTime = 0;
    private String loadMessage = null;

    public PostAdapter(Context activity, ThreadManager threadManager, AbsListView listView) {
        context = activity;
        this.threadManager = threadManager;
        this.listView = listView;
    }

    @Override
    public int getCount() {
        return postList.size() + (showStatusView() ? 1 : 0);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (showStatusView()) {
            return position == getCount() - 1 ? VIEW_TYPE_STATUS : VIEW_TYPE_ITEM;
        } else {
            return VIEW_TYPE_ITEM;
        }
    }

    @Override
    public Post getItem(int position) {
        if (position >= 0 && position < postList.size()) {
            return postList.get(position);
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
                if (convertView == null || convertView.getTag() == null && (Integer) convertView.getTag() != VIEW_TYPE_ITEM) {
                    convertView = new PostView(context);
                    convertView.setTag(VIEW_TYPE_ITEM);
                }

                PostView postView = (PostView) convertView;
                postView.setPost(getItem(position), threadManager);

                return postView;
            }
            case VIEW_TYPE_STATUS: {
                return new StatusView(context);
            }
        }

        return null;
    }

    private void onGetBottomView() {
        if (threadManager.getLoadable().isBoardMode() && !endOfLine) {
            // Try to load more posts
            threadManager.requestNextData();
        }

        if (lastPostCount != postList.size()) {
            lastPostCount = postList.size();
            lastViewedTime = Time.get();
        }

        if (Time.get(lastViewedTime) > 1000L) {
            lastViewedTime = Time.get();
            threadManager.bottomPostViewed();
        }
    }

    private boolean showStatusView() {
        Loadable l = threadManager.getLoadable();
        if (l != null) {
            if (l.isBoardMode()) {
                return true;
            } else if (l.isThreadMode() && threadManager.shouldWatch()) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
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

    public void scrollToPost(int no) {
        notifyDataSetChanged();

        for (int i = 0; i < postList.size(); i++) {
            if (postList.get(i).no == no) {
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

    public void setErrorMessage(String loadMessage) {
        this.loadMessage = loadMessage;
    }

    public String getErrorMessage() {
        return loadMessage;
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
            Loader loader = threadManager.getLoader();
            if (loader == null)
                return;

            setGravity(Gravity.CENTER);

            if (threadManager.shouldWatch()) {
                String error = getErrorMessage();
                if (error != null) {
                    setText(error);
                } else {
                    int time = Math.round(loader.getTimeUntilLoadMore() / 1000f);
                    if (time == 0) {
                        setText("Loading");
                    } else {
                        setText("Loading in " + time);
                    }
                }

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!detached) {
                            notifyDataSetChanged();
                        }
                    }
                }, 1000);

                setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Loader loader = threadManager.getLoader();
                        if (loader != null) {
                            loader.requestMoreDataAndResetTimer();
                        }

                        notifyDataSetChanged();
                    }
                });

                Utils.setPressedDrawable(this);
            } else {
                if (endOfLine) {
                    setText(context.getString(R.string.thread_load_end_of_line));
                } else {
                    setProgressBar();
                }
            }
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
            addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, Utils.dp(48)));
        }

        private void setProgressBar() {
            addView(new ProgressBar(context));
        }
    }
}
