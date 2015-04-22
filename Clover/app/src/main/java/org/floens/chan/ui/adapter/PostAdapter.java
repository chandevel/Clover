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

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.floens.chan.R;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.cell.ThreadStatusCell;
import org.floens.chan.ui.view.PostView;

import java.util.ArrayList;
import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_POST = 0;
    private static final int TYPE_STATUS = 1;

    private final PostAdapterCallback postAdapterCallback;
    private final PostView.PostViewCallback postViewCallback;
    private final ThreadStatusCell.Callback statusCellCallback;
    private RecyclerView recyclerView;

    private final List<Post> sourceList = new ArrayList<>();
    private final List<Post> displayList = new ArrayList<>();
    private int lastPostCount = 0;
    private String error = null;
    private String filter = "";
    private int pendingScrollToPost = -1;
    private Post highlightedPost;
    private String highlightedPostId;

    public PostAdapter(RecyclerView recyclerView, PostAdapterCallback postAdapterCallback, PostView.PostViewCallback postViewCallback, ThreadStatusCell.Callback statusCellCallback) {
        this.recyclerView = recyclerView;
        this.postAdapterCallback = postAdapterCallback;
        this.postViewCallback = postViewCallback;
        this.statusCellCallback = statusCellCallback;

        setHasStableIds(true);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_POST) {
            PostView postView = new PostView(parent.getContext());
            return new PostViewHolder(postView);
        } else {
            StatusViewHolder statusViewHolder = new StatusViewHolder((ThreadStatusCell) LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_thread_status, parent, false));
            statusViewHolder.threadStatusCell.setCallback(statusCellCallback);
            statusViewHolder.threadStatusCell.setError(error);
            return statusViewHolder;
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_POST) {
            PostViewHolder postViewHolder = (PostViewHolder) holder;
            Post post = displayList.get(position);
            boolean highlight = post == highlightedPost || post.id.equals(highlightedPostId);
            postViewHolder.postView.setPost(post, postViewCallback, highlight);
        } else if (getItemViewType(position) == TYPE_STATUS) {
            ((StatusViewHolder)holder).threadStatusCell.update();
            onScrolledToBottom();
        }
    }

    @Override
    public int getItemCount() {
        if (showStatusView()) {
            return displayList.size() + 1;
        } else {
            return displayList.size();
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (showStatusView()) {
            if (position == getItemCount() - 1) {
                return TYPE_STATUS;
            } else {
                return TYPE_POST;
            }
        } else {
            return TYPE_POST;
        }
    }

    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) != TYPE_POST) {
            return -1;
        } else {
            return displayList.get(position).no;
        }
    }

    public void setThread(ChanThread thread) {
        showError(null);
        sourceList.clear();
        sourceList.addAll(thread.posts);

        displayList.clear();
        displayList.addAll(sourceList);

        // Update all, recyclerview will figure out all the animations
        notifyDataSetChanged();
    }

    public void cleanup() {
        highlightedPost = null;
        sourceList.clear();
        displayList.clear();
        lastPostCount = 0;
    }

    public void showError(String error) {
        this.error = error;
        if (showStatusView()) {
            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(getItemCount() - 1);
            // Recyclerview did not sync yet
            if (viewHolder instanceof StatusViewHolder) {
                ThreadStatusCell threadStatusCell = ((StatusViewHolder) viewHolder).threadStatusCell;
                threadStatusCell.setError(error);
                threadStatusCell.update();
            }
        }
    }

    public void highlightPost(Post post) {
        highlightedPostId = null;
        highlightedPost = post;
        notifyDataSetChanged();
    }

    public void highlightPostId(String id) {
        highlightedPost = null;
        highlightedPostId = id;
        notifyDataSetChanged();
    }

    private void onScrolledToBottom() {
        if (lastPostCount != sourceList.size()) {
            lastPostCount = sourceList.size();
            postAdapterCallback.onListScrolledToBottom();
        }
    }

    private boolean showStatusView() {
        return postAdapterCallback.getLoadable().isThreadMode();
    }

    private boolean isFiltering() {
        return !TextUtils.isEmpty(filter);
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        private PostView postView;

        public PostViewHolder(PostView postView) {
            super(postView);
            this.postView = postView;
        }
    }

    public static class StatusViewHolder extends RecyclerView.ViewHolder {
        private ThreadStatusCell threadStatusCell;

        public StatusViewHolder(ThreadStatusCell threadStatusCell) {
            super(threadStatusCell);
            this.threadStatusCell = threadStatusCell;
        }
    }

/*
    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= getCount() - 1) {
            onScrolledToBottom();
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
    }*/

    public interface PostAdapterCallback {
        void onFilteredResults(String filter, int count, boolean all);

        Loadable getLoadable();

        void onListScrolledToBottom();

        void scrollTo(int position);
    }
}
