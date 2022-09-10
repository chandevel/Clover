package com.github.adamantcheese.chan.ui.controller;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.*;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.ImageLoadable;
import com.github.adamantcheese.chan.ui.helper.RemovedPostsHelper;
import com.github.adamantcheese.chan.ui.view.ShapeablePostImageView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.*;

public class RemovedPostsController
        extends BaseFloatingController {
    private final RemovedPostsHelper removedPostsHelper;
    private RecyclerView postsListView;

    public RemovedPostsController(Context context, RemovedPostsHelper removedPostsHelper) {
        super(context);
        this.removedPostsHelper = removedPostsHelper;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view.setOnClickListener(v -> removedPostsHelper.pop());
        view.findViewById(R.id.removed_posts_restore_posts).setOnClickListener(v -> onRestoreClicked());
        //noinspection ConstantConditions
        view
                .findViewById(R.id.removed_posts_select_all)
                .setOnClickListener(v -> ((RemovedPostAdapter) postsListView.getAdapter()).selectAll());

        postsListView = view.findViewById(R.id.removed_posts_posts_list);
        postsListView.setAdapter(new RemovedPostAdapter(Collections.emptyList()));
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_removed_posts;
    }

    @Override
    public boolean onBack() {
        removedPostsHelper.pop();
        return true;
    }

    public void showRemovePosts(List<Post> removedPosts) {
        BackgroundUtils.ensureMainThread();

        List<RemovedPost> newRemovedPosts = new ArrayList<>();
        for (Post post : removedPosts) {
            newRemovedPosts.add(new RemovedPost(post));
        }

        postsListView.swapAdapter(new RemovedPostAdapter(newRemovedPosts), false);
    }

    private void onRestoreClicked() {
        //noinspection ConstantConditions
        List<Integer> selectedPosts = ((RemovedPostAdapter) postsListView.getAdapter()).getSelectedPostNoList();
        if (selectedPosts.isEmpty()) {
            return;
        }

        removedPostsHelper.onRestoreClicked(selectedPosts);
    }

    public static class RemovedPostAdapter
            extends RecyclerView.Adapter<RemovedPostCell> {
        private final List<RemovedPost> removedPosts;

        public RemovedPostAdapter(List<RemovedPost> removedPosts) {
            this.removedPosts = removedPosts;
        }

        @NonNull
        @Override
        public RemovedPostCell onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RemovedPostCell(LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.layout_removed_post, parent, false));
        }

        @Override
        public void onBindViewHolder(
                @NonNull RemovedPostCell holder, int position
        ) {
            RemovedPost removedPost = removedPosts.get(position);
            holder.postNo.setText(String.format(Locale.ENGLISH, "No. %d", removedPost.postNo));
            holder.postComment.setText(removedPost.comment);
            holder.checkbox.setChecked(removedPost.checked);

            if (!removedPost.images.isEmpty()) {
                // load only the first image
                PostImage image = removedPost.images.get(0);
                holder.postImage.setVisibility(VISIBLE);
                holder.loadUrl(image.getThumbnailUrl(), holder.postImage);
            } else {
                holder.postImage.setVisibility(GONE);
            }

            holder.checkbox.setOnClickListener(v -> onItemClick(removedPost));
            holder.itemView.setOnClickListener(v -> onItemClick(removedPost));
        }

        @Override
        public void onViewRecycled(
                @NonNull RemovedPostCell holder
        ) {
            holder.postNo.setText(null);
            holder.postComment.setText(null);
            holder.checkbox.setChecked(false);
            holder.postImage.setVisibility(VISIBLE);
            holder.cancelLoad(holder.postImage);
            holder.checkbox.setOnClickListener(null);
            holder.itemView.setOnClickListener(null);
        }

        @Override
        public int getItemCount() {
            return removedPosts.size();
        }

        public void onItemClick(RemovedPost removedPost) {
            removedPost.checked = !removedPost.checked;
            notifyItemChanged(removedPosts.indexOf(removedPost));
        }

        public List<Integer> getSelectedPostNoList() {
            List<Integer> selectedPosts = new ArrayList<>();

            for (RemovedPost removedPost : removedPosts) {
                if (removedPost.checked) selectedPosts.add(removedPost.postNo);
            }

            return selectedPosts;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void selectAll() {
            if (removedPosts.isEmpty()) return;

            // If first item is selected - unselect all other items
            // If it's not selected - select all other items
            RemovedPost rm0 = removedPosts.get(0);
            boolean select = rm0 != null && !rm0.checked;

            for (RemovedPost removedPost : removedPosts) {
                removedPost.checked = select;
            }

            notifyDataSetChanged();
        }
    }

    private static class RemovedPostCell
            extends RecyclerView.ViewHolder
            implements ImageLoadable {
        private final TextView postNo;
        private final TextView postComment;
        private final CheckBox checkbox;
        private final ShapeablePostImageView postImage;
        private ImageLoadableData data;

        public RemovedPostCell(@NonNull View itemView) {
            super(itemView);
            postNo = itemView.findViewById(R.id.removed_post_no);
            postComment = itemView.findViewById(R.id.removed_post_comment);
            checkbox = itemView.findViewById(R.id.removed_post_checkbox);
            postImage = itemView.findViewById(R.id.post_image);
        }

        @Override
        public ImageLoadableData getImageLoadableData() {
            return data;
        }

        @Override
        public void setImageLoadableData(ImageLoadableData data) {
            this.data = data;
        }
    }

    public static class RemovedPost {
        public final List<PostImage> images;
        public final int postNo;
        public final CharSequence comment;
        public boolean checked;

        public RemovedPost(Post post) {
            this.images = post.images;
            this.postNo = post.no;
            this.comment = post.comment;
            this.checked = false;
        }
    }
}
