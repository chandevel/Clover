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
package org.floens.chan.ui.layout;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.utils.AndroidUtils;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static org.floens.chan.utils.AndroidUtils.dp;

public class BrowseBoardsFloatingMenu implements Observer, AdapterView.OnItemClickListener {
    private FloatingMenu floatingMenu;
    private BoardManager.SavedBoards savedBoards;
    private BrowseBoardsAdapter adapter;

    private Callback callback;

    public BrowseBoardsFloatingMenu(BoardManager.SavedBoards savedBoards) {
        this.savedBoards = savedBoards;
        this.savedBoards.addObserver(this);
    }

    private void onDismissed() {
        savedBoards.deleteObserver(this);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void show(View anchor, Board selectedBoard) {
        floatingMenu = new FloatingMenu(anchor.getContext());
        floatingMenu.setCallback(new FloatingMenu.FloatingMenuCallbackAdapter() {
            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
                onDismissed();
            }
        });
        floatingMenu.setManageItems(false);
        floatingMenu.setAnchor(anchor, Gravity.LEFT, dp(5), dp(5));
        floatingMenu.setPopupWidth(FloatingMenu.POPUP_WIDTH_ANCHOR);
        adapter = new BrowseBoardsAdapter();
        floatingMenu.setAdapter(adapter);
        floatingMenu.setOnItemClickListener(this);
        floatingMenu.setSelectedPosition(resolveCurrentIndex(selectedBoard));
        floatingMenu.show();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == savedBoards) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Pair<Site, Board> siteOrBoard = getAtPosition(position);
        if (siteOrBoard.second != null) {
            callback.onBoardClicked(siteOrBoard.second);
        } else {
            callback.onSiteClicked(siteOrBoard.first);
        }
        floatingMenu.dismiss();
    }

    private int getCount() {
        int count = 0;
        for (Pair<Site, List<Board>> siteListPair : savedBoards.get()) {
            count += 1;
            count += siteListPair.second.size();
        }

        return count;
    }

    private int resolveCurrentIndex(Board board) {
        int position = 0;
        for (Pair<Site, List<Board>> siteListPair : savedBoards.get()) {
            position += 1;

            for (Board other : siteListPair.second) {
                if (board == other) {
                    return position;
                }
                position++;
            }
        }

        return 0;
    }

    private Pair<Site, Board> getAtPosition(int position) {
        for (Pair<Site, List<Board>> siteListPair : savedBoards.get()) {
            if (position == 0) {
                return new Pair<>(siteListPair.first, null);
            }
            position -= 1;

            if (position < siteListPair.second.size()) {
                return new Pair<>(null, siteListPair.second.get(position));
            }
            position -= siteListPair.second.size();
        }
        throw new IllegalArgumentException();
    }

    private class BrowseBoardsAdapter extends BaseAdapter {
        final int TYPE_SITE = 0;
        final int TYPE_BOARD = 1;

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            Pair<Site, Board> siteOrBoard = getAtPosition(position);
            if (siteOrBoard.first != null) {
                return TYPE_SITE;
            } else if (siteOrBoard.second != null) {
                return TYPE_BOARD;
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public int getCount() {
            return BrowseBoardsFloatingMenu.this.getCount();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("ViewHolder")
        @Override
        public View getView(int position, View view, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            Pair<Site, Board> siteOrBoard = getAtPosition(position);
            if (siteOrBoard.first != null) {
                Site site = siteOrBoard.first;

                if (view == null) {
                    view = inflater.inflate(R.layout.cell_browse_site, parent, false);
                }

                View divider = view.findViewById(R.id.divider);
                final ImageView image = view.findViewById(R.id.image);
                TextView text = view.findViewById(R.id.text);

                divider.setVisibility(position == 0 ? View.GONE : View.VISIBLE);

                final SiteIcon icon = site.icon();
                image.setTag(icon);

                icon.get(new SiteIcon.SiteIconResult() {
                    @Override
                    public void onSiteIcon(SiteIcon siteIcon, Drawable drawable) {
                        if (image.getTag() == icon) {
                            image.setImageDrawable(drawable);
                        }
                    }
                });

                text.setTypeface(AndroidUtils.ROBOTO_MEDIUM);
                text.setText(site.name());

                return view;
            } else {
                Board board = siteOrBoard.second;

                if (view == null) {
                    view = inflater.inflate(R.layout.cell_browse_board, parent, false);
                }

                TextView text = (TextView) view;

                text.setTypeface(AndroidUtils.ROBOTO_MEDIUM);
                text.setText(BoardHelper.getName(board));

                return text;
            }
        }
    }

    public interface Callback {
        void onBoardClicked(Board item);

        void onSiteClicked(Site site);
    }
}
