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

import android.content.Context;
import android.content.Intent;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

    private final List<ChanThreadLoader> filterLoaders = new ArrayList<>();

    private long lastBackgroundUpdateTime;
    private Intent intent = new Intent("org.floens.chan.intent.action.FILTER_PIN_UPDATE");

    @Inject
    public FilterPinManager(WakeManager wakeManager, FilterEngine filterEngine, WatchManager watchManager, ChanLoaderFactory chanLoaderFactory, BoardRepository boardRepository) {
        this.wakeManager = wakeManager;
        this.filterEngine = filterEngine;
        this.watchManager = watchManager;
        this.chanLoaderFactory = chanLoaderFactory;
        this.boardRepository = boardRepository;

        if(ChanSettings.watchBackground.get() && ChanSettings.watchEnabled.get()){
            wakeManager.registerWakeable(intent, this);
        }

        EventBus.getDefault().register(this);
    }

    private void populateFilterLoaders() {
        filterLoaders.clear();
        //get our filters that are tagged as "pin"
        List<Filter> activeFilters = filterEngine.getEnabledPinFilters();
        //get a set of boards to background load
        Set<String> boardCodes = new HashSet<>();
        for(Filter f: activeFilters) {
            boardCodes.addAll(Arrays.asList(f.boardCodes()));
        }
        //create background loaders for each thing in the board set
        for(BoardRepository.SiteBoards siteBoard : boardRepository.getSaved().get()) {
            for(Board b : siteBoard.boards) {
                for(String code : boardCodes)
                    if(b.code.equals(code)) {
                        BackgroundLoader backgroundLoader = new BackgroundLoader();
                        ChanThreadLoader catalogLoader = chanLoaderFactory.obtain(Loadable.forCatalog(b), backgroundLoader);
                        filterLoaders.add(catalogLoader);
                    }
            }
        }
    }

    private void clearFilterLoaders() {
        filterLoaders.clear();
    }

    @Override
    public void onWake(Context context, Intent intent) {
        if (System.currentTimeMillis() - lastBackgroundUpdateTime < 90 * 1000) { //wait 90 seconds between background updates
            Logger.w(TAG, "Background update broadcast ignored because it was requested too soon");
        } else {
            populateFilterLoaders();
            lastBackgroundUpdateTime = System.currentTimeMillis();
            // load up boards listed as filter pins and check em
            for(ChanThreadLoader loader : filterLoaders) {
                loader.requestData();
            }
        }
    }

    // Called when either the background watch or watch enable settings are changed
    // Both must be enabled in order for filter pins to work
    public void onEvent(ChanSettings.SettingChanged<Boolean> settingChanged) {
        if (settingChanged.setting == ChanSettings.watchBackground || settingChanged.setting == ChanSettings.watchEnabled) {
            if(ChanSettings.watchBackground.get() && ChanSettings.watchEnabled.get()) {
                wakeManager.registerWakeable(intent, this);
            } else {
                wakeManager.unregisterWakeable(intent);
            }
        }
    }

    private class BackgroundLoader implements ChanThreadLoader.ChanLoaderCallback {

        @Override
        public void onChanLoaderData(ChanThread result) {
            List<Filter> filters = filterEngine.getEnabledPinFilters();
            for(Filter f : filters) {
                for(Post p : result.posts) {
                    if(filterEngine.matches(f, p) && p.filterPin) {
                        Loadable pinLoadable = Loadable.forThread(result.loadable.site, p.board, p.no);
                        watchManager.createPin(pinLoadable);
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
