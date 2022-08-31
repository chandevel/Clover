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

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.FILTERS_CHANGED;

import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.*;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.utils.*;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FilterWatchManager
        implements WakeManager.Wakeable {
    private final BoardRepository boardRepository;
    private final FilterEngine filterEngine;
    private final WatchManager watchManager;

    //filterLoaders keeps track of ChanThreadLoaders so they can be cleared correctly each alarm trigger
    //ignoredPosts keeps track of threads pinned by the filter manager and ignores them for future alarm triggers
    //this lets you unpin threads that are pinned by the filter pin manager and not have them come back
    //note that ignoredPosts is currently only saved while the application is running and not in the database
    private final Map<ChanThreadLoader, CatalogLoader> filterLoaders = new HashMap<>();
    private final Set<CatalogPost> ignoredPosts = Collections.synchronizedSet(new HashSet<>());
    //keep track of how many boards we've checked and their posts so we can cut out things from the ignored posts
    private final AtomicInteger numBoardsChecked = new AtomicInteger();
    private final Set<CatalogPost> lastCheckedPosts = Collections.synchronizedSet(new HashSet<>());
    private boolean processing = false;

    private static final Type IGNORED_TYPE = new TypeToken<Set<CatalogPost>>() {}.getType();
    private static final File CACHED_IGNORES = new File(getCacheDir(), "filter_watch_ignores.json");

    public FilterWatchManager(
            BoardRepository boardRepository, FilterEngine filterEngine, WatchManager watchManager
    ) {
        this.boardRepository = boardRepository;
        this.filterEngine = filterEngine;
        this.watchManager = watchManager;

        if (!filterEngine.getEnabledWatchFilters().isEmpty()) {
            WakeManager.getInstance().registerWakeable(this);
        }

        try (FileReader reader = new FileReader(CACHED_IGNORES)) {
            Set<CatalogPost> previousIgnore = AppModule.gson.fromJson(reader, IGNORED_TYPE);
            if (previousIgnore != null) ignoredPosts.addAll(previousIgnore);
        } catch (Exception e) {
            CACHED_IGNORES.delete(); // bad file probably
        }

        EventBus.getDefault().register(this);
    }

    @Subscribe
    public void onEvent(RefreshUIMessage message) {
        if (message.reason != FILTERS_CHANGED) return;
        if (filterEngine.getEnabledWatchFilters().isEmpty()) {
            WakeManager.getInstance().unregisterWakeable(this);
        } else {
            WakeManager.getInstance().registerWakeable(this);
        }
    }

    @Override
    public void onWake() {
        if (!processing) {
            WakeManager.getInstance().manageLock(true, FilterWatchManager.this);
            processing = true;
            populateFilterLoaders();
            if (!filterLoaders.keySet().isEmpty()) {
                Logger.d(
                        this,
                        "Processing "
                                + numBoardsChecked
                                + " filter loaders, started at "
                                + StringUtils.getCurrentTimeDefaultLocale()
                );
                for (ChanThreadLoader loader : filterLoaders.keySet()) {
                    loader.requestFreshData();
                }
            } else {
                WakeManager.getInstance().manageLock(false, FilterWatchManager.this);
            }
        }
    }

    private void populateFilterLoaders() {
        for (Map.Entry<ChanThreadLoader, CatalogLoader> entry : filterLoaders.entrySet()) {
            entry.getKey().removeListener(entry.getValue());
        }
        filterLoaders.clear();
        //get a set of boards to background load
        Set<Board> boards = new HashSet<>();
        for (BoardRepository.SiteBoards siteBoard : boardRepository.getSaved()) {
            for (Board b : siteBoard.boards) {
                for (Filter f : filterEngine.getEnabledWatchFilters()) {
                    if (filterEngine.matchesBoard(f, b)) {
                        boards.add(b);
                    }
                }
            }
        }
        numBoardsChecked.set(boards.size());

        for (Board b : boards) {
            CatalogLoader backgroundLoader = new CatalogLoader();
            ChanThreadLoader catalogLoader = new ChanThreadLoader(Loadable.forCatalog(b));
            catalogLoader.addListener(backgroundLoader);
            filterLoaders.put(catalogLoader, backgroundLoader);
        }
    }

    private class CatalogLoader
            implements NetUtilsClasses.ResponseResult<ChanThread> {
        @Override
        public void onSuccess(ChanThread result) {
            Logger.d(this, "onChanLoaderData() for /" + result.loadable.boardCode + "/");
            for (Post p : result.getPosts()) {
                CatalogPost catalogPost = new CatalogPost(p);
                //make pins for the necessary stuff
                if (p.filterWatch && !ignoredPosts.contains(catalogPost)) {
                    final Loadable pinLoadable =
                            Loadable.forThread(p.board, p.no, PostHelper.getTitle(p, result.loadable));
                    pinLoadable.thumbnailUrl = p.image() == null ? null : p.image().getThumbnailUrl();
                    BackgroundUtils.runOnMainThread(() -> watchManager.createPin(pinLoadable));
                    ignoredPosts.add(catalogPost);
                }
                //add all posts to ignore
                lastCheckedPosts.add(catalogPost);
            }
            Logger.d(this, "Filter loader processed, left " + numBoardsChecked);
            checkComplete();
        }

        @Override
        public void onFailure(Exception error) {
            Logger.d(this, "Filter loader failed, left " + numBoardsChecked);
            checkComplete();
        }

        private void checkComplete() {
            if (numBoardsChecked.decrementAndGet() == 0) {
                ignoredPosts.retainAll(lastCheckedPosts);
                try (FileWriter writer = new FileWriter(CACHED_IGNORES)) {
                    AppModule.gson.toJson(ignoredPosts, IGNORED_TYPE, writer);
                } catch (Exception e) {
                    CACHED_IGNORES.delete();
                }
                lastCheckedPosts.clear();
                processing = false;
                Logger.d(
                        this,
                        "Finished processing filter loaders, ended at " + StringUtils.getCurrentTimeDefaultLocale()
                );
                WakeManager.getInstance().manageLock(false, FilterWatchManager.this);
            }
        }
    }

    private static class CatalogPost {
        private final int siteId;
        private final String boardCode;
        private final int no;

        public CatalogPost(Post p) {
            siteId = p.board.site.id();
            boardCode = p.boardCode;
            no = p.no;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CatalogPost that = (CatalogPost) o;
            return Objects.equals(siteId, that.siteId)
                    && Objects.equals(boardCode, that.boardCode)
                    && Objects.equals(no, that.no);
        }

        @Override
        public int hashCode() {
            return Objects.hash(siteId, boardCode, no);
        }
    }
}
