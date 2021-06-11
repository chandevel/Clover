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
package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.ui.text.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.view.ColorPickerView;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;

public class FilterLayout
        extends LinearLayout
        implements View.OnClickListener {
    private TextView typeText;
    private TextView boardsSelector;
    private TextView pattern;
    private TextView patternPreview;
    private TextView patternPreviewStatus;
    private CheckBox enabled;
    private ImageView help;
    private TextView actionText;
    private LinearLayout colorContainer;
    private View colorPreview;
    private CheckBox applyToReplies;
    private CheckBox onlyOnOP;
    private CheckBox applyToSaved;

    @Inject
    BoardManager boardManager;

    @Inject
    FilterEngine filterEngine;

    private FilterLayoutCallback callback;
    private Filter filter;

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
        inject(this);

        typeText = findViewById(R.id.type);
        boardsSelector = findViewById(R.id.boards);
        actionText = findViewById(R.id.action);
        pattern = findViewById(R.id.pattern);
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
        patternPreview = findViewById(R.id.pattern_preview);
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
        patternPreviewStatus = findViewById(R.id.pattern_preview_status);
        enabled = findViewById(R.id.enabled);
        help = findViewById(R.id.help);
        colorContainer = findViewById(R.id.color_container);
        colorPreview = findViewById(R.id.color_preview);
        applyToReplies = findViewById(R.id.apply_to_replies_checkbox);
        onlyOnOP = findViewById(R.id.only_on_op_checkbox);
        applyToSaved = findViewById(R.id.apply_to_saved_checkbox);

        Drawable dropdownDrawable = getRes().getDrawable(R.drawable.ic_fluent_caret_down_16_filled);
        dropdownDrawable.setTint(getAttrColor(getContext(), android.R.attr.textColorSecondary));
        typeText.setCompoundDrawablesWithIntrinsicBounds(null, null, dropdownDrawable, null);
        boardsSelector.setCompoundDrawablesWithIntrinsicBounds(null, null, dropdownDrawable, null);
        actionText.setCompoundDrawablesWithIntrinsicBounds(null, null, dropdownDrawable, null);

        colorContainer.setOnClickListener(this);
        help.setOnClickListener(this);
        typeText.setOnClickListener(this);
        boardsSelector.setOnClickListener(this);
        actionText.setOnClickListener(this);
    }

    public void setFilter(Filter filter) {
        this.filter = filter;

        pattern.setText(filter.pattern);

        updateFilterValidity();
        updateFilterType();
        updateFilterAction();
        updateCheckboxes();
        updateBoardsSummary();
        updatePatternPreview();
    }

    public void setCallback(FilterLayoutCallback callback) {
        this.callback = callback;
    }

    public Filter getFilter() {
        filter.enabled = enabled.isChecked();
        filter.applyToReplies = applyToReplies.isChecked();
        filter.onlyOnOP = onlyOnOP.isChecked();
        filter.applyToSaved = applyToSaved.isChecked();

        return filter;
    }

    @Override
    public void onClick(View v) {
        if (v == typeText) {
            @SuppressWarnings("unchecked")
            final SelectLayout<FilterType> selectLayout =
                    (SelectLayout<FilterType>) LayoutInflater.from(getContext()).inflate(R.layout.layout_select, null);

            List<SelectLayout.SelectItem<FilterType>> items = new ArrayList<>();
            for (FilterType filterType : FilterType.values()) {
                String name = FilterType.filterTypeName(filterType);
                boolean checked = filter.hasFilter(filterType);

                items.add(new SelectLayout.SelectItem<>(filterType, filterType.flag, name, null, name, checked));
            }

            selectLayout.setItems(items);

            getDefaultAlertBuilder(getContext()).setView(selectLayout)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        List<SelectLayout.SelectItem<FilterType>> items12 = selectLayout.getItems();
                        int flags = 0;
                        for (SelectLayout.SelectItem<FilterType> item : items12) {
                            if (item.checked) {
                                flags |= item.item.flag;
                            }
                        }

                        filter.type = flags;
                        updateFilterType();
                        updatePatternPreview();
                    })
                    .show();
        } else if (v == boardsSelector) {
            @SuppressWarnings("unchecked")
            final SelectLayout<Board> selectLayout =
                    (SelectLayout<Board>) LayoutInflater.from(getContext()).inflate(R.layout.layout_select, null);

            List<SelectLayout.SelectItem<Board>> items = new ArrayList<>();

            Boards allSavedBoards = new Boards();
            for (BoardRepository.SiteBoards item : boardManager.getSavedBoardsObservable().get()) {
                allSavedBoards.addAll(item.boards);
            }

            for (Board board : allSavedBoards) {
                String name = board.getFormattedName();
                boolean checked = filterEngine.matchesBoard(filter, board);

                items.add(new SelectLayout.SelectItem<>(board, board.id, name, "", name, checked));
            }

            selectLayout.setItems(items);

            getDefaultAlertBuilder(getContext()).setView(selectLayout)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        List<SelectLayout.SelectItem<Board>> items1 = selectLayout.getItems();
                        boolean all = selectLayout.areAllChecked();
                        Boards boardList = new Boards(items1.size());
                        if (!all) {
                            for (SelectLayout.SelectItem<Board> item : items1) {
                                if (item.checked) {
                                    boardList.add(item.item);
                                }
                            }
                            if (boardList.isEmpty()) {
                                all = true;
                            }
                        }

                        filterEngine.saveBoardsToFilter(boardList, all, filter);

                        updateBoardsSummary();
                    })
                    .show();
        } else if (v == actionText) {
            List<FloatingMenuItem<FilterAction>> menuItems = new ArrayList<>(6);

            for (FilterAction action : FilterAction.values()) {
                menuItems.add(new FloatingMenuItem<>(action, FilterAction.actionName(action)));
            }

            FloatingMenu<FilterAction> menu = new FloatingMenu<>(v.getContext(), v, menuItems);
            menu.setAnchorGravity(Gravity.LEFT, -dp(5), -dp(5));
            menu.setCallback(new FloatingMenu.ClickCallback<FilterAction>() {
                @Override
                public void onFloatingMenuItemClicked(
                        FloatingMenu<FilterAction> menu, FloatingMenuItem<FilterAction> item
                ) {
                    filter.action = item.getId().id;
                    updateFilterAction();
                }
            });
            menu.show();
        } else if (v == help) {
            SpannableStringBuilder message =
                    (SpannableStringBuilder) HtmlCompat.fromHtml(getString(R.string.filter_help),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                    );
            TypefaceSpan[] typefaceSpans = message.getSpans(0, message.length(), TypefaceSpan.class);
            for (TypefaceSpan span : typefaceSpans) {
                if (span.getFamily().equals("monospace")) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpanHashed(0x22000000), start, end, 0);
                }
            }

            StyleSpan[] styleSpans = message.getSpans(0, message.length(), StyleSpan.class);
            for (StyleSpan span : styleSpans) {
                if (span.getStyle() == Typeface.ITALIC) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpanHashed(0x22000000), start, end, 0);
                }
            }

            getDefaultAlertBuilder(getContext()).setTitle(R.string.filter_help_title)
                    .setMessage(message)
                    .setNegativeButton("Open Regex101", (dialog1, which) -> openLink("https://regex101.com/"))
                    .setPositiveButton(R.string.close, null)
                    .show();
        } else if (v == colorContainer) {
            final ColorPickerView colorPickerView = new ColorPickerView(getContext());
            colorPickerView.setColor(filter.color);

            AlertDialog dialog = getDefaultAlertBuilder(getContext()).setTitle(R.string.filter_color_pick)
                    .setView(colorPickerView)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog1, which) -> {
                        filter.color = colorPickerView.getColor();
                        updateFilterAction();
                    })
                    .show();
            dialog.getWindow().setLayout(dp(300), dp(300));
        }
    }

    private void updateFilterValidity() {
        int extraFlags = (filter.type & FilterType.FLAG_CODE.flag) != 0 ? Pattern.CASE_INSENSITIVE : 0;
        boolean valid = !TextUtils.isEmpty(filter.pattern) && filterEngine.compile(filter.pattern, extraFlags) != null;
        pattern.setError(valid ? null : getString(R.string.filter_invalid_pattern));

        if (callback != null) {
            callback.setSaveButtonEnabled(valid);
        }
    }

    private void updateBoardsSummary() {
        String text = getString(R.string.filter_boards) + " (";
        if (filter.allBoards) {
            text += getString(R.string.filter_all);
        } else {
            text += filterEngine.getFilterBoardCount(filter);
        }
        text += ")";
        boardsSelector.setText(text);
    }

    private void updateCheckboxes() {
        enabled.setChecked(filter.enabled);
        applyToReplies.setChecked(filter.applyToReplies);
        onlyOnOP.setChecked(filter.onlyOnOP);
        applyToSaved.setChecked(filter.applyToSaved);
        if (filter.action == FilterAction.WATCH.id) {
            applyToReplies.setEnabled(false);
            onlyOnOP.setChecked(true);
            onlyOnOP.setEnabled(false);
            applyToSaved.setEnabled(false);
        }
    }

    private void updateFilterAction() {
        FilterAction action = FilterAction.forId(filter.action);
        actionText.setText(FilterAction.actionName(action));
        colorContainer.setVisibility(action == FilterAction.COLOR ? VISIBLE : GONE);
        if (filter.color == 0) {
            filter.color = 0xffff0000;
        }
        colorPreview.setBackgroundColor(filter.color);
        if (filter.action != FilterAction.WATCH.id) {
            applyToReplies.setEnabled(true);
            onlyOnOP.setEnabled(true);
            onlyOnOP.setChecked(false);
            applyToSaved.setEnabled(true);
        } else {
            applyToReplies.setEnabled(false);
            onlyOnOP.setEnabled(false);
            applyToSaved.setEnabled(false);
            if (applyToReplies.isChecked()) {
                applyToReplies.toggle();
                filter.applyToReplies = false;
            }
            if (!onlyOnOP.isChecked()) {
                onlyOnOP.toggle();
                filter.onlyOnOP = true;
            }
            if (applyToSaved.isChecked()) {
                applyToSaved.toggle();
                filter.applyToSaved = false;
            }
        }
    }

    private void updateFilterType() {
        int types = FilterType.forFlags(filter.type).size();
        String text = getString(R.string.filter_types) + " (" + types + ")";
        typeText.setText(text);
    }

    private void updatePatternPreview() {
        String text = patternPreview.getText().toString();
        boolean matches = filterEngine.matches(filter, FilterType.forFlags(filter.type).get(0), text, true);
        patternPreviewStatus.setText(matches ? R.string.filter_matches : R.string.filter_no_matches);
    }

    public interface FilterLayoutCallback {
        void setSaveButtonEnabled(boolean enabled);
    }
}
