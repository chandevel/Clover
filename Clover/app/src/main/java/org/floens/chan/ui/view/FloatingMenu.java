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
package org.floens.chan.ui.view;

import android.content.Context;
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
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class FloatingMenu {
    public static final int POPUP_WIDTH_AUTO = -1;
    public static final int POPUP_WIDTH_ANCHOR = -2;

    private final Context context;
    private View anchor;
    private int anchorGravity = Gravity.LEFT;
    private int anchorOffsetX;
    private int anchorOffsetY;
    private int popupWidth = POPUP_WIDTH_AUTO;
    private List<FloatingMenuItem> items;
    private FloatingMenuItem selectedItem;
    private ListAdapter adapter;
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;

    private ListPopupWindow popupWindow;
    private FloatingMenuCallback callback;

    public FloatingMenu(Context context, View anchor, List<FloatingMenuItem> items) {
        this.context = context;
        this.anchor = anchor;
        anchorOffsetX = -dp(5);
        anchorOffsetY = dp(5);
        anchorGravity = Gravity.RIGHT;
        this.items = items;
    }

    public FloatingMenu(Context context) {
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

    public void setItems(List<FloatingMenuItem> items) {
        this.items = items;
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
    }

    public void setSelectedItem(FloatingMenuItem item) {
        this.selectedItem = item;
    }

    public void setAdapter(ListAdapter adapter) {
        this.adapter = adapter;
    }

    public void setCallback(FloatingMenuCallback callback) {
        this.callback = callback;
    }

    public void show() {
        popupWindow = new ListPopupWindow(context);
        popupWindow.setAnchorView(anchor);
        popupWindow.setModal(true);
        popupWindow.setDropDownGravity(anchorGravity);
        popupWindow.setVerticalOffset(-anchor.getHeight() + anchorOffsetY);
        popupWindow.setHorizontalOffset(anchorOffsetX);
        if (popupWidth == POPUP_WIDTH_ANCHOR) {
            popupWindow.setContentWidth(Math.min(dp(4 * 56), anchor.getWidth()));
        } else if (popupWidth == POPUP_WIDTH_AUTO) {
            popupWindow.setContentWidth(dp(3 * 56));
        } else {
            popupWindow.setContentWidth(popupWidth);
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
            popupWindow.setAdapter(new FloatingMenuArrayAdapter(context, R.layout.toolbar_menu_item, stringItems));
        }

        popupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < items.size()) {
                    callback.onFloatingMenuItemClicked(FloatingMenu.this, items.get(position));
                    popupWindow.dismiss();
                } else {
                    callback.onFloatingMenuItemClicked(FloatingMenu.this, null);
                }
            }
        });

        globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (popupWindow == null) {
                    Logger.w("FloatingMenu", "popupWindow null in layout listener");
                } else {
                    if (popupWindow.isShowing()) {
                        // Recalculate anchor position
                        popupWindow.show();
                    }
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

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            popupWindow = null;
        }
    }

    public interface FloatingMenuCallback {
        void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item);
    }

    private static class FloatingMenuArrayAdapter extends ArrayAdapter<String> {
        public FloatingMenuArrayAdapter(Context context, int resource, List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
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
