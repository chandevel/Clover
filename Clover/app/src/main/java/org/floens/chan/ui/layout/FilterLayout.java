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
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Filter;
import org.floens.chan.ui.controller.FiltersController;
import org.floens.chan.ui.dialog.ColorPickerView;
import org.floens.chan.ui.drawable.DropdownArrowDrawable;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;

public class FilterLayout extends LinearLayout implements View.OnClickListener {
    private TextView typeText;
    private TextView boardsSelector;
    private boolean patternContainerErrorShowing = false;
    private TextView pattern;
    private TextView patternPreview;
    private TextView patternPreviewStatus;
    private CheckBox enabled;
    private ImageView help;
    private TextView actionText;
    private LinearLayout colorContainer;
    private View colorPreview;

    private BoardManager boardManager;

    private FilterLayoutCallback callback;
    private Filter filter;

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
        actionText = (TextView) findViewById(R.id.action);
        pattern = (TextView) findViewById(R.id.pattern);
        pattern.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.pattern = s.toString();
                updateFilterValidity();
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreview = (TextView) findViewById(R.id.pattern_preview);
        patternPreview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreviewStatus = (TextView) findViewById(R.id.pattern_preview_status);
        enabled = (CheckBox) findViewById(R.id.enabled);
        help = (ImageView) findViewById(R.id.help);
        theme().helpDrawable.apply(help);
        help.setOnClickListener(this);
        colorContainer = (LinearLayout) findViewById(R.id.color_container);
        colorContainer.setOnClickListener(this);
        colorPreview = findViewById(R.id.color_preview);

        typeText.setOnClickListener(this);
        typeText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);

        boardsSelector.setOnClickListener(this);
        boardsSelector.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);

        actionText.setOnClickListener(this);
        actionText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
        appliedBoards.clear();
        appliedBoards.addAll(FilterEngine.getInstance().getBoardsForFilter(filter));

        pattern.setText(filter.pattern);

        updateFilterValidity();
        updateCheckboxes();
        updateFilterType();
        updateFilterAction();
        updateBoardsSummary();
        updatePatternPreview();
    }

    public void setCallback(FilterLayoutCallback callback) {
        this.callback = callback;
    }

    public Filter getFilter() {
        filter.enabled = enabled.isChecked();

        FilterEngine.getInstance().saveBoardsToFilter(appliedBoards, filter);

        return filter;
    }

    @Override
    public void onClick(View v) {
        if (v == typeText) {
            List<FloatingMenuItem> menuItems = new ArrayList<>(6);

            for (FilterEngine.FilterType filterType : FilterEngine.FilterType.values()) {
                menuItems.add(new FloatingMenuItem(filterType, FiltersController.filterTypeName(filterType)));
            }

            FloatingMenu menu = new FloatingMenu(v.getContext());
            menu.setAnchor(v, Gravity.LEFT, -dp(5), -dp(5));
            menu.setPopupWidth(dp(150));
            menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                @Override
                public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                    FilterEngine.FilterType type = (FilterEngine.FilterType) item.getId();
                    filter.type = type.id;
                    updateFilterType();
                    updatePatternPreview();
                }

                @Override
                public void onFloatingMenuDismissed(FloatingMenu menu) {
                }
            });
            menu.setItems(menuItems);
            menu.show();
        } else if (v == boardsSelector) {
            @SuppressWarnings("unchecked")
            final SelectLayout<Board> selectLayout = (SelectLayout<Board>) LayoutInflater.from(getContext()).inflate(R.layout.layout_select, null);

            List<SelectLayout.SelectItem<Board>> items = new ArrayList<>();
            List<Board> savedList = boardManager.getSavedBoards();
            for (int i = 0; i < savedList.size(); i++) {
                Board saved = savedList.get(i);
                String name = BoardHelper.getName(saved);
                String description = BoardHelper.getDescription(saved);
                String search = name + " " + saved.code;

                boolean checked = false;
                for (int j = 0; j < appliedBoards.size(); j++) {
                    Board appliedBoard = appliedBoards.get(j);
                    if (appliedBoard.code.equals(saved.code)) {
                        checked = true;
                        break;
                    }
                }

                items.add(new SelectLayout.SelectItem<>(
                        saved, saved.id, name, description, search, checked
                ));
            }

            selectLayout.setItems(items);

            new AlertDialog.Builder(getContext())
                    .setView(selectLayout)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            appliedBoards.clear();

                            List<SelectLayout.SelectItem<Board>> items = selectLayout.getItems();
                            for (int i = 0; i < items.size(); i++) {
                                SelectLayout.SelectItem<Board> selectItem = items.get(i);
                                if (selectItem.checked) {
                                    appliedBoards.add(selectItem.item);
                                }
                            }

                            filter.allBoards = selectLayout.areAllChecked();
                            updateBoardsSummary();
                        }
                    })
                    .show();
        } else if (v == actionText) {
            List<FloatingMenuItem> menuItems = new ArrayList<>(6);

            for (FilterEngine.FilterAction action : FilterEngine.FilterAction.values()) {
                menuItems.add(new FloatingMenuItem(action, FiltersController.actionName(action)));
            }

            FloatingMenu menu = new FloatingMenu(v.getContext());
            menu.setAnchor(v, Gravity.LEFT, -dp(5), -dp(5));
            menu.setPopupWidth(dp(150));
            menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                @Override
                public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                    FilterEngine.FilterAction action = (FilterEngine.FilterAction) item.getId();
                    filter.action = action.id;
                    updateFilterAction();
                }

                @Override
                public void onFloatingMenuDismissed(FloatingMenu menu) {
                }
            });
            menu.setItems(menuItems);
            menu.show();
        } else if (v == help) {
            SpannableStringBuilder message = (SpannableStringBuilder) Html.fromHtml(getString(R.string.filter_help));
            TypefaceSpan[] typefaceSpans = message.getSpans(0, message.length(), TypefaceSpan.class);
            for (TypefaceSpan span : typefaceSpans) {
                if (span.getFamily().equals("monospace")) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
                }
            }

            StyleSpan[] styleSpans = message.getSpans(0, message.length(), StyleSpan.class);
            for (StyleSpan span : styleSpans) {
                if (span.getStyle() == Typeface.ITALIC) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
                }
            }

            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.filter_help_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else if (v == colorContainer) {
            final ColorPickerView colorPickerView = new ColorPickerView(getContext());
            colorPickerView.setColor(filter.color);

            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.filter_color_pick)
                    .setView(colorPickerView)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            filter.color = colorPickerView.getColor();
                            updateFilterAction();
                        }
                    })
                    .show();
            dialog.getWindow().setLayout(dp(300), dp(300));
        }
    }

    private void updateFilterValidity() {
        FilterEngine.FilterType filterType = FilterEngine.FilterType.forId(filter.type);

        boolean valid;
        if (filterType.isRegex) {
            valid = FilterEngine.getInstance().compile(filter.pattern) != null;
        } else {
            valid = !TextUtils.isEmpty(filter.pattern);
        }

        if (valid != patternContainerErrorShowing) {
            patternContainerErrorShowing = valid;
            pattern.setError(valid ? null : getString(R.string.filter_invalid_pattern));
        }

        if (callback != null) {
            callback.setSaveButtonEnabled(valid);
        }
    }

    private void updateBoardsSummary() {
        String text = getString(R.string.filter_boards) + " (";
        if (filter.allBoards) {
            text += getString(R.string.filter_boards_all);
        } else {
            text += String.valueOf(appliedBoards.size());
        }
        text += ")";
        boardsSelector.setText(text);
    }

    private void updateCheckboxes() {
        enabled.setChecked(filter.enabled);
    }

    private void updateFilterAction() {
        FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(filter.action);
        actionText.setText(FiltersController.actionName(action));
        colorContainer.setVisibility(action == FilterEngine.FilterAction.COLOR ? VISIBLE : GONE);
        if (filter.color == 0) {
            filter.color = 0xffff0000;
        }
        colorPreview.setBackgroundColor(filter.color);
    }

    private void updateFilterType() {
        FilterEngine.FilterType filterType = FilterEngine.FilterType.forId(filter.type);
        typeText.setText(FiltersController.filterTypeName(filterType));
        pattern.setHint(filterType.isRegex ? R.string.filter_pattern_hint_regex : R.string.filter_pattern_hint_exact);
    }

    private void updatePatternPreview() {
        String text = patternPreview.getText().toString();
        boolean matches = text.length() > 0 && FilterEngine.getInstance().matches(filter, text, true);
        patternPreviewStatus.setText(matches ? R.string.filter_matches : R.string.filter_no_matches);
    }

    public interface FilterLayoutCallback {
        void setSaveButtonEnabled(boolean enabled);
    }
}
