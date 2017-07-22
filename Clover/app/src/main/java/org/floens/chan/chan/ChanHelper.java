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
package org.floens.chan.chan;

import android.net.Uri;

import org.floens.chan.core.database.DatabaseLoadableManager;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.Sites;

import java.util.List;

import static org.floens.chan.Chan.getGraph;

public class ChanHelper {
    public static Loadable getLoadableFromStartUri(Uri uri) {
        Loadable loadable = null;

        List<String> parts = uri.getPathSegments();

        // TODO(multi-site) get correct site
        Site site = Sites.defaultSite();

        if (parts.size() > 0) {
            String rawBoard = parts.get(0);
            DatabaseManager databaseManager = getGraph().get(DatabaseManager.class);
            DatabaseLoadableManager loadableManager = databaseManager.getDatabaseLoadableManager();
            Board board = site.board(rawBoard);
            if (board != null) {
                if (parts.size() == 1 || (parts.size() == 2 && "catalog".equals(parts.get(1)))) {
                    // Board mode
                    loadable = loadableManager.get(Loadable.forCatalog(board));
                } else if (parts.size() >= 3) {
                    // Thread mode
                    int no = -1;

                    try {
                        no = Integer.parseInt(parts.get(2));
                    } catch (NumberFormatException ignored) {
                    }

                    int post = -1;
                    String fragment = uri.getFragment();
                    if (fragment != null) {
                        int index = fragment.indexOf("p");
                        if (index >= 0) {
                            try {
                                post = Integer.parseInt(fragment.substring(index + 1));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    if (no >= 0) {
                        loadable = loadableManager.get(Loadable.forThread(site, board, no));
                        if (post >= 0) {
                            loadable.markedNo = post;
                        }
                    }
                }
            }
        }

        return loadable;
    }
}
