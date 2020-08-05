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
import com.github.adamantcheese.chan.core.site.http.Reply;

import java.util.HashMap;
import java.util.Map;

public class LastReplyRepository {
    private static Map<Board, Long> lastReplyMap = new HashMap<>();
    private static Map<Board, Long> lastThreadMap = new HashMap<>();

    public static void putLastReply(Reply reply) {
        if (reply.loadable.isCatalogMode()) {
            lastThreadMap.put(reply.loadable.board, System.currentTimeMillis());
        } else {
            lastReplyMap.put(reply.loadable.board, System.currentTimeMillis());
        }
    }

    public static long getTimeUntilDraftPostable(Reply draft) {
        if (draft.loadable.isCatalogMode()) {
            return getTimeUntilThread(draft.loadable.board);
        } else {
            return getTimeUntilReply(draft.loadable.board, draft.file != null);
        }
    }

    /**
     * @param b        board for a new reply
     * @param hasImage if the reply has an image attached to it
     * @return seconds until a new reply can be posted on this board; negative if postable
     */
    private static long getTimeUntilReply(Board b, boolean hasImage) {
        Long lastTime = lastReplyMap.get(b);
        long lastReplyTime = lastTime != null ? lastTime : 0L;
        long waitTime = hasImage ? b.cooldownImages : b.cooldownReplies;
        if (b.site.actions().isLoggedIn()) waitTime /= 2;
        return waitTime - ((System.currentTimeMillis() - lastReplyTime) / 1000L);
    }

    /**
     * @param b board for a new thread
     * @return seconds until a new thread can be posted on this board; negative if postable
     */
    private static long getTimeUntilThread(Board b) {
        Long lastTime = lastThreadMap.get(b);
        long lastThreadTime = lastTime != null ? lastTime : 0L;
        long waitTime = b.cooldownThreads;
        if (b.site.actions().isLoggedIn()) waitTime /= 2;
        return waitTime - ((System.currentTimeMillis() - lastThreadTime) / 1000L);
    }
}
