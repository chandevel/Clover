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
package com.github.adamantcheese.chan.ui.toolbar;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.removeFromParentView;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * An item for the Toolbar menu. These are ImageViews with an icon, that when pressed call
 * some callback. Add them with the NavigationItem MenuBuilder.
 */
public class ToolbarMenuItem {
    private final Object id;

    private boolean visible;
    private boolean enabled;

    public Drawable drawable;

    public final List<ToolbarMenuSubItem> subItems = new ArrayList<>();

    private ToolbarItemClickCallback clickCallback;

    @Nullable
    private OverflowMenuCallback overflowMenuCallback;

    // Views, only non-null if attached to ToolbarMenuView.
    private ImageView view;

    public ToolbarMenuItem(int id, int drawable, ToolbarItemClickCallback clickCallback) {
        this(id, getAppContext().getDrawable(drawable), clickCallback);
    }

    public ToolbarMenuItem(int id, Drawable drawable, ToolbarItemClickCallback clickCallback) {
        this.id = id;
        this.drawable = drawable;
        this.clickCallback = clickCallback;
        this.visible = true;
        this.enabled = true;
    }

    public ToolbarMenuItem(
            int id,
            int drawable,
            ToolbarItemClickCallback clickCallback,
            @Nullable OverflowMenuCallback overflowMenuCallback
    ) {
        this(id, getAppContext().getDrawable(drawable), clickCallback);
        this.overflowMenuCallback = overflowMenuCallback;
    }

    public void attach(ImageView view) {
        this.view = view;
    }

    public void detach() {
        if (view == null) {
            Logger.d(this, "Already detached");
            return;
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
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (view != null) {
            if (!enabled) {
                view.setClickable(false);
                view.getDrawable().setTint(Color.GRAY);
            } else {
                view.setClickable(true);
                view.getDrawable().setTint(Color.WHITE);
            }
        }
    }

    public void setImage(int drawable) {
        setImage(getAppContext().getDrawable(drawable));
    }

    public void setImage(int drawable, boolean animated) {
        setImage(getAppContext().getDrawable(drawable), animated);
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
            TransitionDrawable transitionDrawable =
                    new TransitionDrawable(new Drawable[]{this.drawable.mutate(), drawable.mutate()});

            view.setImageDrawable(transitionDrawable);

            transitionDrawable.setCrossFadeEnabled(true);
            transitionDrawable.startTransition(100);
        }

        this.drawable = drawable;
    }

    public void showSubmenu() {
        if (view == null) {
            Logger.w(this, "Item not attached, can't show submenu");
            return;
        }

        List<FloatingMenuItem<ToolbarMenuSubItem>> floatingMenuItems = new ArrayList<>();
        for (ToolbarMenuSubItem subItem : this.subItems) {
            if (subItem.enabled) {
                floatingMenuItems.add(new FloatingMenuItem<>(subItem, subItem.text));
            }
        }

        FloatingMenu<ToolbarMenuSubItem> overflowMenu = new FloatingMenu<>(view.getContext(), view, floatingMenuItems);
        overflowMenu.setCallback(new FloatingMenu.FloatingMenuCallback<ToolbarMenuSubItem>() {
            @Override
            public void onFloatingMenuItemClicked(
                    FloatingMenu<ToolbarMenuSubItem> menu, FloatingMenuItem<ToolbarMenuSubItem> item
            ) {
                for (ToolbarMenuSubItem subItem : subItems) {
                    if (subItem == item.getId()) {
                        subItem.performClick();
                        return;
                    }
                }
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu<ToolbarMenuSubItem> menu) {
                if (overflowMenuCallback != null) {
                    overflowMenuCallback.onMenuHidden();
                }
            }
        });
        overflowMenu.show();

        if (overflowMenuCallback != null) {
            overflowMenuCallback.onMenuShown(overflowMenu);
        }
    }

    public Object getId() {
        return id;
    }

    void performClick(@SuppressWarnings("unused") View view) {
        if (clickCallback != null) {
            clickCallback.onClick(this);
        }
    }

    public void setCallback(ToolbarItemClickCallback callback) {
        clickCallback = callback;
    }

    public boolean getVisible() {
        return visible;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public interface ToolbarItemClickCallback {
        void onClick(ToolbarMenuItem item);
    }

    public interface OverflowMenuCallback {
        void onMenuShown(FloatingMenu<ToolbarMenuSubItem> menu);

        void onMenuHidden();
    }
}
