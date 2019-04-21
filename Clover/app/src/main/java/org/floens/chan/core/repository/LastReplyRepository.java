/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.repository;

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.Site;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LastReplyRepository {
    private static final String TAG = "LastReplyRepository";

    private Map<SiteBoard, Long> lastReplyMap = new HashMap<>();

    @Inject
    public LastReplyRepository() {
    }

    public void putLastReply(Site s, Board b) {
        lastReplyMap.put(new SiteBoard(s, b), System.currentTimeMillis());
    }

    public long getLastReply(Site s, Board b) {
        Long lastTime = lastReplyMap.get(new SiteBoard(s, b));
        return lastTime != null ? lastTime : 0L;
    }

    public boolean canPost(Site s, Board b) {
        return getLastReply(s, b) + 60 * 1000 < System.currentTimeMillis();
    }

    private class SiteBoard {
        public String site;
        public String boardCode;

        public SiteBoard (Site site, Board board) {
            this.site = site.name();
            this.boardCode = board.code;
        }

        @Override
        public boolean equals(Object o) {
            if(!(o instanceof SiteBoard)) return false;
            SiteBoard sb = (SiteBoard) o;
            return sb.boardCode.equals(this.boardCode) && sb.site.equals(this.site);
        }

        @Override
        public int hashCode() {
            return (site + boardCode).hashCode();
        }
    }
}
