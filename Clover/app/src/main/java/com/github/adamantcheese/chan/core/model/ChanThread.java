/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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

import java.util.List;

public class ChanThread {
    public Loadable loadable;
    public List<Post> posts;
    public Post op;
    public boolean closed = false;
    public boolean archived = false;

    public ChanThread(Loadable loadable, List<Post> posts) {
        this.loadable = loadable;
        this.posts = posts;
    }
}
