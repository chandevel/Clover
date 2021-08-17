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
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.RecyclerUtils;

import java.util.ArrayList;
import java.util.List;

import static android.view.View.GONE;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.LIST;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.STAGGER;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_CARD;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_CARD_STAGGER;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_CARD_STUB;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_CARD_STUB_STAGGER;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST_FLIP;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST_FLIP_STUB;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST_STUB;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_STATUS;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class PostAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    enum CellType {
        TYPE_POST(R.layout.cell_post, false),
        TYPE_POST_FLIP(R.layout.cell_post_flip, false),
        TYPE_POST_STUB(R.layout.cell_post_stub, true),
        TYPE_POST_FLIP_STUB(R.layout.cell_post_stub, true),
        TYPE_CARD(R.layout.cell_post_card, false),
        TYPE_CARD_STUB(R.layout.cell_post_stub_card, true),
        TYPE_CARD_STAGGER(R.layout.cell_post_card_stagger, false),
        TYPE_CARD_STUB_STAGGER(R.layout.cell_post_stub_card, true),
        TYPE_STATUS(R.layout.cell_thread_status, false);

        int layoutResId;
        boolean isStub;

        CellType(int layoutId, boolean stub) {
            layoutResId = layoutId;
            isStub = stub;
        }
    }

    private final PostAdapterCallback postAdapterCallback;
    private final PostCellInterface.PostCellCallback postCellCallback;
    private final RecyclerView recycler;

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
        this.recycler = recyclerView;
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
                        int dividerBottom = (int) (dividerTop + dp(4));

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
                    outRect.top = (int) dp(4);
                }
            }
        };
    }

    @Override
    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CellType inflateType = CellType.values()[viewType];
        View inflated = LayoutInflater.from(parent.getContext()).inflate(inflateType.layoutResId, parent, false);
        if (inflateType != TYPE_STATUS) {
            return new PostViewHolder((PostCellInterface) inflated);
        } else {
            ThreadStatusCell statusCell = (ThreadStatusCell) inflated;
            statusCell.setCallback(statusCellCallback);
            return new StatusViewHolder(statusCell);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (loadable == null) {
            throw new IllegalStateException("Loadable cannot be null");
        }

        CellType cellType = CellType.values()[getItemViewType(position)];
        if (cellType != TYPE_STATUS) {
            PostViewHolder postViewHolder = (PostViewHolder) holder;
            final Post post = displayList.get(position);

            if (!cellType.isStub) {
                // apply embedding
                boolean embedInProgress = EmbeddingEngine.getInstance()
                        .embed(theme, post, () -> recycler.post(() -> notifyItemChanged(position, new Object())));
                // no embeds, cleanup/finalize
                if (!embedInProgress) {
                    for (PostLinkable linkable : post.getLinkables()) {
                        linkable.callback = this::allowsDashedUnderlines;
                    }
                    holder.itemView.findViewById(R.id.embed_spinner).setVisibility(GONE);
                }
                doSetPost(postViewHolder, post, false); // set after
            } else {
                doSetPost(postViewHolder, post, false); // set before
                if (postAdapterCallback != null) {
                    holder.itemView.setOnClickListener(v -> postAdapterCallback.onUnhidePostClick(post));
                }
            }
        } else {
            ((ThreadStatusCell) holder.itemView).update();
        }
    }

    private void doSetPost(PostViewHolder holder, @NonNull Post post, boolean clearEmbed) {
        ((PostCellInterface) holder.itemView).setPost(loadable,
                post,
                postCellCallback,
                isInPopup(),
                shouldHighlight(post),
                isCompact(),
                theme
        );
        if (clearEmbed) {
            holder.itemView.findViewById(R.id.embed_spinner).setVisibility(GONE);
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads
    ) {
        if (payloads.isEmpty() || payloads.get(0) == null) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }
        CellType cellType = CellType.values()[holder.getItemViewType()];
        if (cellType != TYPE_STATUS) {
            doSetPost((PostViewHolder) holder, displayList.get(position), !cellType.isStub);
        } else {
            ((ThreadStatusCell) holder.itemView).setError(payloads.get(0).toString());
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder.getItemViewType() == TYPE_POST.ordinal() || holder.getItemViewType() == TYPE_POST_FLIP.ordinal()) {
            //this is a hack to make sure text is selectable
            holder.itemView.findViewById(R.id.comment).setEnabled(false);
            holder.itemView.findViewById(R.id.comment).setEnabled(true);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        CellType cellType = CellType.values()[holder.getItemViewType()];
        if (cellType != TYPE_STATUS) {
            PostCellInterface postView = (PostCellInterface) holder.itemView;
            if (!cellType.isStub) {
                holder.itemView.findViewById(R.id.embed_spinner).setVisibility(View.VISIBLE);
                Post post = postView.getPost();
                post.stopEmbedding(); // before the post is cleared out
                for (PostLinkable linkable : post.getLinkables()) {
                    linkable.callback = null;
                }
            }
            postView.unsetPost();
        } else {
            ((ThreadStatusCell) holder.itemView).setError(null);
        }
    }

    public boolean isInPopup() {
        return false;
    }

    public boolean shouldHighlight(@NonNull Post post) {
        return post.id.equals(highlightedId) || post.no == highlightedNo || post.tripcode.equals(highlightedTripcode);
    }

    public boolean allowsDashedUnderlines() {
        return false;
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
                if (postViewMode == LIST) {
                    return ChanSettings.flipPostCells.get() ? TYPE_POST_FLIP_STUB.ordinal() : TYPE_POST_STUB.ordinal();
                } else {
                    return postViewMode == STAGGER ? TYPE_CARD_STUB_STAGGER.ordinal() : TYPE_CARD_STUB.ordinal();
                }
            } else {
                if (postViewMode == LIST) {
                    return ChanSettings.flipPostCells.get() ? TYPE_POST_FLIP.ordinal() : TYPE_POST.ordinal();
                } else {
                    return postViewMode == STAGGER ? TYPE_CARD_STAGGER.ordinal() : TYPE_CARD.ordinal();
                }
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

        this.loadable = thread.getLoadable();

        List<Post> newList = newFilter == null ? thread.getPosts() : newFilter.apply(thread);
        currentFilter = newFilter == null ? new PostsFilter(PostsFilter.Order.BUMP, null) : newFilter;

        lastSeenIndicatorPosition = Integer.MIN_VALUE;
        // Do not process the last post, the indicator does not have to appear at the bottom
        for (int i = 0; i < newList.size() - 1; i++) {
            if (newList.get(i).no == loadable.lastViewed) {
                lastSeenIndicatorPosition = i;
                break;
            }
        }

        displayList.clear();
        displayList.addAll(newList);
        notifyDataSetChanged();
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
