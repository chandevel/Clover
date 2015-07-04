package org.floens.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.manager.FilterEngine;
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
    private TextView boards;
    private TextView pattern;
    private CheckBox hide;

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

        typeText = (TextView) findViewById(R.id.type);
        boards = (TextView) findViewById(R.id.boards);
        pattern = (TextView) findViewById(R.id.pattern);
        hide = (CheckBox) findViewById(R.id.hide);
        typeText.setOnClickListener(this);
        typeText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
        pattern.setText(filter.pattern);
        boards.setText(filter.boards);
        hide.setChecked(filter.hide);

        typeText.setText(filterTypeName(FilterEngine.FilterType.forId(filter.type)));
    }

    public void save() {
        filter.pattern = pattern.getText().toString();
        filter.boards = boards.getText().toString();
        filter.hide = hide.isChecked();

        Chan.getDatabaseManager().addFilter(filter);
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
        }
    }

    @Override
    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
        FilterEngine.FilterType type = (FilterEngine.FilterType) item.getId();
        typeText.setText(filterTypeName(type));
        filter.type = type.id;
    }

    @Override
    public void onFloatingMenuDismissed(FloatingMenu menu) {
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
