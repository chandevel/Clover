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

import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import android.widget.ImageView;

import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.getRes;
import static org.floens.chan.utils.AndroidUtils.removeFromParentView;

/**
 * An item for the Toolbar menu. These are ImageViews with an icon, that wehen pressed call
 * some callback. Add them with the NavigationItem MenuBuilder.
 */
public class ToolbarMenuItem {
    private static final String TAG = "ToolbarMenuItem";

    public Object id;
    public int order;

    public boolean overflowStyle = false;

    public boolean visible = true;

    public Drawable drawable;

    public final List<ToolbarMenuSubItem> subItems = new ArrayList<>();

    private ClickCallback clicked;

    // Views, only non-null if attached to ToolbarMenuView.
    private ImageView view;

    public ToolbarMenuItem(int id, int drawable, ClickCallback clicked) {
        this.id = id;
        this.drawable = getRes().getDrawable(drawable);
        this.clicked = clicked;
    }

    public void attach(ImageView view) {
        if (this.view != null) {
            throw new IllegalStateException("Already attached");
        }

        this.view = view;
    }

    public void detach() {
        if (this.view == null) {
            throw new IllegalStateException("Not attached");
        }

        removeFromParentView(this.view);
        this.view = null;
    }

    public ImageView getView() {
        return view;
    }

    public void addSubItem(ToolbarMenuSubItem subItem) {
        subItems.add(subItem);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;

        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setImage(int drawable) {
        setImage(getRes().getDrawable(drawable));
    }

    public void setImage(Drawable drawable) {
        setImage(drawable, false);
    }

    public void setImage(Drawable drawable, boolean animated) {
        if (view == null) {
            this.drawable = drawable;
            return;
        }

        if (!animated) {
            view.setImageDrawable(drawable);
        } else {
            TransitionDrawable transitionDrawable = new TransitionDrawable(new Drawable[]{
                    this.drawable.mutate(), drawable.mutate()
            });

            view.setImageDrawable(transitionDrawable);

            transitionDrawable.setCrossFadeEnabled(true);
            transitionDrawable.startTransition(100);
        }

        this.drawable = drawable;
    }

    public void setSubMenu(FloatingMenu subMenu) {
    }

    public void showSubmenu() {
        if (view == null) {
            Logger.w(TAG, "Item not attached, can't show submenu");
            return;
        }

        List<FloatingMenuItem> floatingMenuItems = new ArrayList<>();
        List<ToolbarMenuSubItem> subItems = new ArrayList<>(this.subItems);
        for (ToolbarMenuSubItem subItem : subItems) {
            floatingMenuItems.add(new FloatingMenuItem(subItem.id, subItem.text, subItem.enabled));
        }

        FloatingMenu overflowMenu = new FloatingMenu(view.getContext(), view, floatingMenuItems);
        overflowMenu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                ToolbarMenuSubItem subItem = subItems.get(floatingMenuItems.indexOf(item));
                subItem.performClick();
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
            }
        });
        overflowMenu.show();
    }

    public Object getId() {
        return id;
    }

    public int getOrder() {
        return order;
    }

    public void performClick() {
        if (clicked != null) {
            clicked.clicked(this);
        }
    }

    public interface ClickCallback {
        void clicked(ToolbarMenuItem item);
    }
}
