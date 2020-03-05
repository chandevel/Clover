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
package com.github.adamantcheese.chan.core.repository;

import com.github.adamantcheese.chan.core.model.orm.Board;

import java.util.HashMap;
import java.util.Map;

public class LastReplyRepository {
    private Map<Board, Long> lastReplyMap = new HashMap<>();
    private Map<Board, Long> lastThreadMap = new HashMap<>();

    public void putLastReply(Board b) {
        lastReplyMap.put(b, System.currentTimeMillis());
    }

    /**
     * @param b        board for a new reply
     * @param hasImage if the reply has an image attached to it
     * @return seconds until a new reply can be posted on this board; negative if postable
     */
    public long getTimeUntilReply(Board b, boolean hasImage) {
        Long lastTime = lastReplyMap.get(b);
        long lastReplyTime = lastTime != null ? lastTime : 0L;
        long waitTime = hasImage ? b.cooldownImages : b.cooldownReplies;
        if (b.site.actions().isLoggedIn()) waitTime /= 2;
        return waitTime - ((System.currentTimeMillis() - lastReplyTime) / 1000L);
    }

    public void putLastThread(Board b) {
        lastThreadMap.put(b, System.currentTimeMillis());
    }

    /**
     * @param b board for a new thread
     * @return seconds until a new thread can be posted on this board; negative if postable
     */
    public long getTimeUntilThread(Board b) {
        Long lastTime = lastThreadMap.get(b);
        long lastThreadTime = lastTime != null ? lastTime : 0L;
        long waitTime = b.cooldownThreads;
        if (b.site.actions().isLoggedIn()) waitTime /= 2;
        return waitTime - ((System.currentTimeMillis() - lastThreadTime) / 1000L);
    }
}
