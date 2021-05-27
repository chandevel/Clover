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
package com.github.adamantcheese.chan.core.site.parser;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChanReaderProcessingQueue {
    private final Map<Integer, Post> cachedByNo = new HashMap<>();
    public final Loadable loadable;

    private final List<Post> toReuse = new ArrayList<>();
    private final List<Post.Builder> toParse = new ArrayList<>();
    private Post.Builder op;

    public ChanReaderProcessingQueue(List<Post> toReuse, Loadable loadable) {
        this.loadable = loadable;

        for (Post cache : toReuse) {
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

    List<Post> getToReuse() {
        return toReuse;
    }

    List<Post.Builder> getToParse() {
        return toParse;
    }

    public Post.Builder getOp() {
        return op;
    }
}
