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

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.RecyclerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static android.view.View.GONE;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.LIST;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST_STUB;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_STATUS;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class PostAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    enum CellType {
        TYPE_POST,
        TYPE_STATUS,
        TYPE_POST_STUB
    }

    private final PostAdapterCallback postAdapterCallback;
    private final PostCellInterface.PostCellCallback postCellCallback;
    private final RecyclerView recyclerView;

    private final ThreadStatusCell.Callback statusCellCallback;
    private final List<Post> displayList = new ArrayList<>();

    private Loadable loadable = null;
    private String highlightedId;
    private int highlightedNo = -1;
    private String highlightedTripcode;
    public int lastSeenIndicatorPosition = Integer.MIN_VALUE;
    private PostsFilter currentFilter = new PostsFilter(PostsFilter.Order.BUMP, null);

    private ChanSettings.PostViewMode postViewMode = LIST;
    private boolean compact = false;
    private final Theme theme;
    private final RecyclerView.ItemDecoration cellDivider;
    private final RecyclerView.ItemDecoration lastSeenDivider;

    public PostAdapter(
            RecyclerView recyclerView,
            PostAdapterCallback postAdapterCallback,
            PostCellInterface.PostCellCallback postCellCallback,
            ThreadStatusCell.Callback statusCellCallback,
            Theme theme
    ) {
        recyclerView.setItemAnimator(null);
        this.recyclerView = recyclerView;
        this.postAdapterCallback = postAdapterCallback;
        this.postCellCallback = postCellCallback;
        this.statusCellCallback = statusCellCallback;
        this.theme = theme;
        setHasStableIds(true);

        cellDivider = RecyclerUtils.getBottomDividerDecoration(recyclerView.getContext());
        final ShapeDrawable lastSeen = new ShapeDrawable();
        lastSeen.setTint(getAttrColor(recyclerView.getContext(), R.attr.colorAccent));

        // Last seen decoration
        lastSeenDivider = new RecyclerView.ItemDecoration() {
            @Override
            public void onDrawOver(
                    @NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state
            ) {
                super.onDrawOver(c, parent, state);
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (parent.getChildAdapterPosition(child) == lastSeenIndicatorPosition) {
                        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

                        int dividerTop = child.getBottom() + params.bottomMargin;
                        int dividerBottom = dividerTop + dp(4);

                        lastSeen.setBounds(0, dividerTop, parent.getWidth(), dividerBottom);
                        lastSeen.draw(c);
                    }
                }
            }

            @Override
            public void getItemOffsets(
                    @NonNull Rect outRect,
                    @NonNull View view,
                    @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state
            ) {
                super.getItemOffsets(outRect, view, parent, state);
                if (parent.getChildAdapterPosition(view) == lastSeenIndicatorPosition) {
                    outRect.top = dp(4);
                }
            }
        };
    }

    @Override
    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = 0;
        CellType inflateType = CellType.values()[viewType];
        switch (inflateType) {
            case TYPE_POST:
            case TYPE_POST_STUB:
                switch (postViewMode) {
                    case LIST:
                        layout = inflateType == TYPE_POST ? R.layout.cell_post : R.layout.cell_post_stub;
                        break;
                    case GRID:
                        layout = inflateType == TYPE_POST ? R.layout.cell_post_card : R.layout.cell_post_stub_card;
                        break;
                }

                PostCellInterface postCell =
                        (PostCellInterface) LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
                return new PostViewHolder(postCell);
            case TYPE_STATUS:
                ThreadStatusCell statusCell = (ThreadStatusCell) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cell_thread_status, parent, false);
                StatusViewHolder statusViewHolder = new StatusViewHolder(statusCell);
                statusCell.setCallback(statusCellCallback);
                return statusViewHolder;
            default:
                throw new IllegalStateException("Unknown view holder");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (loadable == null) {
            throw new IllegalStateException("Loadable cannot be null");
        }
        Post post = position == displayList.size() ? null : displayList.get(position);
        CellType cellType = CellType.values()[getItemViewType(position)];
        switch (cellType) {
            case TYPE_POST:
                PostViewHolder postViewHolder = (PostViewHolder) holder;

                ((PostCellInterface) postViewHolder.itemView).setPost(loadable,
                        post,
                        postCellCallback,
                        isInPopup(),
                        shouldHighlight(post),
                        isCompact(),
                        theme
                );
                // apply embedding
                boolean embedInProgress = EmbeddingEngine.getInstance()
                        .embed(theme, post, () -> recyclerView.post(() -> notifyItemChanged(position)));
                // no embeds
                if (!embedInProgress) {
                    holder.itemView.findViewById(R.id.embed_spinner).setVisibility(GONE);
                }
                // PostCell with shift on and no embedding and not yet shifted gets a predraw listener to shift stuff (this will occur after embedding, if any)
                if (postViewMode == LIST && ChanSettings.shiftPostFormat.get() && !embedInProgress
                        && !postViewHolder.shifted) {
                    if (postViewHolder.shifter != null) {
                        postViewHolder.shifter.removeListener();
                    }
                    postViewHolder.shifter = OneShotPreDrawListener.add(postViewHolder.itemView, () -> {
                        postViewHolder.shifted = true;
                        PostCell cell = (PostCell) postViewHolder.itemView;
                        cell.doShiftPostFormatting();
                    });
                }

                break;
            case TYPE_POST_STUB:
                PostViewHolder postStubViewHolder = (PostViewHolder) holder;
                ((PostCellInterface) postStubViewHolder.itemView).setPost(loadable,
                        post,
                        postCellCallback,
                        isInPopup(),
                        shouldHighlight(post),
                        isCompact(),
                        theme
                );

                if (postAdapterCallback != null) {
                    holder.itemView.setOnClickListener(v -> postAdapterCallback.onUnhidePostClick(post));
                }
                break;
            case TYPE_STATUS:
                ((ThreadStatusCell) holder.itemView).update();
                break;
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }
        CellType cellType = CellType.values()[getItemViewType(position)];
        if (cellType == TYPE_STATUS) {
            String error = payloads.get(0) == null ? null : (String) payloads.get(0);
            ((ThreadStatusCell) holder.itemView).setError(error);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder.getItemViewType() == TYPE_POST.ordinal()) {
            //this is a hack to make sure text is selectable
            holder.itemView.findViewById(R.id.comment).setEnabled(false);
            holder.itemView.findViewById(R.id.comment).setEnabled(true);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        switch (CellType.values()[holder.getItemViewType()]) {
            case TYPE_POST:
                PostViewHolder postViewHolder = (PostViewHolder) holder;
                if (postViewHolder.shifter != null) {
                    postViewHolder.shifter.removeListener();
                    postViewHolder.shifter = null;
                }
                postViewHolder.shifted = false;
                holder.itemView.findViewById(R.id.embed_spinner).setVisibility(View.VISIBLE);
                ((PostCellInterface) holder.itemView).getPost().stopEmbedding(); // before the post is cleared out
                //noinspection fallthrough
            case TYPE_POST_STUB:
                ((PostCellInterface) holder.itemView).unsetPost();
                break;
            case TYPE_STATUS:
                ((ThreadStatusCell) holder.itemView).setError(null);
                break;
        }
    }

    public boolean isInPopup() {
        return false;
    }

    public boolean shouldHighlight(Post post) {
        return post.id.equals(highlightedId) || post.no == highlightedNo || post.tripcode.equals(highlightedTripcode);
    }

    @Override
    public int getItemCount() {
        return displayList.size() + (showStatusView() ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (showStatusView() && position == getItemCount() - 1) {
            return TYPE_STATUS.ordinal();
        } else {
            Post post = displayList.get(position);
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
        } else {
            return displayList.get(position).no;
        }
    }

    public void setThread(ChanThread thread, PostsFilter newFilter) {
        BackgroundUtils.ensureMainThread();

        boolean prevUseStatusView = showStatusView();
        boolean loadableChanged = !thread.getLoadable().equals(loadable);
        this.loadable = thread.getLoadable();

        List<Post> newList = newFilter == null ? thread.getPosts() : newFilter.apply(thread);
        boolean filterChanged = !Objects.equals(currentFilter, newFilter);
        currentFilter = newFilter;

        boolean newUseStatusView = showStatusView();

        lastSeenIndicatorPosition = Integer.MIN_VALUE;
        // Do not process the last post, the indicator does not have to appear at the bottom
        for (int i = 0; i < newList.size() - 1; i++) {
            if (newList.get(i).no == loadable.lastViewed) {
                lastSeenIndicatorPosition = i;
                break;
            }
        }

        // shortcut for performance, if the loadable has changed just invalidate everything
        if (loadableChanged) {
            displayList.clear();
            displayList.addAll(newList);
            notifyDataSetChanged();
            return;
        }

        // otherwise, use a diff to calculate adapter changes more efficiently and not refresh items that haven't changed
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            // +1 for status cells
            @Override
            public int getOldListSize() {
                return displayList.size() + (prevUseStatusView ? 1 : 0);
            }

            @Override
            public int getNewListSize() {
                return newList.size() + (newUseStatusView ? 1 : 0);
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Post oldPost = oldItemPosition >= displayList.size() ? null : displayList.get(oldItemPosition);
                Post newPost = newItemPosition >= newList.size() ? null : newList.get(newItemPosition);
                // if one of these posts is out-of-range, return true so that we can check contents
                // we want to call notifyItemChanged in this case, and that's how to do it with this method
                if (oldPost == null || newPost == null) return true;
                return oldPost.no == newPost.no;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                // if the filter has changed, all other locations have new contents
                if (filterChanged) return false;

                Post oldPost = oldItemPosition >= displayList.size() ? null : displayList.get(oldItemPosition);
                Post newPost = newItemPosition >= newList.size() ? null : newList.get(newItemPosition);

                if (oldPost == null || newPost == null) return false;
                // check equality of posts to see if they have changed
                return oldPost.equals(newPost);
            }
        });

        displayList.clear();
        displayList.addAll(newList);

        result.dispatchUpdatesTo(this); // better than notifyDataSetChanged for small UI updates, but can also act as a full refresh if needed
    }

    public List<Post> getDisplayList() {
        return displayList;
    }

    public void cleanup() {
        highlightedId = null;
        highlightedNo = -1;
        highlightedTripcode = null;
        lastSeenIndicatorPosition = Integer.MIN_VALUE;
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

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.removeItemDecoration(cellDivider);
        recyclerView.removeItemDecoration(lastSeenDivider);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        if (postViewMode == LIST) {
            recyclerView.addItemDecoration(cellDivider);
            recyclerView.addItemDecoration(lastSeenDivider);
        } else {
            recyclerView.removeItemDecoration(cellDivider);
            recyclerView.removeItemDecoration(lastSeenDivider);
        }
    }

    public void setCompact(boolean compact) {
        this.compact = compact;
    }

    public boolean isCompact() {
        return compact;
    }

    public boolean showStatusView() {
        // the loadable can be null while this adapter is used between cleanup and the removal
        // of the recyclerview from the view hierarchy, although it's rare.
        // also don't show the status view if there's a search query going
        return postAdapterCallback != null && TextUtils.isEmpty(currentFilter.getQuery()) && loadable != null
                && loadable.isThreadMode();
    }

    public static class PostViewHolder
            extends RecyclerView.ViewHolder {
        // used for shift-post formatting, only for PostCell instances of postView
        public OneShotPreDrawListener shifter = null;
        public boolean shifted = false;

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

    public interface PostAdapterCallback {
        void onUnhidePostClick(Post post);
    }
}
