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
package com.github.adamantcheese.chan.core.manager;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PageRequestManager implements SiteActions.PagesListener {
    private static final String TAG = "PageRequestManager";
    private static final int THREE_MINUTES = 3 * 60 * 1000;

    private Set<String> requestedBoards = Collections.synchronizedSet(new HashSet<>());
    private Set<String> savedBoards = Collections.synchronizedSet(new HashSet<>());
    private ConcurrentMap<String, Chan4PagesRequest.Pages> boardPagesMap = new ConcurrentHashMap<>();
    private ConcurrentMap<String, Long> boardTimeMap = new ConcurrentHashMap<>();

    private List<PageCallback> callbackList = new ArrayList<>();

    public Chan4PagesRequest.Page getPage(Post op) {
        if (op == null) {
            return null;
        }
        return findPage(op.board, op.no);
    }

    public Chan4PagesRequest.Page getPage(Loadable opLoadable) {
        if (opLoadable == null) {
            return null;
        }
        return findPage(opLoadable.board, opLoadable.no);
    }

    public void forceUpdateForBoard(Board b) {
        Logger.d(TAG, "Requesting existing board pages, forced");
        requestBoard(b);
    }

    private Chan4PagesRequest.Page findPage(Board board, int opNo) {
        Chan4PagesRequest.Pages pages = getPages(board);
        if (pages == null) return null;
        for (Chan4PagesRequest.Page page : pages.pages) {
            for (Chan4PagesRequest.ThreadNoTimeModPair threadNoTimeModPair : page.threads) {
                if (opNo == threadNoTimeModPair.no) {
                    return page;
                }
            }
        }
        return null;
    }

    private Chan4PagesRequest.Pages getPages(Board b) {
        if (savedBoards.contains(b.code)) {
            //if we have it stored already, return the pages for it
            //also issue a new request if 3 minutes have passed
            shouldUpdate(b);
            return boardPagesMap.get(b.code);
        } else {
            //otherwise, get the site for the board and request the pages for it
            Logger.d(TAG, "Requesting new board pages");
            requestBoard(b);
            return null;
        }
    }

    private void shouldUpdate(Board b) {
        if (b == null) return; //if for any reason the board is null, don't do anything
        Long lastUpdate = boardTimeMap.get(b.code); //had some null issues for some reason? arisuchan in particular?
        long lastUpdateTime = lastUpdate != null ? lastUpdate : 0L;
        if (lastUpdateTime + THREE_MINUTES <= System.currentTimeMillis()) {
            Logger.d(TAG, "Requesting existing board pages, timeout");
            requestBoard(b);
        }
    }

    private void requestBoard(Board b) {
        synchronized (this) {
            if (!requestedBoards.contains(b.code)) {
                requestedBoards.add(b.code);
                b.site.actions().pages(b, this);
            } else {
                Logger.d(TAG, "Board /" + b.code + "/ has already been requested");
            }
        }
    }

    public void addListener(PageCallback callback) {
        if (callback != null) {
            callbackList.add(callback);
        }
    }

    public void removeListener(PageCallback callback) {
        if (callback != null) {
            callbackList.remove(callback);
        }
    }

    @Override
    public void onPagesReceived(Board b, Chan4PagesRequest.Pages pages) {
        Logger.d(TAG, "Got pages for " + b.site.name() + " /" + b.code + "/");
        savedBoards.add(b.code);
        requestedBoards.remove(b.code);
        boardTimeMap.put(b.code, System.currentTimeMillis());
        boardPagesMap.put(b.code, pages);

        for (PageCallback callback : callbackList) {
            callback.onPagesReceived();
        }
    }

    public interface PageCallback {
        void onPagesReceived();
    }
}
