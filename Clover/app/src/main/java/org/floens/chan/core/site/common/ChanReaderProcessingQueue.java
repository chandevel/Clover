package org.floens.chan.core.site.common;


import android.annotation.SuppressLint;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Loadable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ChanReaderProcessingQueue {
    @SuppressLint("UseSparseArrays")
    private Map<Integer, Post> cachedByNo = new HashMap<>();
    private Loadable loadable;

    private List<Post> toReuse = new ArrayList<>();
    private List<Post.Builder> toParse = new ArrayList<>();
    private Post.Builder op;

    public ChanReaderProcessingQueue(List<Post> toReuse, Loadable loadable) {
        this.loadable = loadable;

        for (int i = 0; i < toReuse.size(); i++) {
            Post cache = toReuse.get(i);
            cachedByNo.put(cache.no, cache);
        }
    }

    public Post getCachedPost(int no) {
        return cachedByNo.get(no);
    }

    public void addForReuse(Post post) {
        toReuse.add(post);
    }

    public void addForParse(Post.Builder postBuilder) {
        toParse.add(postBuilder);
    }

    public void setOp(Post.Builder op) {
        this.op = op;
    }

    public Loadable getLoadable() {
        return loadable;
    }

    List<Post> getToReuse() {
        return toReuse;
    }

    List<Post.Builder> getToParse() {
        return toParse;
    }

    Post.Builder getOp() {
        return op;
    }
}
