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

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.ui.helper.BoardHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;

public class BoardSelectLayout extends LinearLayout implements SearchLayout.SearchLayoutCallback, View.OnClickListener {
    private SearchLayout searchLayout;
    private RecyclerView recyclerView;
    private Button checkAllButton;

    private List<BoardChecked> boards = new ArrayList<>();
    private BoardManager boardManager;
    private BoardSelectAdapter adapter;
    private boolean allChecked = false;

    public BoardSelectLayout(Context context) {
        super(context);
    }

    public BoardSelectLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BoardSelectLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onSearchEntered(String entered) {
        adapter.search(entered);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        boardManager = Chan.getBoardManager();

        List<Board> savedList = boardManager.getSavedBoards();
        for (Board saved : savedList) {
            boards.add(new BoardChecked(saved, false));
        }

        searchLayout = (SearchLayout) findViewById(R.id.search_layout);
        searchLayout.setCallback(this);
        searchLayout.setHint(getString(R.string.search_hint));
        searchLayout.setTextColor(getAttrColor(getContext(), R.attr.text_color_primary));
        searchLayout.setHintColor(getAttrColor(getContext(), R.attr.text_color_hint));
        searchLayout.setClearButtonImage(R.drawable.ic_clear_black_24dp);

        checkAllButton = (Button) findViewById(R.id.select_all);
        checkAllButton.setOnClickListener(this);

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new BoardSelectAdapter();
        recyclerView.setAdapter(adapter);
        adapter.load();

        updateAllSelected();
    }

    @Override
    public void onClick(View v) {
        if (v == checkAllButton) {
            for (BoardChecked item : boards) {
                item.checked = !allChecked;
            }

            updateAllSelected();
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    public void setCheckedBoards(List<Board> checked) {
        for (BoardChecked board : boards) {
            for (Board check : checked) {
                if (check.code.equals(board.board.code)) {
                    board.checked = true;
                    break;
                }
            }
        }

        recyclerView.getAdapter().notifyDataSetChanged();
    }

    public List<Board> getCheckedBoards() {
        List<Board> list = new ArrayList<>();
        for (int i = 0; i < boards.size(); i++) {
            BoardSelectLayout.BoardChecked board = boards.get(i);
            if (board.checked) {
                list.add(board.board);
            }
        }

        return list;
    }

    public boolean getAllChecked() {
        return allChecked;
    }

    private void updateAllSelected() {
        int checkedCount = 0;
        for (BoardChecked item : boards) {
            if (item.checked) {
                checkedCount++;
            }
        }

        allChecked = checkedCount == boards.size();
        checkAllButton.setText(allChecked ? R.string.board_select_none : R.string.board_select_all);
    }

    private class BoardSelectAdapter extends RecyclerView.Adapter<BoardSelectViewHolder> {
        private List<BoardChecked> sourceList = new ArrayList<>();
        private List<BoardChecked> displayList = new ArrayList<>();
        private String searchQuery;

        public BoardSelectAdapter() {
            setHasStableIds(true);
        }

        @Override
        public BoardSelectViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new BoardSelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_board_select, parent, false));
        }

        @Override
        public void onBindViewHolder(BoardSelectViewHolder holder, int position) {
            BoardChecked board = displayList.get(position);
            holder.checkBox.setChecked(board.checked);
            holder.text.setText(BoardHelper.getName(board.board));
            holder.description.setText(BoardHelper.getDescription(board.board));
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        @Override
        public long getItemId(int position) {
            return displayList.get(position).board.id;
        }

        public void search(String query) {
            this.searchQuery = query;
            filter();
        }

        private void load() {
            sourceList.clear();
            sourceList.addAll(boards);

            filter();
        }

        private void filter() {
            displayList.clear();
            if (!TextUtils.isEmpty(searchQuery)) {
                String query = searchQuery.toLowerCase(Locale.ENGLISH);
                for (BoardChecked boardChecked : sourceList) {
                    String description = boardChecked.board.description == null ? "" : boardChecked.board.description;
                    if (boardChecked.board.name.toLowerCase(Locale.ENGLISH).contains(query) ||
                            boardChecked.board.code.toLowerCase(Locale.ENGLISH).contains(query) ||
                            description.toLowerCase(Locale.ENGLISH).contains(query)) {
                        displayList.add(boardChecked);
                    }
                }
            } else {
                displayList.addAll(sourceList);
            }

            notifyDataSetChanged();
        }
    }

    private class BoardSelectViewHolder extends RecyclerView.ViewHolder implements CompoundButton.OnCheckedChangeListener, OnClickListener {
        private CheckBox checkBox;
        private TextView text;
        private TextView description;

        public BoardSelectViewHolder(View itemView) {
            super(itemView);
            checkBox = (CheckBox) itemView.findViewById(R.id.checkbox);
            text = (TextView) itemView.findViewById(R.id.text);
            description = (TextView) itemView.findViewById(R.id.description);

            checkBox.setOnCheckedChangeListener(this);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (buttonView == checkBox) {
                BoardChecked board = adapter.displayList.get(getAdapterPosition());
                board.checked = isChecked;
                updateAllSelected();
            }
        }

        @Override
        public void onClick(View v) {
            checkBox.toggle();
        }
    }

    public static class BoardChecked {
        public Board board;
        public boolean checked;

        public BoardChecked(Board board, boolean checked) {
            this.board = board;
            this.checked = checked;
        }
    }
}
