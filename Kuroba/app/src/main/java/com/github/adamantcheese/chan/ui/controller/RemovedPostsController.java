package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.ui.helper.RemovedPostsHelper;
import com.github.adamantcheese.chan.ui.layout.RemovedPostLayout;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class RemovedPostsController
        extends BaseFloatingController {
    private final RemovedPostsHelper removedPostsHelper;

    public RemovedPostsController(Context context, RemovedPostsHelper removedPostsHelper) {
        super(context);
        this.removedPostsHelper = removedPostsHelper;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view.setOnClickListener(v -> removedPostsHelper.pop());
        view.findViewById(R.id.removed_posts_restore_posts).setOnClickListener(v -> onRestoreClicked());
        view.findViewById(R.id.removed_posts_select_all).setOnClickListener(v -> getAdapter().selectAll());

        ListView postsListView = view.findViewById(R.id.removed_posts_posts_list);
        RemovedPostAdapter adapter = new RemovedPostAdapter(context, R.layout.layout_removed_posts);
        postsListView.setAdapter(adapter);
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

    private RemovedPostAdapter getAdapter() {
        return (RemovedPostAdapter) ((ListView) view.findViewById(R.id.removed_posts_posts_list)).getAdapter();
    }

    public void showRemovePosts(List<Post> removedPosts) {
        BackgroundUtils.ensureMainThread();

        RemovedPost[] removedPostsArray = new RemovedPost[removedPosts.size()];

        for (int i = 0, removedPostsSize = removedPosts.size(); i < removedPostsSize; i++) {
            Post post = removedPosts.get(i);
            if (post == null) continue;
            removedPostsArray[i] = new RemovedPost(post.images, post.no, post.comment.toString(), false);
        }

        getAdapter().clear();
        getAdapter().addAll(removedPostsArray);
        getAdapter().notifyDataSetChanged();
    }

    private void onRestoreClicked() {
        List<Integer> selectedPosts = getAdapter().getSelectedPostNoList();
        if (selectedPosts.isEmpty()) {
            return;
        }

        removedPostsHelper.onRestoreClicked(selectedPosts);
    }

    public static class RemovedPost {
        public final List<PostImage> images;
        public final int postNo;
        public final String comment;
        public boolean checked;

        public RemovedPost(List<PostImage> images, int postNo, String comment, boolean checked) {
            this.images = images;
            this.postNo = postNo;
            this.comment = comment;
            this.checked = checked;
        }
    }

    public static class RemovedPostAdapter
            extends ArrayAdapter<RemovedPost> {
        public RemovedPostAdapter(@NonNull Context context, int resource) {
            super(context, resource);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            RemovedPost removedPost = getItem(position);
            RemovedPostLayout remLayout;

            if (removedPost == null) {
                throw new RuntimeException(
                        "removedPost is null! position = " + position + ", items count = " + getCount());
            }

            if (convertView == null) {
                remLayout = (RemovedPostLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.layout_removed_post, parent, false);
            } else {
                remLayout = (RemovedPostLayout) convertView;
            }

            remLayout.postNo.setText(String.format(Locale.ENGLISH, "No. %d", removedPost.postNo));
            remLayout.postComment.setText(removedPost.comment);
            remLayout.checkbox.setChecked(removedPost.checked);

            if (!removedPost.images.isEmpty()) {
                // load only the first image
                PostImage image = removedPost.images.get(0);
                remLayout.postImage.setVisibility(VISIBLE);
                remLayout.loadUrl(image.getThumbnailUrl(), remLayout.postImage);
            } else {
                remLayout.postImage.setVisibility(GONE);
            }

            remLayout.checkbox.setOnClickListener(v -> onItemClick(removedPost));
            remLayout.setOnClickListener(v -> onItemClick(removedPost));

            return remLayout;
        }

        public void onItemClick(RemovedPost removedPost) {
            removedPost.checked = !removedPost.checked;
            notifyDataSetChanged();
        }

        public List<Integer> getSelectedPostNoList() {
            List<Integer> selectedPosts = new ArrayList<>();

            for (int i = 0; i < getCount(); i++) {
                RemovedPost rm = getItem(i);
                if (rm == null) continue;
                if (rm.checked) {
                    selectedPosts.add(rm.postNo);
                }
            }

            return selectedPosts;
        }

        public void selectAll() {
            if (isEmpty()) return;

            // If first item is selected - unselect all other items
            // If it's not selected - select all other items
            RemovedPost rm0 = getItem(0);
            boolean select = rm0 != null && !rm0.checked;

            for (int i = 0; i < getCount(); ++i) {
                RemovedPost rm = getItem(i);
                if (rm == null) continue;
                rm.checked = select;
            }

            notifyDataSetChanged();
        }
    }
}
