/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;

public class PostAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    //we don't recycle POST cells because of layout changes between cell contents
    public static final int TYPE_POST = 0;
    private static final int TYPE_STATUS = 1;
    private static final int TYPE_POST_STUB = 2;
    private static final int TYPE_LAST_SEEN = 3;

    private final PostAdapterCallback postAdapterCallback;
    private final PostCellInterface.PostCellCallback postCellCallback;
    private RecyclerView recyclerView;

    private final ThreadStatusCell.Callback statusCellCallback;
    private final List<Post> displayList = new ArrayList<>();

    private Loadable loadable = null;
    private String error = null;
    private Post highlightedPost;
    private String highlightedPostId;
    private int highlightedPostNo = -1;
    private String highlightedPostTripcode;
    private int selectedPost = -1;
    private int lastSeenIndicatorPosition = -1;

    private ChanSettings.PostViewMode postViewMode;
    private boolean compact = false;

    public PostAdapter(
            RecyclerView recyclerView,
            PostAdapterCallback postAdapterCallback,
            PostCellInterface.PostCellCallback postCellCallback,
            ThreadStatusCell.Callback statusCellCallback
    ) {
        this.recyclerView = recyclerView;
        this.postAdapterCallback = postAdapterCallback;
        this.postCellCallback = postCellCallback;
        this.statusCellCallback = statusCellCallback;

        setHasStableIds(true);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context inflateContext = parent.getContext();
        switch (viewType) {
            case TYPE_POST:
                int layout = 0;
                switch (postViewMode) {
                    case LIST:
                        layout = R.layout.cell_post;
                        break;
                    case CARD:
                        layout = R.layout.cell_post_card;
                        break;
                }

                PostCellInterface postCell = (PostCellInterface) inflate(inflateContext, layout, parent, false);
                return new PostViewHolder(postCell);
            case TYPE_POST_STUB:
                PostCellInterface postCellStub =
                        (PostCellInterface) inflate(inflateContext, R.layout.cell_post_stub, parent, false);
                return new PostViewHolder(postCellStub);
            case TYPE_LAST_SEEN:
                return new LastSeenViewHolder(inflate(inflateContext, R.layout.cell_post_last_seen, parent, false));
            case TYPE_STATUS:
                ThreadStatusCell statusCell =
                        (ThreadStatusCell) inflate(inflateContext, R.layout.cell_thread_status, parent, false);
                StatusViewHolder statusViewHolder = new StatusViewHolder(statusCell);
                statusCell.setCallback(statusCellCallback);
                statusCell.setError(error);
                return statusViewHolder;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int itemViewType = getItemViewType(position);
        switch (itemViewType) {
            case TYPE_POST:
            case TYPE_POST_STUB:
                if (loadable == null) {
                    throw new IllegalStateException("Loadable cannot be null");
                }

                PostViewHolder postViewHolder = (PostViewHolder) holder;
                Post post = displayList.get(getPostPosition(position));
                boolean highlight =
                        post == highlightedPost || post.id.equals(highlightedPostId) || post.no == highlightedPostNo
                                || post.tripcode.equals(highlightedPostTripcode);
                ((PostCellInterface) postViewHolder.itemView).setPost(
                        loadable,
                        post,
                        postCellCallback,
                        false,
                        highlight,
                        post.no == selectedPost,
                        -1,
                        true,
                        postViewMode,
                        compact,
                        ThemeHelper.getTheme()
                );

                if (itemViewType == TYPE_POST_STUB) {
                    holder.itemView.setOnClickListener(v -> postAdapterCallback.onUnhidePostClick(post));
                }
                break;
            case TYPE_STATUS:
                ((ThreadStatusCell) holder.itemView).update();
                break;
            case TYPE_LAST_SEEN:
                break;
        }
    }

    @Override
    public int getItemCount() {
        int size = displayList.size();

        if (showStatusView()) {
            size++;
        }

        if (lastSeenIndicatorPosition >= 0) {
            size++;
        }

        return size;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == lastSeenIndicatorPosition) {
            return TYPE_LAST_SEEN;
        } else if (showStatusView() && position == getItemCount() - 1) {
            return TYPE_STATUS;
        } else {
            Post post = displayList.get(getPostPosition(position));
            if (post.filterStub) {
                return TYPE_POST_STUB;
            } else {
                return TYPE_POST;
            }
        }
    }

    @Override
    public long getItemId(int position) {
        int itemViewType = getItemViewType(position);
        if (itemViewType == TYPE_STATUS) {
            return -1;
        } else if (itemViewType == TYPE_LAST_SEEN) {
            return -2;
        } else {
            Post post = displayList.get(getPostPosition(position));
            int repliesFromSize;
            synchronized (post.repliesFrom) {
                repliesFromSize = post.repliesFrom.size();
            }
            return ((long) repliesFromSize << 32L) + (long) post.no + (compact ? 1L : 0L);
        }
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        //this is a hack to make sure text is selectable
        super.onViewAttachedToWindow(holder);
        if (holder.itemView instanceof PostCell) {
            PostCell cell = (PostCell) holder.itemView;
            cell.findViewById(R.id.comment).setEnabled(false);
            cell.findViewById(R.id.comment).setEnabled(true);
        }
    }

    public void setThread(Loadable threadLoadable, List<Post> posts, boolean refreshAfterHideOrRemovePosts) {
        BackgroundUtils.ensureMainThread();
        boolean changed = this.loadable != null && !this.loadable.equals(threadLoadable); //changed threads, update

        this.loadable = threadLoadable;
        showError(null);

        int lastLastSeenIndicator = lastSeenIndicatorPosition;
        if (displayList.size() == posts.size()) {
            for (int i = 0; i < displayList.size(); i++) {
                if (!displayList.get(i).equals(posts.get(i))) {
                    changed = true; //posts are different, or a post got deleted and needs to be updated
                    break;
                }
            }
        } else {
            changed = true; //new posts or fewer posts, update
        }

        displayList.clear();
        displayList.addAll(posts);

        lastSeenIndicatorPosition = -1;
        if (threadLoadable.lastViewed >= 0) {
            // Do not process the last post, the indicator does not have to appear at the bottom
            for (int i = 0, displayListSize = displayList.size() - 1; i < displayListSize; i++) {
                Post post = displayList.get(i);
                if (post.no == threadLoadable.lastViewed) {
                    lastSeenIndicatorPosition = i + 1;
                    break;
                }
            }
        }

        //update for indicator (adds/removes extra recycler item that causes inconsistency exceptions)
        //or if something changed per reasons above
        if (lastLastSeenIndicator != lastSeenIndicatorPosition || changed
                // When true that means that the user has just hid or removed post/thread
                // so we need to refresh the UI
                || refreshAfterHideOrRemovePosts) {
            notifyDataSetChanged();
        }
    }

    public List<Post> getDisplayList() {
        return displayList;
    }

    public void cleanup() {
        highlightedPost = null;
        highlightedPostId = null;
        highlightedPostNo = -1;
        highlightedPostTripcode = null;
        selectedPost = -1;
        lastSeenIndicatorPosition = -1;
        error = null;
    }

    public void showError(String error) {
        this.error = error;
        if (showStatusView()) {
            final int childCount = recyclerView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = recyclerView.getChildAt(i);
                if (child instanceof ThreadStatusCell) {
                    ThreadStatusCell threadStatusCell = (ThreadStatusCell) child;
                    threadStatusCell.setError(error);
                    threadStatusCell.update();
                }
            }
        }
    }

    public void highlightPost(Post post) {
        highlightedPost = post;
        highlightedPostId = null;
        highlightedPostNo = -1;
        highlightedPostTripcode = null;
        notifyDataSetChanged();
    }

    public void highlightPostId(String id) {
        highlightedPost = null;
        highlightedPostId = id;
        highlightedPostNo = -1;
        highlightedPostTripcode = null;
        notifyDataSetChanged();
    }

    public void highlightPostTripcode(String tripcode) {
        highlightedPost = null;
        highlightedPostId = null;
        highlightedPostNo = -1;
        highlightedPostTripcode = tripcode;
        notifyDataSetChanged();
    }

    public void highlightPostNo(int no) {
        highlightedPost = null;
        highlightedPostId = null;
        highlightedPostNo = no;
        highlightedPostTripcode = null;
        notifyDataSetChanged();
    }

    public void selectPost(int no) {
        selectedPost = no;
        notifyDataSetChanged();
    }

    public void setPostViewMode(ChanSettings.PostViewMode postViewMode) {
        this.postViewMode = postViewMode;
    }

    public void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
            notifyDataSetChanged();
        }
    }

    public int getPostPosition(int position) {
        int postPosition = position;
        if (lastSeenIndicatorPosition >= 0 && position > lastSeenIndicatorPosition) {
            postPosition--;
        }
        return postPosition;
    }

    public int getScrollPosition(int displayPosition) {
        int postPosition = displayPosition;
        if (lastSeenIndicatorPosition >= 0 && displayPosition > lastSeenIndicatorPosition) {
            postPosition++;
        }
        return postPosition;
    }

    private boolean showStatusView() {
        Loadable loadable = postAdapterCallback.getLoadable();
        // the loadable can be null while this adapter is used between cleanup and the removal
        // of the recyclerview from the view hierarchy, although it's rare.
        return loadable != null && loadable.isThreadMode();
    }

    //region Holders
    public static class PostViewHolder
            extends RecyclerView.ViewHolder {
        public PostViewHolder(PostCellInterface postView) {
            super((View) postView);
        }
    }

    public static class StatusViewHolder
            extends RecyclerView.ViewHolder {
        public StatusViewHolder(ThreadStatusCell threadStatusCell) {
            super(threadStatusCell);
        }
    }

    public static class LastSeenViewHolder
            extends RecyclerView.ViewHolder {
        public LastSeenViewHolder(View itemView) {
            super(itemView);
        }
    }
    //endregion

    public interface PostAdapterCallback {
        Loadable getLoadable();

        void onUnhidePostClick(Post post);
    }
}
