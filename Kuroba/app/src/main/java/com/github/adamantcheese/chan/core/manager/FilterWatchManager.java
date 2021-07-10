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

import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.FILTERS_CHANGED;

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
    private final Set<Integer> ignoredPosts = Collections.synchronizedSet(new HashSet<>());
    //keep track of how many boards we've checked and their posts so we can cut out things from the ignored posts
    private final AtomicInteger numBoardsChecked = new AtomicInteger();
    private final Set<Post> lastCheckedPosts = Collections.synchronizedSet(new HashSet<>());
    private boolean processing = false;

    public FilterWatchManager(
            BoardRepository boardRepository, FilterEngine filterEngine, WatchManager watchManager
    ) {
        this.boardRepository = boardRepository;
        this.filterEngine = filterEngine;
        this.watchManager = watchManager;

        if (!filterEngine.getEnabledWatchFilters().isEmpty()) {
            WakeManager.getInstance().registerWakeable(this);
        }

        Set<Integer> previousIgnore = AppModule.gson.fromJson(PersistableChanState.filterWatchIgnored.get(),
                new TypeToken<Set<Integer>>() {}.getType()
        );
        if (previousIgnore != null) ignoredPosts.addAll(previousIgnore);

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
                Logger.d(this,
                        "Processing " + numBoardsChecked + " filter loaders, started at "
                                + StringUtils.getCurrentTimeDefaultLocale()
                );
                for (ChanThreadLoader loader : filterLoaders.keySet()) {
                    loader.requestData();
                }
            } else {
                WakeManager.getInstance().manageLock(false, FilterWatchManager.this);
            }
        }
    }

    private void populateFilterLoaders() {
        for (Map.Entry<ChanThreadLoader, CatalogLoader> entry : filterLoaders.entrySet()) {
            ChanLoaderManager.release(entry.getKey(), entry.getValue());
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
            ChanThreadLoader catalogLoader = ChanLoaderManager.obtain(Loadable.forCatalog(b), backgroundLoader);
            filterLoaders.put(catalogLoader, backgroundLoader);
        }
    }

    private class CatalogLoader
            implements ChanThreadLoader.ChanLoaderCallback {
        @Override
        public void onChanLoaderData(ChanThread result) {
            Logger.d(this, "onChanLoaderData() for /" + result.getLoadable().boardCode + "/");
            for (Post p : result.getPosts()) {
                if (p.filterWatch && !ignoredPosts.contains(p.no)) {
                    final Loadable pinLoadable =
                            Loadable.forThread(p.board, p.no, PostHelper.getTitle(p, result.getLoadable()));
                    pinLoadable.thumbnailUrl = p.image() == null ? null : p.image().getThumbnailUrl();
                    BackgroundUtils.runOnMainThread(() -> watchManager.createPin(pinLoadable));
                    ignoredPosts.add(p.no);
                }
            }
            //add all posts to ignore
            lastCheckedPosts.addAll(result.getPosts());
            Logger.d(this, "Filter loader processed, left " + numBoardsChecked);
            checkComplete();
        }

        @Override
        public void onChanLoaderError(ChanThreadLoader.ChanLoaderException error) {
            Logger.d(this, "Filter loader failed, left " + numBoardsChecked);
            checkComplete();
        }

        private void checkComplete() {
            if (numBoardsChecked.decrementAndGet() == 0) {
                Set<Integer> lastCheckedPostNumbers = new HashSet<>();
                for (Post post : lastCheckedPosts) {
                    lastCheckedPostNumbers.add(post.no);
                }
                ignoredPosts.retainAll(lastCheckedPostNumbers);
                PersistableChanState.filterWatchIgnored.setSync(AppModule.gson.toJson(ignoredPosts));
                lastCheckedPosts.clear();
                processing = false;
                Logger.d(this,
                        "Finished processing filter loaders, ended at " + StringUtils.getCurrentTimeDefaultLocale()
                );
                WakeManager.getInstance().manageLock(false, FilterWatchManager.this);
            }
        }
    }
}
