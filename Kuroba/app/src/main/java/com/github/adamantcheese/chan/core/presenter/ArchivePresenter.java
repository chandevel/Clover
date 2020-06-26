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
package com.github.adamantcheese.chan.core.presenter;

import com.github.adamantcheese.chan.core.model.Archive;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.SiteActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.text.TextUtils.isEmpty;

public class ArchivePresenter
        implements SiteActions.ArchiveListener {

    private Callback callback;
    private Board board;

    private boolean inRequest = false;

    private String filter;
    private List<Archive.ArchiveItem> items = new ArrayList<>();
    private List<Archive.ArchiveItem> filteredItems = new ArrayList<>();

    public ArchivePresenter(Callback callback, Board board) {
        this.callback = callback;
        this.board = board;
    }

    public void onRefresh() {
        if (!inRequest) {
            loadArchive();
        }
    }

    private void loadArchive() {
        inRequest = true;
        callback.showError(false);
        board.site.actions().archive(board, this);
    }

    public void onSearchEntered(String query) {
        filterArchive(query);
    }

    public void onSearchVisibility(boolean visible) {
        if (!visible) {
            filterArchive(null);
        }
    }

    public void onItemClicked(Archive.ArchiveItem item) {
        callback.openThread(Loadable.forThread(board, item.id, ""));
    }

    @Override
    public void onArchive(Archive archive) {
        inRequest = false;
        callback.hideRefreshing();
        callback.showList();
        items = archive.items;
        updateWithFilter();
    }

    @Override
    public void onArchiveError() {
        inRequest = false;
        callback.hideRefreshing();
        callback.showError(true);
    }

    private void filterArchive(String query) {
        filter = query;
        updateWithFilter();
    }

    private void updateWithFilter() {
        filteredItems.clear();
        if (isEmpty(filter)) {
            filteredItems.addAll(items);
        } else {
            for (Archive.ArchiveItem item : items) {
                if (filterApplies(item, filter)) {
                    filteredItems.add(item);
                }
            }
        }

        callback.setArchiveItems(filteredItems);
    }

    private boolean filterApplies(Archive.ArchiveItem item, String filter) {
        return item.description.toLowerCase(Locale.ENGLISH).contains(filter.toLowerCase());
    }

    public interface Callback {
        void setArchiveItems(List<Archive.ArchiveItem> items);

        void hideRefreshing();

        void showList();

        void showError(boolean show);

        void openThread(Loadable loadable);
    }
}
