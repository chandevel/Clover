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
package com.github.adamantcheese.chan.ui.view;

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
import android.widget.TextView;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class FloatingMenu {
    public static final int POPUP_WIDTH_AUTO = -1;
    public static final int POPUP_WIDTH_ANCHOR = -2;

    private final Context context;
    private View anchor;
    private int anchorGravity = Gravity.LEFT;
    private int anchorOffsetX;
    private int anchorOffsetY;
    private int popupWidth = POPUP_WIDTH_AUTO;
    private int popupHeight = -1;
    private boolean manageItems = true;
    private List<FloatingMenuItem> items;
    private FloatingMenuItem selectedItem;
    private int selectedPosition;
    private ListAdapter adapter;
    private AdapterView.OnItemClickListener itemClickListener;
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

    public void setPopupHeight(int height) {
        this.popupHeight = height;
        if (popupWindow != null) {
            popupWindow.setHeight(height);
        }
    }

    public void setItems(List<FloatingMenuItem> items) {
        if (!manageItems) throw new IllegalArgumentException();
        this.items = items;
    }

    public void setSelectedItem(FloatingMenuItem item) {
        if (!manageItems) throw new IllegalArgumentException();
        this.selectedItem = item;
    }

    public void setSelectedPosition(int selectedPosition) {
        if (manageItems) throw new IllegalArgumentException();
        this.selectedPosition = selectedPosition;
    }

    public void setAdapter(ListAdapter adapter) {
        this.adapter = adapter;
        if (popupWindow != null) {
            popupWindow.setAdapter(adapter);
        }
    }

    public void setCallback(FloatingMenuCallback callback) {
        this.callback = callback;
    }

    public void setManageItems(boolean manageItems) {
        this.manageItems = manageItems;
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
        this.itemClickListener = listener;
        if (popupWindow != null) {
            popupWindow.setOnItemClickListener(listener);
        }
    }

    public void show() {
        popupWindow = new ListPopupWindow(context);
        popupWindow.setAnchorView(anchor);
        popupWindow.setModal(true);
        popupWindow.setDropDownGravity(anchorGravity);
        popupWindow.setVerticalOffset(-anchor.getHeight() + anchorOffsetY);
        popupWindow.setHorizontalOffset(anchorOffsetX);
        if (popupWidth == POPUP_WIDTH_ANCHOR) {
            popupWindow.setContentWidth(Math.min(dp(8 * 56), Math.max(dp(4 * 56), anchor.getWidth())));
        } else if (popupWidth == POPUP_WIDTH_AUTO) {
            popupWindow.setContentWidth(dp(3 * 56));
        } else {
            popupWindow.setContentWidth(popupWidth);
        }

        if (popupHeight > 0) {
            popupWindow.setHeight(popupHeight);
        }

        int selection = 0;
        if (manageItems) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == selectedItem) {
                    selection = i;
                }
            }
        } else {
            selection = this.selectedPosition;
        }

        if (adapter != null) {
            popupWindow.setAdapter(adapter);
        } else {
            popupWindow.setAdapter(new FloatingMenuArrayAdapter(context, R.layout.toolbar_menu_item, items));
        }

        if (manageItems) {
            popupWindow.setOnItemClickListener((parent, view, position, id) -> {
                if (position >= 0 && position < items.size()) {
                    FloatingMenuItem item = items.get(position);
                    if (item.isEnabled()) {
                        callback.onFloatingMenuItemClicked(FloatingMenu.this, item);
                        popupWindow.dismiss();
                    }
                } else {
                    callback.onFloatingMenuItemClicked(FloatingMenu.this, null);
                }
            });
        } else {
            popupWindow.setOnItemClickListener(itemClickListener);
        }

        globalLayoutListener = () -> {
            if (popupWindow == null) {
                Logger.w("FloatingMenu", "popupWindow null in layout listener");
            } else {
                if (popupWindow.isShowing()) {
                    // Recalculate anchor position
                    popupWindow.show();
                }
            }
        };
        anchor.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

        popupWindow.setOnDismissListener(() -> {
            if (anchor.getViewTreeObserver().isAlive()) {
                anchor.getViewTreeObserver().removeGlobalOnLayoutListener(globalLayoutListener);
            }
            globalLayoutListener = null;
            popupWindow = null;
            callback.onFloatingMenuDismissed(FloatingMenu.this);
        });

        popupWindow.show();
        popupWindow.setSelection(selection);
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

        void onFloatingMenuDismissed(FloatingMenu menu);
    }

    public static class FloatingMenuCallbackAdapter implements FloatingMenuCallback {
        @Override
        public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
        }

        @Override
        public void onFloatingMenuDismissed(FloatingMenu menu) {
        }
    }

    private static class FloatingMenuArrayAdapter extends ArrayAdapter<FloatingMenuItem> {
        public FloatingMenuArrayAdapter(Context context, int resource, List<FloatingMenuItem> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.toolbar_menu_item, parent, false);
            }

            FloatingMenuItem item = getItem(position);

            TextView textView = (TextView) convertView;
            textView.setText(item.getText());
            textView.setEnabled(item.isEnabled());
            textView.setTextColor(getAttrColor(getContext(), item.isEnabled() ? R.attr.text_color_primary : R.attr.text_color_hint));
            textView.setTypeface(AndroidUtils.ROBOTO_MEDIUM);

            return textView;
        }
    }
}
