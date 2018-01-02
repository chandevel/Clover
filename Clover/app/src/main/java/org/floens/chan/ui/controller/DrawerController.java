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
package org.floens.chan.ui.controller;

import android.content.Context;
import android.content.DialogInterface;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.adapter.DrawerAdapter;
import org.floens.chan.utils.AndroidUtils;

import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.ROBOTO_MEDIUM;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.fixSnackbarText;

public class DrawerController extends Controller implements DrawerAdapter.Callback, View.OnClickListener {
    protected FrameLayout container;
    protected DrawerLayout drawerLayout;
    protected LinearLayout drawer;
    protected RecyclerView recyclerView;
    protected LinearLayout settings;
    protected DrawerAdapter drawerAdapter;

    @Inject
    WatchManager watchManager;

    public DrawerController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        EventBus.getDefault().register(this);

        view = inflateRes(R.layout.controller_navigation_drawer);
        container = (FrameLayout) view.findViewById(R.id.container);
        drawerLayout = (DrawerLayout) view.findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.LEFT);
        drawer = (LinearLayout) view.findViewById(R.id.drawer);
        recyclerView = (RecyclerView) view.findViewById(R.id.drawer_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        settings = (LinearLayout) view.findViewById(R.id.settings);
        settings.setOnClickListener(this);
        theme().settingsDrawable.apply((ImageView) settings.findViewById(R.id.image));
        ((TextView) settings.findViewById(R.id.text)).setTypeface(ROBOTO_MEDIUM);

        drawerAdapter = new DrawerAdapter(this);
        recyclerView.setAdapter(drawerAdapter);

        drawerAdapter.onPinsChanged(watchManager.getAllPins());

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(drawerAdapter.getItemTouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(recyclerView);

        updateBadge();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);
    }

    public void setChildController(Controller childController) {
        addChildController(childController);
        childController.attachToParentView(container);
        childController.onShow();
    }

    @Override
    public void onClick(View v) {
        if (v == settings) {
            openController(new MainSettingsController(context));
        }
    }

    public void onMenuClicked() {
        if (getMainToolbarNavigationController().getTop().navigationItem.hasDrawer) {
            drawerLayout.openDrawer(drawer);
        }
    }

    @Override
    public boolean onBack() {
        if (drawerLayout.isDrawerOpen(drawer)) {
            drawerLayout.closeDrawer(drawer);
            return true;
        } else {
            return super.onBack();
        }
    }

    @Override
    public void onPinClicked(Pin pin) {
        drawerLayout.closeDrawer(Gravity.LEFT);
        ThreadController threadController = getTopThreadController();
        threadController.openPin(pin);
    }

    @Override
    public void onWatchCountClicked(Pin pin) {
        watchManager.toggleWatch(pin);
    }

    @Override
    public void onHeaderClicked(DrawerAdapter.HeaderHolder holder, DrawerAdapter.HeaderAction headerAction) {
        if (headerAction == DrawerAdapter.HeaderAction.SETTINGS) {
            openController(new WatchSettingsController(context));
        } else if (headerAction == DrawerAdapter.HeaderAction.CLEAR || headerAction == DrawerAdapter.HeaderAction.CLEAR_ALL) {
            boolean all = headerAction == DrawerAdapter.HeaderAction.CLEAR_ALL || !ChanSettings.watchEnabled.get();
            final List<Pin> pins = watchManager.clearPins(all);
            if (!pins.isEmpty()) {
                String text = context.getResources().getQuantityString(R.plurals.bookmark, pins.size(), pins.size());
                //noinspection WrongConstant
                Snackbar snackbar = Snackbar.make(drawerLayout, context.getString(R.string.drawer_pins_cleared, text), 4000);
                fixSnackbarText(context, snackbar);
                snackbar.setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        watchManager.addAll(pins);
                    }
                });
                snackbar.show();
            } else {
                int text = watchManager.getAllPins().isEmpty() ? R.string.drawer_pins_non_cleared : R.string.drawer_pins_non_cleared_try_all;
                Snackbar snackbar = Snackbar.make(drawerLayout, text, Snackbar.LENGTH_LONG);
                fixSnackbarText(context, snackbar);
                snackbar.show();
            }
        }
    }

    @Override
    public void onPinRemoved(Pin pin) {
        final Pin undoPin = pin.copy();
        watchManager.deletePin(pin);
        Snackbar snackbar = Snackbar.make(drawerLayout, context.getString(R.string.drawer_pin_removed, pin.loadable.title), Snackbar.LENGTH_LONG);
        fixSnackbarText(context, snackbar);
        snackbar.setAction(R.string.undo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                watchManager.createPin(undoPin);
            }
        });
        snackbar.show();
    }

    @Override
    public void onPinLongClocked(final Pin pin) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setPadding(dp(16), dp(16), dp(16), 0);
        final EditText text = new EditText(context);
        text.setSingleLine();
        text.setText(pin.loadable.title);
        wrap.addView(text, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setPositiveButton(R.string.action_rename, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = text.getText().toString();

                        if (!TextUtils.isEmpty(value)) {
                            pin.loadable.title = value;
                            watchManager.updatePin(pin);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setTitle(R.string.action_rename_pin)
                .setView(wrap)
                .create();

        AndroidUtils.requestKeyboardFocus(dialog, text);

        dialog.show();
    }

    @Override
    public void openSites() {
        openController(new SitesSetupController(context));
    }

    @Override
    public void openHistory() {
        openController(new HistoryController(context));
    }

    public void setPinHighlighted(Pin pin) {
        drawerAdapter.setPinHighlighted(pin);
        drawerAdapter.updateHighlighted(recyclerView);
    }

    public void onEvent(WatchManager.PinAddedMessage message) {
        drawerAdapter.onPinAdded(message.pin);
        drawerLayout.openDrawer(drawer);
        updateBadge();
    }

    public void onEvent(WatchManager.PinRemovedMessage message) {
        drawerAdapter.onPinRemoved(message.pin);
        updateBadge();
    }

    public void onEvent(WatchManager.PinChangedMessage message) {
        drawerAdapter.onPinChanged(recyclerView, message.pin);
        updateBadge();
    }

    public void setDrawerEnabled(boolean enabled) {
        drawerLayout.setDrawerLockMode(enabled ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
        if (!enabled) {
            drawerLayout.closeDrawer(drawer);
        }
    }

    private void updateBadge() {
        List<Pin> list = watchManager.getWatchingPins();
        int count = 0;
        boolean color = false;
        if (list.size() > 0) {
            for (Pin p : list) {
                count += p.getNewPostCount();
                if (p.getNewQuoteCount() > 0) {
                    color = true;
                }
            }
        }

        if (getTop() != null) {
            getMainToolbarNavigationController().toolbar.getArrowMenuDrawable().setBadge(count, color);
        }
    }

    private void openController(Controller controller) {
        Controller top = getTop();
        if (top instanceof NavigationController) {
            ((NavigationController) top).pushController(controller);
        } else if (top instanceof DoubleNavigationController) {
            ((DoubleNavigationController) top).pushController(controller);
        }

        drawerLayout.closeDrawer(Gravity.LEFT);
    }

    private ThreadController getTopThreadController() {
        ToolbarNavigationController nav = getMainToolbarNavigationController();
        if (nav.getTop() instanceof ThreadController) {
            return (ThreadController) nav.getTop();
        } else if (nav.getTop() instanceof ThreadSlideController) {
            ThreadSlideController slideNav = (ThreadSlideController) nav.getTop();
            if (slideNav.leftController instanceof ThreadController) {
                return (ThreadController) slideNav.leftController;
            }
        }
        throw new IllegalStateException();
    }

    private ToolbarNavigationController getMainToolbarNavigationController() {
        ToolbarNavigationController navigationController = null;

        Controller top = getTop();
        if (top instanceof StyledToolbarNavigationController) {
            navigationController = (StyledToolbarNavigationController) top;
        } else if (top instanceof SplitNavigationController) {
            SplitNavigationController splitNav = (SplitNavigationController) top;
            if (splitNav.getLeftController() instanceof StyledToolbarNavigationController) {
                navigationController = (StyledToolbarNavigationController) splitNav.getLeftController();
            }
        } else if (top instanceof ThreadSlideController) {
            ThreadSlideController slideNav = (ThreadSlideController) top;
            navigationController = (StyledToolbarNavigationController) slideNav.leftController;
        }

        if (navigationController == null) {
            throw new IllegalStateException("The child controller of a DrawerController must either be StyledToolbarNavigationController" +
                    "or an DoubleNavigationController that has a ToolbarNavigationController.");
        }

        return navigationController;
    }
}
