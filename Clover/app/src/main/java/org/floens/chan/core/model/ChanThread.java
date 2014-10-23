package org.floens.chan.core.model;

import java.util.List;

public class ChanThread {
    public List<Post> posts;
    public Post op;
    public boolean closed = false;
    public boolean archived = false;

    public ChanThread(List<Post> posts) {
        this.posts = posts;
    }
}
