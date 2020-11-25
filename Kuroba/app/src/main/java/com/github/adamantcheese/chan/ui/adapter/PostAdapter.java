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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_LAST_SEEN;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST_STUB;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_STATUS;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

public class PostAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    enum CellType {
        TYPE_POST,
        TYPE_STATUS,
        TYPE_POST_STUB,
        TYPE_LAST_SEEN
    }

    private final PostAdapterCallback postAdapterCallback;
    private final PostCellInterface.PostCellCallback postCellCallback;
    private final RecyclerView recyclerView;

    private final ThreadStatusCell.Callback statusCellCallback;
    private final List<Post> displayList = new ArrayList<>();

    private Loadable loadable = null;
    private String error = null;
    private String highlightedId;
    private int highlightedNo = -1;
    private String highlightedTripcode;
    private String searchQuery;
    private int lastSeenIndicatorPosition = -1;

    private ChanSettings.PostViewMode postViewMode;
    private boolean compact = false;
    private final Theme theme;

    public PostAdapter(
            RecyclerView recyclerView,
            PostAdapterCallback postAdapterCallback,
            PostCellInterface.PostCellCallback postCellCallback,
            ThreadStatusCell.Callback statusCellCallback,
            Theme theme
    ) {
        this.recyclerView = recyclerView;
        this.postAdapterCallback = postAdapterCallback;
        this.postCellCallback = postCellCallback;
        this.statusCellCallback = statusCellCallback;
        this.theme = theme;

        setHasStableIds(true);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context inflateContext = parent.getContext();
        switch (CellType.values()[viewType]) {
            case TYPE_POST:
                int layout = 0;
                switch (getPostViewMode()) {
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
        switch (CellType.values()[itemViewType]) {
            case TYPE_POST:
            case TYPE_POST_STUB:
                if (loadable == null) {
                    throw new IllegalStateException("Loadable cannot be null");
                }

                PostViewHolder postViewHolder = (PostViewHolder) holder;
                Post post = displayList.get(getPostPosition(position));
                ((PostCellInterface) postViewHolder.itemView).setPost(
                        loadable,
                        post,
                        postCellCallback,
                        isInPopup(),
                        shouldHighlight(post),
                        getMarkedNo(),
                        showDivider(position),
                        getPostViewMode(),
                        isCompact(),
                        searchQuery,
                        theme,
                        recyclerView
                );

                if (itemViewType == TYPE_POST_STUB.ordinal() && postAdapterCallback != null) {
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
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        //this is a hack to make sure text is selectable
        super.onViewAttachedToWindow(holder);
        try {
            holder.itemView.findViewById(R.id.comment).setEnabled(false);
            holder.itemView.findViewById(R.id.comment).setEnabled(true);
        } catch (Exception ignored) {}
    }

    public boolean isInPopup() {
        return false;
    }

    public boolean shouldHighlight(Post post) {
        return post.id.equals(highlightedId) || post.no == highlightedNo || post.tripcode.equals(highlightedTripcode);
    }

    public int getMarkedNo() {
        return -1;
    }

    public boolean showDivider(int position) {
        return true;
    }

    public ChanSettings.PostViewMode getPostViewMode() {
        return postViewMode;
    }

    @Override
    public int getItemCount() {
        return displayList.size() + (showStatusView() ? 1 : 0) + (lastSeenIndicatorPosition >= 0 ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == lastSeenIndicatorPosition) {
            return TYPE_LAST_SEEN.ordinal();
        } else if (showStatusView() && position == getItemCount() - 1) {
            return TYPE_STATUS.ordinal();
        } else {
            Post post = displayList.get(getPostPosition(position));
            if (post.filterStub) {
                return TYPE_POST_STUB.ordinal();
            } else {
                return TYPE_POST.ordinal();
            }
        }
    }

    @Override
    public long getItemId(int position) {
        int itemViewType = getItemViewType(position);
        if (itemViewType == TYPE_STATUS.ordinal()) {
            return -2;
        } else if (itemViewType == TYPE_LAST_SEEN.ordinal()) {
            return -3;
        } else {
            return displayList.get(getPostPosition(position)).no;
        }
    }

    public void setThread(ChanThread thread, PostsFilter filter) {
        BackgroundUtils.ensureMainThread();

        this.loadable = thread.getLoadable();
        this.searchQuery = filter == null ? null : filter.getQuery();

        showError(null);

        displayList.clear();
        displayList.addAll(filter == null ? thread.getPosts() : filter.apply(thread));

        lastSeenIndicatorPosition = -1;
        // Do not process the last post, the indicator does not have to appear at the bottom
        for (int i = 0; i < displayList.size() - 1; i++) {
            if (displayList.get(i).no == loadable.lastViewed) {
                lastSeenIndicatorPosition = i + 1;
                break;
            }
        }

        notifyDataSetChanged();
    }

    public void setLastSeenIndicatorPosition(int position) {
        lastSeenIndicatorPosition = position;
        notifyDataSetChanged();
    }

    public List<Post> getDisplayList() {
        return displayList;
    }

    public void cleanup() {
        highlightedId = null;
        highlightedNo = -1;
        highlightedTripcode = null;
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
                    ((ThreadStatusCell) child).setError(error);
                }
            }
        }
    }

    public void highlightPostId(String id) {
        highlightedId = id;
        highlightedNo = -1;
        highlightedTripcode = null;
        notifyDataSetChanged();
    }

    public void highlightPostTripcode(String tripcode) {
        highlightedId = null;
        highlightedNo = -1;
        highlightedTripcode = tripcode;
        notifyDataSetChanged();
    }

    public void highlightPostNo(int no) {
        highlightedId = null;
        highlightedNo = no;
        highlightedTripcode = null;
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

    public boolean isCompact() {
        return compact;
    }

    /**
     * Given a viewholder position, translate it to a displaylist position.
     */
    public int getPostPosition(int position) {
        int postPosition = position;
        if (lastSeenIndicatorPosition >= 0 && position > lastSeenIndicatorPosition) {
            postPosition--;
        }
        return postPosition;
    }

    /**
     * Given a displaylist position, translate it to a viewholder position.
     */
    public int getScrollPosition(int displayPosition) {
        int postPosition = displayPosition;
        if (lastSeenIndicatorPosition >= 0 && displayPosition > lastSeenIndicatorPosition) {
            postPosition++;
        }
        return postPosition;
    }

    public boolean showStatusView() {
        if (postAdapterCallback == null) return false;
        // the loadable can be null while this adapter is used between cleanup and the removal
        // of the recyclerview from the view hierarchy, although it's rare.
        return loadable != null && loadable.isThreadMode();
    }

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

    public interface PostAdapterCallback {
        void onUnhidePostClick(Post post);
    }
}
