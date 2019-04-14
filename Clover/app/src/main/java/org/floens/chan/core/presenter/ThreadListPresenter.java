package org.floens.chan.core.presenter;

import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.adapter.PostsFilter;

import java.util.ArrayList;
import java.util.List;

public class ThreadListPresenter {

    /**
     * Filters out hidden posts. This function may be called from the main thread.
     * */
    public List<Post> filterOutHiddenPosts(ChanThread thread, PostsFilter filter) {
        List<Post> sourceList = new ArrayList<>(thread.posts);

        return filter.apply(
                sourceList,
                thread.loadable.site.id(),
                thread.loadable.board.code
        );
    }

    public void destroy() {

    }
}
