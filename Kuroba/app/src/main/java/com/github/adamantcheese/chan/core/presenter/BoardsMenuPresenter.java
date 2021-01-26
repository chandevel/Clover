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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.core.presenter.BoardsMenuPresenter.Item.Type.BOARD;
import static com.github.adamantcheese.chan.core.presenter.BoardsMenuPresenter.Item.Type.SEARCH;
import static com.github.adamantcheese.chan.core.presenter.BoardsMenuPresenter.Item.Type.SITE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class BoardsMenuPresenter
        implements Observer {
    private final BoardRepository.SitesBoards allBoards;

    private Items items;

    @Nullable
    private String filter;

    @Inject
    public BoardsMenuPresenter(BoardManager boardManager) {
        allBoards = boardManager.getAllBoardsObservable();
    }

    public void create(Callback callback, Board selectedBoard, Context context) {

        this.allBoards.addObserver(this);

        items = new Items(context);

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
        items.update(allBoards.get(), filter);
    }

    public static class Items
            extends Observable {
        public List<Item> items = new ArrayList<>();
        private BackgroundUtils.Cancelable boardsCall;
        private final Context context;

        public Items(Context context) {
            this.context = context;
        }

        public void update(final List<BoardRepository.SiteBoards> allBoards, final String filter) {
            if (boardsCall != null) {
                boardsCall.cancel();
            }
            boardsCall = BackgroundUtils.runWithExecutor(BackgroundUtils.backgroundService, () -> {
                List<Item> newItems = new ArrayList<>();
                int itemIdCounter = 1;

                newItems.add(new Item(0, SEARCH));

                for (BoardRepository.SiteBoards siteAndBoards : allBoards) {
                    Site site = siteAndBoards.site;
                    Boards boards = siteAndBoards.boards;

                    newItems.add(new Item(itemIdCounter++, site));

                    if (filter == null || filter.isEmpty()) {
                        for (Board board : boards) {
                            if (board.saved) {
                                newItems.add(new Item(itemIdCounter++, board));
                            }
                        }
                    } else {
                        // cap the amount of outputs to 5 instead of all boards, which could be a lot!
                        int count = 0;
                        for (Board b : BoardHelper.search(boards, filter)) {
                            if (count == 5) break;
                            newItems.add(new Item(itemIdCounter++, b));
                            count++;
                        }
                    }
                }

                if (newItems.size() == 1) {
                    CommonSite setupSite = new CommonSite() {
                        @Override
                        public void setup() {
                            setName("App Setup");
                            @SuppressLint("UseCompatLoadingForDrawables")
                            Drawable settingsDrawable = context.getDrawable(R.drawable.ic_fluent_settings_24_filled);
                            settingsDrawable.setTint(getAttrColor(context, android.R.attr.textColorSecondary));
                            setIcon(SiteIcon.fromDrawable(settingsDrawable));
                        }
                    };
                    setupSite.setup();
                    newItems.add(new Item(1, setupSite));
                }

                return newItems;
            }, result -> {
                items.clear();
                items.addAll(result);
                setChanged();
                notifyObservers();
            });
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
            BOARD,
            SITE,
            SEARCH
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
            type = BOARD;
            this.board = board;
        }

        public Item(int id, Site site) {
            this.id = id;
            type = SITE;
            this.site = site;
        }
    }

    public interface Callback {
        void scrollToPosition(int position);
    }
}
