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

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

public class BoardsMenuPresenter
        implements Observer {
    private BoardRepository.SitesBoards allBoards;

    private Items items;

    @Nullable
    private String filter;

    @Inject
    public BoardsMenuPresenter(BoardManager boardManager) {
        allBoards = boardManager.getAllBoardsObservable();
    }

    public void create(Callback callback, Board selectedBoard) {

        this.allBoards.addObserver(this);

        items = new Items();

        updateWithFilter();

        callback.scrollToPosition(items.findBoardPosition(selectedBoard));
    }

    public void destroy() {
        allBoards.deleteObserver(this);
    }

    public Items items() {
        return items;
    }

    public void filterChanged(String filter) {
        this.filter = filter;
        updateWithFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == allBoards) {
            updateWithFilter();
        }
    }

    private void updateWithFilter() {
        items.update(this.allBoards.get(), filter);
    }

    public static class Items
            extends Observable {
        public List<Item> items = new ArrayList<>();
        private int itemIdCounter = 1;

        public Items() {
        }

        public void update(List<BoardRepository.SiteBoards> allBoards, String filter) {
            items.clear();

            items.add(new Item(0, Item.Type.SEARCH));

            for (BoardRepository.SiteBoards siteAndBoards : allBoards) {
                Site site = siteAndBoards.site;
                List<Board> boards = siteAndBoards.boards;

                items.add(new Item(itemIdCounter++, site));

                if (filter == null || filter.length() == 0) {
                    for (Board board : boards) {
                        if (board.saved) {
                            items.add(new Item(itemIdCounter++, board));
                        }
                    }
                } else {
                    List<Board> res = BoardHelper.quickSearch(boards, filter);
                    for (Board b : res) {
                        items.add(new Item(itemIdCounter++, b));
                    }
                }
            }

            setChanged();
            notifyObservers();
        }

        public int getCount() {
            return items.size();
        }

        public int findBoardPosition(Board board) {
            int position = 0;
            for (Item item : items) {

                if (item.board != null && item.board.siteCodeEquals(board)) {
                    return position;
                }

                position++;
            }

            return 0;
        }

        public Item getAtPosition(int position) {
            return items.get(position);
        }
    }

    public static class Item {
        public enum Type {
            BOARD(0),
            SITE(1),
            SEARCH(2);

            public int typeId;

            Type(int typeId) {
                this.typeId = typeId;
            }
        }

        public final Type type;
        public Board board;
        public Site site;
        public int id;

        public Item(int id, Type type) {
            this.id = id;
            this.type = type;
        }

        public Item(int id, Board board) {
            this.id = id;
            type = Type.BOARD;
            this.board = board;
        }

        public Item(int id, Site site) {
            this.id = id;
            type = Type.SITE;
            this.site = site;
        }
    }

    public interface Callback {
        void scrollToPosition(int position);
    }
}
