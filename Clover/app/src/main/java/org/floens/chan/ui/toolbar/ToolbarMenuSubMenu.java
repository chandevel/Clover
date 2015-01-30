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
import android.support.v4.view.GravityCompat;
import android.support.v7.widget.ListPopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ToolbarMenuSubMenu {
    private final Context context;
    private View anchor;
    private int anchorGravity;
    private int anchorOffsetX;
    private int anchorOffsetY;
    private int popupWidth = -1;
    private List<ToolbarMenuSubItem> items;
    private ToolbarMenuSubItem selectedItem;
    private ListAdapter adapter;
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;

    private ListPopupWindow popupWindow;
    private ToolbarMenuItemSubMenuCallback callback;

    public ToolbarMenuSubMenu(Context context, View anchor, List<ToolbarMenuSubItem> items) {
        this.context = context;
        this.anchor = anchor;
        anchorGravity = Gravity.RIGHT | Gravity.TOP;
        anchorOffsetX = -dp(5);
        anchorOffsetY = dp(5);
        this.items = items;
    }

    public ToolbarMenuSubMenu(Context context) {
        this.context = context;
    }

    public void setAnchor(View anchor, int anchorGravity, int anchorOffsetX, int anchorOffsetY) {
        this.anchor = anchor;
        this.anchorGravity = anchorGravity;
        this.anchorOffsetX = anchorOffsetX;
        this.anchorOffsetY = anchorOffsetY;
    }

    public void setPopupWidth(int width) {
        this.popupWidth = width;
        if (popupWindow != null) {
            popupWindow.setContentWidth(popupWidth);
        }
    }

    public void setItems(List<ToolbarMenuSubItem> items) {
        this.items = items;
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
    }

    public void setSelectedItem(ToolbarMenuSubItem item) {
        this.selectedItem = item;
    }

    public void setAdapter(ListAdapter adapter) {
        this.adapter = adapter;
    }

    public void setCallback(ToolbarMenuItemSubMenuCallback callback) {
        this.callback = callback;
    }

    public void show() {
        popupWindow = new ListPopupWindow(context);
        popupWindow.setAnchorView(anchor);
        popupWindow.setModal(true);
        popupWindow.setDropDownGravity(GravityCompat.END | Gravity.TOP);
        popupWindow.setVerticalOffset(-anchor.getHeight() + anchorOffsetY);
        popupWindow.setHorizontalOffset(anchorOffsetX);
        if (popupWidth > 0) {
            popupWindow.setContentWidth(popupWidth);
        } else {
            popupWindow.setContentWidth(dp(3 * 56));
        }

        List<String> stringItems = new ArrayList<>(items.size());
        int selectedPosition = 0;
        for (int i = 0; i < items.size(); i++) {
            stringItems.add(items.get(i).getText());
            if (items.get(i) == selectedItem) {
                selectedPosition = i;
            }
        }

        if (adapter != null) {
            popupWindow.setAdapter(adapter);
        } else {
            popupWindow.setAdapter(new SubMenuArrayAdapter(context, R.layout.toolbar_menu_item, stringItems));
        }

        popupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < items.size()) {
                    callback.onSubMenuItemClicked(ToolbarMenuSubMenu.this, items.get(position));
                    popupWindow.dismiss();
                } else {
                    callback.onSubMenuItemClicked(ToolbarMenuSubMenu.this, null);
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
                popupWindow = null;
            }
        });

        popupWindow.show();
        popupWindow.setSelection(selectedPosition);
    }

    public interface ToolbarMenuItemSubMenuCallback {
        public void onSubMenuItemClicked(ToolbarMenuSubMenu menu, ToolbarMenuSubItem item);
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
