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
package org.floens.chan.core.site;


import com.android.volley.RequestQueue;

import org.floens.chan.core.database.LoadableProvider;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.json.site.SiteConfig;
import org.floens.chan.core.model.json.site.SiteUserSettings;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.http.HttpCallManager;

import java.util.Collections;

import dagger.ObjectGraph;

import static org.floens.chan.Chan.getGraph;

public abstract class SiteBase implements Site {
    protected int id;
    protected SiteConfig config;
    protected SiteUserSettings userSettings;

    protected HttpCallManager httpCallManager;
    protected RequestQueue requestQueue;
    protected BoardManager boardManager;
    protected LoadableProvider loadableProvider;

    @Override
    public void initialize(int id, SiteConfig config, SiteUserSettings userSettings) {
        this.id = id;
        this.config = config;
        this.userSettings = userSettings;
    }

    @Override
    public void postInitialize() {
        ObjectGraph graph = getGraph();

        httpCallManager = graph.get(HttpCallManager.class);
        requestQueue = graph.get(RequestQueue.class);
        boardManager = graph.get(BoardManager.class);
        loadableProvider = graph.get(LoadableProvider.class);

        if (boardsType() == BoardsType.DYNAMIC) {
            boards(boards -> boardManager.createAll(boards.boards));
        }
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public Board board(String code) {
        return boardManager.getBoard(this, code);
    }

    @Override
    public Board createBoard(String name, String code) {
        Board existing = board(code);
        if (existing != null) {
            return existing;
        }

        Board board = Board.fromSiteNameCode(this, name, code);
        boardManager.createAll(Collections.singletonList(board));
        return board;
    }

    @Override
    public boolean postRequiresAuthentication() {
        return false;
    }
}
