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

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.Page;
import org.floens.chan.core.site.Pages;
import org.floens.chan.core.site.SiteActions;
import org.floens.chan.core.site.ThreadTime;
import org.floens.chan.utils.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PageRepository implements SiteActions.PagesListener {
    private static final String TAG = "PageRepository";

    private Set<String> savedBoards = Collections.synchronizedSet(new HashSet<>());
    private ConcurrentMap<String, Pages> boardPagesMap = new ConcurrentHashMap<>();
    private ConcurrentMap<String, Long> boardTimeMap = new ConcurrentHashMap<>();

    @Inject
    public PageRepository() {
    }

    public Page getPage(Post op) {
        Pages pages = getPages(op.board);
        if (pages == null) {
            return null;
        } else {
            for (Page page : pages.pages) {
                for (ThreadTime threadTime : page.threads) {
                    if (op.no == threadTime.no) {
                        return page;
                    }
                }
            }
        }
        return null;
    }

    private Pages getPages(Board b) {
        if (savedBoards.contains(b.code)) {
            //if we have it stored already, return the pages for it
            //also issue a new request if 3 minutes have passed
            long lastUpdateTime = boardTimeMap.get(b.code);
            if (lastUpdateTime + 3 * 1000 * 60 > System.currentTimeMillis()) {
                //this will spam the log if you turn it on
                //Logger.w(TAG, "Too early to request pages for this board");
            } else {
                Logger.d(TAG, "Requesting existing board pages, timeout");
                b.site.actions().pages(b, this);
            }
            return boardPagesMap.get(b.code);
        } else {
            //otherwise, get the site for the board and request the pages for it
            Logger.d(TAG, "Requesting new board pages");
            b.site.actions().pages(b, this);
            return null;
        }
    }

    @Override
    public void onPagesReceived(Board b, Pages pages) {
        Logger.d(TAG, "Got pages for " + b.site.name() + " /" + b.code + "/");
        savedBoards.add(b.code);
        boardTimeMap.put(b.code, System.currentTimeMillis());
        boardPagesMap.put(b.code, pages);
    }
}
