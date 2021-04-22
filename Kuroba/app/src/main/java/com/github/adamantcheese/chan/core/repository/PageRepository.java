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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPages;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ThreadNoTimeModPair;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.concurrent.TimeUnit.MINUTES;

public class PageRepository {
    private static final Set<Board> requestedBoards = Collections.synchronizedSet(new HashSet<>());
    private static final Set<Board> savedBoards = Collections.synchronizedSet(new HashSet<>());
    private static final ConcurrentMap<Board, ChanPages> boardPagesMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Board, Long> boardTimeMap = new ConcurrentHashMap<>();

    private static final List<PageCallback> callbackList = new ArrayList<>();

    public static ChanPage getPage(@NonNull Post op) {
        return findPage(op.board, op.no);
    }

    public static ChanPage getPage(@NonNull Loadable opLoadable) {
        return findPage(opLoadable.board, opLoadable.no);
    }

    public static void forceUpdateForBoard(final Board b) {
        if (b != null) {
            BackgroundUtils.runOnBackgroundThread(() -> requestBoard(b), 10000);
        }
    }

    private static ChanPage findPage(Board board, int opNo) {
        ChanPages pages = getPages(board);
        if (pages == null) return null;
        for (ChanPage page : pages) {
            for (ThreadNoTimeModPair threadNoTimeModPair : page.threads) {
                if (opNo == threadNoTimeModPair.no) {
                    return page;
                }
            }
        }
        return null;
    }

    private static ChanPages getPages(Board b) {
        if (savedBoards.contains(b)) {
            //if we have it stored already, return the pages for it
            //also issue a new request if 3 minutes have passed
            shouldUpdate(b);
            return boardPagesMap.get(b);
        } else {
            //otherwise, get the site for the board and request the pages for it
            requestBoard(b);
            return null;
        }
    }

    private static void shouldUpdate(Board b) {
        if (b == null) return; //if for any reason the board is null, don't do anything
        Long lastUpdate = boardTimeMap.get(b);
        long lastUpdateTime = lastUpdate != null ? lastUpdate : 0L;
        if (lastUpdateTime + MINUTES.toMillis(3) <= System.currentTimeMillis()) {
            requestBoard(b);
        }
    }

    private static synchronized void requestBoard(final Board b) {
        if (!requestedBoards.contains(b)) {
            requestedBoards.add(b);
            b.site.actions().pages(b, (NetUtilsClasses.NoFailResponseResult<ChanPages>) result -> addPages(b, result));
        }
    }

    private static synchronized void onPagesReceived(Board board, ChanPages pages) {
        savedBoards.add(board);
        requestedBoards.remove(board);
        boardTimeMap.put(board, System.currentTimeMillis());
        boardPagesMap.put(board, pages);

        for (PageCallback callback : callbackList) {
            callback.onPagesReceived();
        }
    }

    public static void addPages(Board board, ChanPages pages) {
        onPagesReceived(board, pages);
    }

    public static void addListener(PageCallback callback) {
        if (callback != null) {
            callbackList.add(callback);
        }
    }

    public static void removeListener(PageCallback callback) {
        if (callback != null) {
            callbackList.remove(callback);
        }
    }

    public interface PageCallback {
        void onPagesReceived();
    }
}
