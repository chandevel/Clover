package org.floens.chan.ui.helper;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.ui.fragment.PostRepliesFragment;

import java.util.ArrayList;
import java.util.List;

public class PostPopupHelper {
    private Context context;
    private ThreadPresenter presenter;

    private final List<RepliesData> dataQueue = new ArrayList<>();
    private PostRepliesFragment currentPopupFragment;

    public PostPopupHelper(Context context, ThreadPresenter presenter) {
        this.context = context;
        this.presenter = presenter;
    }

    public void showPosts(Post forPost, List<Post> posts) {
        RepliesData data = new RepliesData(forPost, posts);

        dataQueue.add(data);

        if (currentPopupFragment != null) {
            currentPopupFragment.dismissNoCallback();
        }

        presentFragment(data);
    }

    public void onPostRepliesPop() {
        if (dataQueue.size() == 0)
            return;

        dataQueue.remove(dataQueue.size() - 1);

        if (dataQueue.size() > 0) {
            presentFragment(dataQueue.get(dataQueue.size() - 1));
        } else {
            currentPopupFragment = null;
        }
    }

    public void closeAllPostFragments() {
        dataQueue.clear();
        if (currentPopupFragment != null) {
            currentPopupFragment.dismissNoCallback();
            currentPopupFragment = null;
        }
    }

    public void postClicked(Post p) {
        closeAllPostFragments();
        presenter.highlightPost(p.no);
        presenter.scrollToPost(p.no);
    }

    private void presentFragment(RepliesData data) {
        PostRepliesFragment fragment = PostRepliesFragment.newInstance(data, this, presenter);
        // TODO fade animations on all platforms
        FragmentTransaction ft = ((Activity) context).getFragmentManager().beginTransaction();
        ft.add(fragment, "postPopup");
        ft.commitAllowingStateLoss();
        currentPopupFragment = fragment;
    }

    public static class RepliesData {
        public List<Post> posts;
        public Post forPost;
        public int listViewIndex;
        public int listViewTop;

        public RepliesData(Post forPost, List<Post> posts) {
            this.forPost = forPost;
            this.posts = posts;
        }
    }
}
