package com.github.adamantcheese.chan.ui.helper;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;

import android.content.Context;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.ui.controller.RemovedPostsController;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.sql.SQLException;
import java.util.*;

public class RemovedPostsHelper {
    private final Context context;
    private final ThreadPresenter presenter;
    private final RemovedPostsCallbacks callbacks;
    private @Nullable
    RemovedPostsController controller;

    public RemovedPostsHelper(Context context, ThreadPresenter presenter, RemovedPostsCallbacks callbacks) {
        this.context = context;
        this.presenter = presenter;
        this.callbacks = callbacks;
    }

    public void showPosts(List<Post> threadPosts, int threadNo) {
        DatabaseUtils.runTask(() -> {
            List<Post> removedPosts = getRemovedPosts(threadPosts, threadNo);

            if (removedPosts.isEmpty()) {
                showToast(context, R.string.no_removed_posts_for_current_thread);
                return null;
            }

            Collections.sort(removedPosts, (o1, o2) -> Integer.compare(o1.no, o2.no));

            BackgroundUtils.runOnMainThread(() -> {
                if (controller == null) {
                    controller = new RemovedPostsController(context, this);
                    callbacks.presentController(controller);
                }

                controller.showRemovePosts(removedPosts);
            });

            return null;
        });
    }

    private List<Post> getRemovedPosts(List<Post> threadPosts, int threadNo)
            throws SQLException {
        List<PostHide> hiddenPosts = instance(DatabaseHideManager.class).getRemovedPostsWithThreadNo(threadNo);
        List<Post> removedPosts = new ArrayList<>();

        Map<Integer, PostHide> fastLookupMap = new HashMap<>();

        for (PostHide postHide : hiddenPosts) {
            fastLookupMap.put(postHide.no, postHide);
        }

        for (Post post : threadPosts) {
            if (fastLookupMap.containsKey(post.no)) {
                removedPosts.add(post);
            }
        }

        return removedPosts;
    }

    public void pop() {
        dismiss();
    }

    public void onRestoreClicked(List<Integer> selectedPosts) {
        presenter.onRestoreRemovedPostsClicked(selectedPosts);

        dismiss();
    }

    private void dismiss() {
        if (controller != null) {
            controller.stopPresenting();
            controller = null;
        }
    }

    public interface RemovedPostsCallbacks {
        void presentController(Controller controller);
    }
}
