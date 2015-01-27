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
package org.floens.chan.ui.toolbar;

import android.content.Context;
import android.support.v7.widget.ListPopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ToolbarMenuSubMenu {
    private final Context context;
    private final View anchor;
    private List<ToolbarMenuSubItem> items;
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;

    private ToolbarMenuItemSubMenuCallback callback;

    public ToolbarMenuSubMenu(Context context, View anchor, List<ToolbarMenuSubItem> items) {
        this.context = context;
        this.anchor = anchor;
        this.items = items;
    }

    public void setCallback(ToolbarMenuItemSubMenuCallback callback) {
        this.callback = callback;
    }

    public void show() {
        final ListPopupWindow popupWindow = new ListPopupWindow(context);
        popupWindow.setAnchorView(anchor);
        popupWindow.setModal(true);
        popupWindow.setDropDownGravity(Gravity.RIGHT | Gravity.TOP);
        popupWindow.setVerticalOffset(-anchor.getHeight() + dp(5));
        popupWindow.setHorizontalOffset(-dp(5));
        popupWindow.setContentWidth(dp(3 * 56));

        List<String> stringItems = new ArrayList<>(items.size());
        for (ToolbarMenuSubItem item : items) {
            stringItems.add(item.getText());
        }

        popupWindow.setAdapter(new SubMenuArrayAdapter(context, R.layout.toolbar_menu_item, stringItems));
        popupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < items.size()) {
                    callback.onSubMenuItemClicked(items.get(position));
                    popupWindow.dismiss();
                }
            }
        });

        globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (popupWindow.isShowing()) {
                    // Recalculate anchor position
                    popupWindow.show();
                }
            }
        };
        anchor.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (anchor.getViewTreeObserver().isAlive()) {
                    anchor.getViewTreeObserver().removeGlobalOnLayoutListener(globalLayoutListener);
                }
                globalLayoutListener = null;
            }
        });

        popupWindow.show();
    }

    public interface ToolbarMenuItemSubMenuCallback {
        public void onSubMenuItemClicked(ToolbarMenuSubItem item);
    }

    private static class SubMenuArrayAdapter extends ArrayAdapter<String> {
        public SubMenuArrayAdapter(Context context, int resource, List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.toolbar_menu_item, parent, false);
            }

            TextView textView = (TextView) convertView;
            textView.setText(getItem(position));
            textView.setTypeface(AndroidUtils.ROBOTO_MEDIUM);

            return textView;
        }
    }
}
