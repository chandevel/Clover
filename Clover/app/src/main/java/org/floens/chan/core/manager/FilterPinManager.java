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
package org.floens.chan.core.manager;

import org.floens.chan.core.database.DatabaseLoadableManager;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.exception.ChanLoaderException;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Filter;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.pool.ChanLoaderFactory;
import org.floens.chan.core.repository.BoardRepository;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.site.loader.ChanThreadLoader;
import org.floens.chan.utils.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import de.greenrobot.event.EventBus;

@Singleton
public class FilterPinManager implements WakeManager.Wakeable {
    private static final String TAG = "FilterPinManager";

    private final WakeManager wakeManager;
    private final FilterEngine filterEngine;
    private final WatchManager watchManager;
    private final ChanLoaderFactory chanLoaderFactory;
    private final BoardRepository boardRepository;
    private final DatabaseLoadableManager databaseLoadableManager;

    private final Map<ChanThreadLoader, BackgroundLoader> filterLoaders = new HashMap<>();

    @Inject
    public FilterPinManager(WakeManager wakeManager, FilterEngine filterEngine, WatchManager watchManager, ChanLoaderFactory chanLoaderFactory, BoardRepository boardRepository, DatabaseManager databaseManager) {
        this.wakeManager = wakeManager;
        this.filterEngine = filterEngine;
        this.watchManager = watchManager;
        this.chanLoaderFactory = chanLoaderFactory;
        this.boardRepository = boardRepository;
        this.databaseLoadableManager = databaseManager.getDatabaseLoadableManager();

        if(ChanSettings.watchFilterPin.get()) {
            wakeManager.registerWakeable(this);
        }

        EventBus.getDefault().register(this);
    }

    public void onEvent(ChanSettings.SettingChanged<Boolean> settingChanged) {
        if(settingChanged.setting == ChanSettings.watchFilterPin) {
            if(ChanSettings.watchFilterPin.get()) {
                wakeManager.registerWakeable(this);
            } else {
                wakeManager.unregisterWakeable(this);
            }
        }
    }

    private void populateFilterLoaders() {
        Logger.d(TAG, "Populating filter loaders");
        clearFilterLoaders();
        //get our filters that are tagged as "pin"
        List<Filter> activeFilters = filterEngine.getEnabledPinFilters();
        //get a set of boards to background load
        Set<String> boardCodes = new HashSet<>();
        for(Filter f: activeFilters) {
            //if the allBoards flag is set for any one filter, add all saved boards to the set
            if(f.allBoards) {
                for(BoardRepository.SiteBoards s : boardRepository.getSaved().get()) {
                    for(Board b : s.boards) {
                        boardCodes.add(b.code);
                    }
                }
                //shortcut out if any filter has the allBoards flag
                break;
            }
            boardCodes.addAll(Arrays.asList(f.boardCodes()));
        }
        //create background loaders for each thing in the board set
        for(BoardRepository.SiteBoards siteBoard : boardRepository.getSaved().get()) {
            for(Board b : siteBoard.boards) {
                for(String code : boardCodes)
                    if(b.code.equals(code)) {
                        BackgroundLoader backgroundLoader = new BackgroundLoader();
                        Loadable boardLoadable = Loadable.forCatalog(b);
                        boardLoadable = databaseLoadableManager.get(boardLoadable);
                        ChanThreadLoader catalogLoader = chanLoaderFactory.obtain(boardLoadable, backgroundLoader);
                        filterLoaders.put(catalogLoader, backgroundLoader);
                    }
            }
        }
    }

    private void clearFilterLoaders() {
        if(filterLoaders.isEmpty()) {
            return;
        }
        for(ChanThreadLoader loader : filterLoaders.keySet()) {
            chanLoaderFactory.release(loader, filterLoaders.get(loader));
        }
        filterLoaders.clear();
    }

    @Override
    public void onWake() {
        populateFilterLoaders();
        for(ChanThreadLoader loader : filterLoaders.keySet()) {
            loader.requestData();
        }
    }

    private class BackgroundLoader implements ChanThreadLoader.ChanLoaderCallback {

        @Override
        public void onChanLoaderData(ChanThread result) {
            Logger.d("BACKGROUND LOADER", "Got data");
            List<Filter> filters = filterEngine.getEnabledPinFilters();
            for(Filter f : filters) {
                for(Post p : result.posts) {
                    if(filterEngine.matches(f, p) && p.filterPin) {
                        Loadable pinLoadable = Loadable.forThread(result.loadable.site, p.board, p.no);
                        pinLoadable = databaseLoadableManager.get(pinLoadable);
                        watchManager.createPin(pinLoadable, p);

                    }
                }
            }
        }

        @Override
        public void onChanLoaderError(ChanLoaderException error) {
            //ignore all errors
        }
    }
}
