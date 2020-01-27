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
package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.appcompat.widget.ListPopupWindow;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;

public class FloatingMenu {
    private final Context context;
    private View anchor;
    private int anchorGravity = Gravity.LEFT;
    private int anchorOffsetX;
    private int anchorOffsetY;
    private int popupHeight = -1;
    private boolean manageItems = true;
    private List<FloatingMenuItem> items = new ArrayList<>();
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
        for (FloatingMenuItem item : items) {
            if (item.isEnabled()) this.items.add(item);
        }
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

    public void setPopupHeight(int height) {
        this.popupHeight = height;
        if (popupWindow != null) {
            popupWindow.setHeight(height);
        }
    }

    public void setItems(List<FloatingMenuItem> items) {
        if (!manageItems) throw new IllegalArgumentException();
        this.items.clear();
        for (FloatingMenuItem item : items) {
            if (item.isEnabled()) this.items.add(item);
        }
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
            popupWindow.setWidth(measureContentWidth(adapter));
        } else {
            FloatingMenuArrayAdapter arrayAdapter =
                    new FloatingMenuArrayAdapter(context, R.layout.toolbar_menu_item, items);
            popupWindow.setAdapter(arrayAdapter);
            popupWindow.setWidth(measureContentWidth(arrayAdapter));
        }

        if (manageItems) {
            popupWindow.setOnItemClickListener((parent, view, position, id) -> {
                if (position >= 0 && position < items.size()) {
                    FloatingMenuItem item = items.get(position);
                    if (item.isEnabled()) {
                        callback.onFloatingMenuItemClicked(FloatingMenu.this, item);
                        dismiss();
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
                Logger.d("FloatingMenu", "popupWindow null in layout listener");
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
                anchor.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
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
        if (isShowing()) {
            popupWindow.dismiss();
            popupWindow = null;
        }
    }

    private int measureContentWidth(ListAdapter listAdapter) {
        ViewGroup mMeasureParent = new FrameLayout(context);
        int maxWidth = 0;
        View itemView = null;
        int itemType = 0;

        final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int count = listAdapter.getCount();
        for (int i = 0; i < count; i++) {
            final int positionType = listAdapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }

            itemView = listAdapter.getView(i, itemView, mMeasureParent);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);

            final int itemWidth = itemView.getMeasuredWidth();

            if (itemWidth > maxWidth) {
                maxWidth = itemWidth;
            }
        }

        return maxWidth < dp(3 * 56) ? dp(3 * 56) : maxWidth;
    }

    public interface FloatingMenuCallback {
        void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item);

        void onFloatingMenuDismissed(FloatingMenu menu);
    }

    public static class FloatingMenuCallbackAdapter
            implements FloatingMenuCallback {
        @Override
        public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
        }

        @Override
        public void onFloatingMenuDismissed(FloatingMenu menu) {
        }
    }

    private static class FloatingMenuArrayAdapter
            extends ArrayAdapter<FloatingMenuItem> {
        public FloatingMenuArrayAdapter(Context context, int resource, List<FloatingMenuItem> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflate(parent.getContext(), R.layout.toolbar_menu_item, parent, false);
            }

            FloatingMenuItem item = getItem(position);

            TextView textView = (TextView) convertView;
            textView.setText(item.getText());
            textView.setTextColor(getAttrColor(getContext(),
                    item.isEnabled() ? R.attr.text_color_primary : R.attr.text_color_hint
            ));
            textView.setTypeface(ThemeHelper.getTheme().mainFont);

            return textView;
        }
    }
}
