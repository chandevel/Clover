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

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.removeFromParentView;

/**
 * An item for the Toolbar menu. These are ImageViews with an icon, that wehen pressed call
 * some callback. Add them with the NavigationItem MenuBuilder.
 */
public class ToolbarMenuItem {
    private static final String TAG = "ToolbarMenuItem";

    public Object id;

    public boolean overflowStyle = false;
    public boolean visible = true;
    public boolean enabled = true;

    public Drawable drawable;

    public final List<ToolbarMenuSubItem> subItems = new ArrayList<>();

    private ClickCallback clicked;

    @Nullable
    private ToobarThreedotMenuCallback threedotMenuCallback;

    // Views, only non-null if attached to ToolbarMenuView.
    private ImageView view;

    public ToolbarMenuItem(int id, int drawable, ClickCallback clicked) {
        this(id, getAppContext().getDrawable(drawable), clicked);
    }

    public ToolbarMenuItem(int id, Drawable drawable, ClickCallback clicked) {
        this.id = id;
        this.drawable = drawable;
        this.clicked = clicked;
    }

    public ToolbarMenuItem(
            int id, int drawable, ClickCallback clicked, @Nullable ToobarThreedotMenuCallback threedotMenuCallback
    ) {
        this.id = id;
        this.drawable = getAppContext().getDrawable(drawable);
        this.clicked = clicked;
        this.threedotMenuCallback = threedotMenuCallback;
    }

    public void attach(ImageView view) {
        this.view = view;
    }

    public void detach() {
        if (view == null) {
            Logger.d(TAG, "Already detached");
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

    public void removeSubItem(ToolbarMenuSubItem subItem) {
        subItems.remove(subItem);
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
                view.setFocusable(false);
                view.getDrawable().setTint(Color.GRAY);
            } else {
                view.setClickable(true);
                view.setFocusable(true);
                view.getDrawable().setTint(Color.WHITE);
            }
        }
    }

    public void setImage(int drawable) {
        setImage(getAppContext().getDrawable(drawable));
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
                if (threedotMenuCallback != null) {
                    threedotMenuCallback.onMenuHidden();
                }
            }
        });
        overflowMenu.show();

        if (threedotMenuCallback != null) {
            threedotMenuCallback.onMenuShown();
        }
    }

    public Object getId() {
        return id;
    }

    public void performClick() {
        if (clicked != null) {
            clicked.clicked(this);
        }
    }

    public void setCallback(ClickCallback callback) {
        clicked = callback;
    }

    public interface ClickCallback {
        void clicked(ToolbarMenuItem item);
    }

    public interface ToobarThreedotMenuCallback {
        void onMenuShown();

        void onMenuHidden();
    }
}
