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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.ScrollerRunnable;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.ui.view.ThreadWatchCounterView;
import org.floens.chan.utils.Time;
import org.floens.chan.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class PostAdapter extends BaseAdapter {
    private final Context context;
    private final ThreadManager threadManager;
    private final ListView listView;
    private boolean endOfLine;
    private final List<Post> postList = new ArrayList<>();
    private int lastPostCount = 0;
    private long lastViewedTime = 0;
    private String loadMessage = null;

    public PostAdapter(Context activity, ThreadManager threadManager, ListView listView) {
        context = activity;
        this.threadManager = threadManager;
        this.listView = listView;
    }

    @Override
    public int getCount() {
        if ((threadManager.getLoadable() != null && threadManager.getLoadable().isBoardMode())
                || threadManager.shouldWatch()) {
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
            if (lastPostCount != postList.size()) {
                lastPostCount = postList.size();
                lastViewedTime = Time.get();
            }

            if (Time.get(lastViewedTime) > 2000L) {
                lastViewedTime = Time.get();
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
            int height = Utils.dp(48f);
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
                if (Math.abs(i - listView.getFirstVisiblePosition()) > 20) {
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
}
