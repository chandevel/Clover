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
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Filter;
import org.floens.chan.ui.drawable.DropdownArrowDrawable;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;

public class FilterLayout extends LinearLayout implements View.OnClickListener, FloatingMenu.FloatingMenuCallback {
    private TextView typeText;
    private TextView boardsSelector;
    private TextView pattern;
    private CheckBox enabled;
    private CheckBox hide;

    private BoardManager boardManager;

    private Filter filter = new Filter();

    private List<Board> appliedBoards = new ArrayList<>();

    public FilterLayout(Context context) {
        super(context);
    }

    public FilterLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        boardManager = Chan.getBoardManager();

        typeText = (TextView) findViewById(R.id.type);
        boardsSelector = (TextView) findViewById(R.id.boards);
        pattern = (TextView) findViewById(R.id.pattern);
        enabled = (CheckBox) findViewById(R.id.enabled);
        hide = (CheckBox) findViewById(R.id.hide);
        typeText.setOnClickListener(this);
        typeText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);

        boardsSelector.setOnClickListener(this);
        boardsSelector.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);

        update();
    }

    public void setFilter(Filter filter) {
        this.filter.apply(filter);
        appliedBoards.clear();
        if (filter.allBoards) {
            appliedBoards.addAll(boardManager.getSavedBoards());
        } else if (!TextUtils.isEmpty(filter.boards)) {
            for (String value : filter.boards.split(",")) {
                Board boardByValue = boardManager.getBoardByValue(value);
                if (boardByValue != null) {
                    appliedBoards.add(boardByValue);
                }
            }
        }
        update();
    }

    public Filter getFilter() {
        filter.pattern = pattern.getText().toString();
        filter.hide = hide.isChecked();
        filter.enabled = enabled.isChecked();

        filter.boards = "";
        for (int i = 0; i < appliedBoards.size(); i++) {
            Board board = appliedBoards.get(i);
            filter.boards += board.value;
            if (i < appliedBoards.size() - 1) {
                filter.boards += ",";
            }
        }

        return filter;
    }

    @Override
    public void onClick(View v) {
        if (v == typeText) {
            List<FloatingMenuItem> menuItems = new ArrayList<>(2);

            for (FilterEngine.FilterType filterType : FilterEngine.FilterType.values()) {
                menuItems.add(new FloatingMenuItem(filterType, filterTypeName(filterType)));
            }

            FloatingMenu menu = new FloatingMenu(v.getContext());
            menu.setAnchor(v, Gravity.LEFT, -dp(5), -dp(5));
            menu.setPopupWidth(dp(150));
            menu.setCallback(this);
            menu.setItems(menuItems);
            menu.show();
        } else if (v == boardsSelector) {
            final BoardSelectLayout boardSelectLayout = (BoardSelectLayout) LayoutInflater.from(getContext()).inflate(R.layout.layout_board_select, null);

            boardSelectLayout.setCheckedBoards(appliedBoards);

            new AlertDialog.Builder(getContext())
                    .setView(boardSelectLayout)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            setCheckedBoards(boardSelectLayout.getCheckedBoards(), boardSelectLayout.getAllChecked());
                            update();
                        }
                    })
                    .show();
        }
    }

    @Override
    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
        FilterEngine.FilterType type = (FilterEngine.FilterType) item.getId();
        filter.type = type.id;
        update();
    }

    @Override
    public void onFloatingMenuDismissed(FloatingMenu menu) {
    }

    private void update() {
        pattern.setText(filter.pattern);
        hide.setChecked(filter.hide);
        enabled.setChecked(filter.enabled);

        typeText.setText(filterTypeName(FilterEngine.FilterType.forId(filter.type)));

        String text = getString(R.string.filter_boards) + " (";
        if (filter.allBoards) {
            text += getString(R.string.filter_boards_all);
        } else {
            text += String.valueOf(appliedBoards.size());
        }
        text += ")";
        boardsSelector.setText(text);
    }

    private void setCheckedBoards(List<Board> checkedBoards, boolean allChecked) {
        appliedBoards.clear();
        appliedBoards.addAll(checkedBoards);
        filter.allBoards = allChecked;
    }

    private String filterTypeName(FilterEngine.FilterType type) {
        switch (type) {
            case TRIPCODE:
                return getString(R.string.filter_tripcode);
            case NAME:
                return getString(R.string.filter_name);
            case COMMENT:
                return getString(R.string.filter_comment);
            case ID:
                return getString(R.string.filter_id);
            case SUBJECT:
                return getString(R.string.filter_subject);
            case FILENAME:
                return getString(R.string.filter_filename);
        }
        return null;
    }
}
