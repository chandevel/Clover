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
package com.github.adamantcheese.chan.core.model;

import com.github.adamantcheese.chan.core.model.orm.Loadable;

import java.util.ArrayList;
import java.util.List;

public class ChanThread {
    private Loadable loadable;
    private List<Post> posts;
    private Post op;
    private boolean closed = false;
    private boolean archived = false;

    public ChanThread(Loadable loadable, List<Post> posts) {
        this.loadable = loadable;
        this.posts = posts;
    }

    public synchronized int getPostsCount() {
        return posts.size();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized boolean isArchived() {
        return archived;
    }

    public synchronized void setClosed(boolean closed) {
        this.closed = closed;
    }

    public synchronized void setArchived(boolean archived) {
        this.archived = archived;
    }

    /**
     * This allocates additional memory. Only use it when you need to change the collection in some way.
     */
    public synchronized List<Post> getPosts() {
        return new ArrayList<>(posts);
    }

    /**
     * To avoid posts allocations.
     * Not safe! Only use for read-only operations like iteration, etc!
     */
    public synchronized List<Post> getPostsUnsafe() {
        return posts;
    }

    public synchronized void setNewPosts(List<Post> newPosts) {
        posts.clear();
        posts.addAll(newPosts);
    }

    public synchronized int getLoadableId() {
        return loadable.id;
    }

    public synchronized void updateLoadableState(Loadable.LoadableDownloadingState state) {
        loadable.loadableDownloadingState = state;
    }

    /**
     * Not safe! Only use for read-only operations!
     */
    public synchronized Post getOp() {
        return posts.get(0);
    }

    public synchronized void setOp(Post op) {
        this.op = op;
    }

    /**
     * For now it is like this because there are a lot of places that will have to changed to make
     * this safe
     */
    public Loadable getLoadable() {
        return loadable;
    }
}
