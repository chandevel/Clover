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
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;

public class ToolbarMenuItem implements View.OnClickListener, FloatingMenu.FloatingMenuCallback {
    private ToolbarMenuItemCallback callback;
    private Object id;
    private int order;
    private FloatingMenu subMenu;

    private ImageView imageView;

    public ToolbarMenuItem(Context context, ToolbarMenuItem.ToolbarMenuItemCallback callback, int order, int drawable) {
        this(context, callback, order, order, context.getResources().getDrawable(drawable));
    }

    public ToolbarMenuItem(Context context, ToolbarMenuItem.ToolbarMenuItemCallback callback, Object id, int order, int drawable) {
        this(context, callback, id, order, context.getResources().getDrawable(drawable));
    }

    public ToolbarMenuItem(Context context, ToolbarMenuItem.ToolbarMenuItemCallback callback, Object id, int order, Drawable drawable) {
        this.id = id;
        this.order = order;
        this.callback = callback;

        if (drawable != null) {
            imageView = new ImageView(context);
            imageView.setOnClickListener(this);
            imageView.setFocusable(true);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(dp(50), dp(56)));

            imageView.setImageDrawable(drawable);
            setRoundItemBackground(imageView);
        }
    }

    public void setImage(Drawable drawable) {
        imageView.setImageDrawable(drawable);
    }

    public void setImage(int drawable) {
        imageView.setImageResource(drawable);
    }

    public void setSubMenu(FloatingMenu subMenu) {
        this.subMenu = subMenu;
        subMenu.setCallback(this);
    }

    @Override
    public void onClick(View v) {
        if (subMenu != null && !subMenu.isShowing()) {
            subMenu.show();
        }
        callback.onMenuItemClicked(this);
    }

    public Object getId() {
        return id;
    }

    public int getOrder() {
        return order;
    }

    public ImageView getView() {
        return imageView;
    }

    @Override
    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
        callback.onSubMenuItemClicked(this, item);
    }

    @Override
    public void onFloatingMenuDismissed(FloatingMenu menu) {
    }

    public interface ToolbarMenuItemCallback {
        void onMenuItemClicked(ToolbarMenuItem item);

        void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item);
    }
}
