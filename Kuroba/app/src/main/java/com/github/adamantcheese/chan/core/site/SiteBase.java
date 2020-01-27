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
package com.github.adamantcheese.chan.core.site;

import com.android.volley.RequestQueue;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.settings.SettingProvider;
import com.github.adamantcheese.chan.core.settings.json.JsonSettings;
import com.github.adamantcheese.chan.core.settings.json.JsonSettingsProvider;
import com.github.adamantcheese.chan.core.site.http.HttpCallManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.instance;

public abstract class SiteBase
        implements Site {
    protected int id;
    protected SiteConfig config;

    protected HttpCallManager httpCallManager;
    protected RequestQueue requestQueue;
    protected BoardManager boardManager;
    protected SettingProvider settingsProvider;
    private JsonSettings userSettings;
    private boolean initialized = false;

    @Override
    public void initialize(int id, SiteConfig config, JsonSettings userSettings) {
        this.id = id;
        this.config = config;
        this.userSettings = userSettings;

        if (initialized) {
            throw new IllegalStateException();
        }
        initialized = true;
    }

    @Override
    public void postInitialize() {
        httpCallManager = instance(HttpCallManager.class);
        requestQueue = instance(RequestQueue.class);
        boardManager = instance(BoardManager.class);
        SiteService siteService = instance(SiteService.class);

        settingsProvider =
                new JsonSettingsProvider(userSettings, () -> siteService.updateUserSettings(this, userSettings));

        initializeSettings();

        if (boardsType().canList) {
            actions().boards(boards -> boardManager.updateAvailableBoardsForSite(this, boards.boards));
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
    public List<SiteSetting> settings() {
        return new ArrayList<>();
    }

    public void initializeSettings() {
    }

    @Override
    public Board createBoard(String name, String code) {
        Board existing = board(code);
        if (existing != null) {
            return existing;
        }

        Board board = Board.fromSiteNameCode(this, name, code);
        boardManager.updateAvailableBoardsForSite(this, Collections.singletonList(board));
        return board;
    }

    public static boolean containsMediaHostUrl(HttpUrl desiredSiteUrl, String[] mediaHosts) {
        String host = desiredSiteUrl.host();

        for (String mediaHost : mediaHosts) {
            if (host.equals(mediaHost)) {
                return true;
            }

            if (host.equals("www." + mediaHost)) {
                return true;
            }
        }

        return false;
    }

    public static class Boards {
        public final List<Board> boards;

        public Boards(List<Board> boards) {
            this.boards = boards;
        }
    }
}
