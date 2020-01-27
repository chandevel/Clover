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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.adapter.DrawerAdapter;
import com.github.adamantcheese.chan.ui.controller.settings.MainSettingsController;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.adapter.DrawerAdapter.TYPE_PIN;
import static com.github.adamantcheese.chan.utils.AndroidUtils.fixSnackbarText;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class DrawerController
        extends Controller
        implements DrawerAdapter.Callback, View.OnClickListener {
    protected FrameLayout container;
    protected DrawerLayout drawerLayout;
    protected LinearLayout drawer;
    protected RecyclerView recyclerView;
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

        view = inflate(context, R.layout.controller_navigation_drawer);
        container = view.findViewById(R.id.container);
        drawerLayout = view.findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.panel_shadow, Gravity.LEFT);
        drawer = view.findViewById(R.id.drawer);
        recyclerView = view.findViewById(R.id.drawer_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.getRecycledViewPool().setMaxRecycledViews(TYPE_PIN, 0);

        drawerAdapter = new DrawerAdapter(this, context);
        recyclerView.setAdapter(drawerAdapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(drawerAdapter.getItemTouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(recyclerView);

        updateBadge();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        recyclerView.setAdapter(null);
        EventBus.getDefault().unregister(this);
    }

    public void setChildController(Controller childController) {
        addChildController(childController);
        childController.attachToParentView(container);
        childController.onShow();
    }

    @Override
    public void onClick(View v) {

    }

    public void onMenuClicked() {
        if (getMainToolbarNavigationController().getTop().navigation.hasDrawer) {
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
        // Post it to avoid animation jumping because the first frame is heavy.
        // TODO: probably twice because of some force redraw, fix that.
        drawerLayout.post(() -> drawerLayout.post(() -> drawerLayout.closeDrawer(drawer)));

        ThreadController threadController = getTopThreadController();
        if (threadController != null) {
            Loadable.LoadableDownloadingState state = Loadable.LoadableDownloadingState.NotDownloading;

            if (PinType.hasDownloadFlag(pin.pinType)) {
                // Try to load saved copy of a thread if pinned thread has an error flag but only if
                // we are downloading this thread. Otherwise it will break archived threads that are not
                // being downloaded
                SavedThread savedThread = watchManager.findSavedThreadByLoadableId(pin.loadable.id);
                if (savedThread != null) {
                    // Do not check for isArchived here since we don't want to show archived threads
                    // as local threads
                    if (pin.isError) {
                        state = Loadable.LoadableDownloadingState.AlreadyDownloaded;
                    } else {
                        if (savedThread.isFullyDownloaded) {
                            state = Loadable.LoadableDownloadingState.AlreadyDownloaded;
                        } else {
                            // TODO(LocalThreads): we can check here that the user has no internet connection
                            //  and load the local thread right away so the user doesn't have
                            //  to do it manually
                            state = Loadable.LoadableDownloadingState.DownloadingAndNotViewable;
                        }
                    }
                }
            }

            pin.loadable.loadableDownloadingState = state;
            threadController.openPin(pin);
        }
    }

    @Override
    public void onWatchCountClicked(Pin pin) {
        watchManager.toggleWatch(pin);
    }

    @Override
    public void onHeaderClicked(DrawerAdapter.HeaderAction headerAction) {
        if (headerAction == DrawerAdapter.HeaderAction.CLEAR || headerAction == DrawerAdapter.HeaderAction.CLEAR_ALL) {
            final boolean all =
                    headerAction == DrawerAdapter.HeaderAction.CLEAR_ALL || !ChanSettings.watchEnabled.get();
            final boolean hasDownloadFlag = watchManager.hasAtLeastOnePinWithDownloadFlag();

            if (all && hasDownloadFlag) {
                // Some pins may have threads that have saved copies on the disk. We want to warn the
                // user that this action will delete them as well
                new AlertDialog.Builder(context).setTitle(R.string.warning)
                        .setMessage(R.string.drawer_controller_at_least_one_pin_has_download_flag)
                        .setNegativeButton(R.string.drawer_controller_do_not_delete,
                                (dialog, which) -> dialog.dismiss()
                        )
                        .setPositiveButton(R.string.drawer_controller_delete_all_pins,
                                ((dialog, which) -> onHeaderClickedInternal(true, true))
                        )
                        .create()
                        .show();
                return;
            }

            onHeaderClickedInternal(all, hasDownloadFlag);
        }
    }

    private void onHeaderClickedInternal(boolean all, boolean hasDownloadFlag) {
        final List<Pin> pins = watchManager.clearPins(all);
        if (!pins.isEmpty()) {
            if (!hasDownloadFlag) {
                // We can't undo this operation when there is at least one pin that downloads a thread
                // because we will be deleting files from the disk. We don't want to warn the user
                // every time he deletes one pin.
                String text = getQuantityString(R.plurals.bookmark, pins.size(), pins.size());
                Snackbar snackbar = Snackbar.make(drawerLayout, getString(R.string.drawer_pins_cleared, text), 4000);
                fixSnackbarText(context, snackbar);
                snackbar.setAction(R.string.undo, v -> watchManager.addAll(pins));
                snackbar.show();
            }
        } else {
            int text = watchManager.getAllPins().isEmpty()
                    ? R.string.drawer_pins_non_cleared
                    : R.string.drawer_pins_non_cleared_try_all;
            Snackbar snackbar = Snackbar.make(drawerLayout, text, Snackbar.LENGTH_LONG);
            fixSnackbarText(context, snackbar);
            snackbar.show();
        }
    }

    @Override
    public void onPinRemoved(Pin pin) {
        final Pin undoPin = pin.clone();
        watchManager.deletePin(pin);

        Snackbar snackbar;

        if (!PinType.hasDownloadFlag(pin.pinType)) {
            snackbar = Snackbar.make(drawerLayout,
                    getString(R.string.drawer_pin_removed, pin.loadable.title),
                    Snackbar.LENGTH_LONG
            );

            snackbar.setAction(R.string.undo, v -> watchManager.createPin(undoPin));
        } else {
            snackbar = Snackbar.make(drawerLayout,
                    getString(R.string.drawer_pin_with_saved_thread_removed, pin.loadable.title),
                    Snackbar.LENGTH_LONG
            );
        }

        fixSnackbarText(context, snackbar);
        snackbar.show();
    }

    @Override
    public void openSettings() {
        openController(new MainSettingsController(context));
    }

    @Override
    public void openHistory() {
        openController(new HistoryController(context));
    }

    public void setPinHighlighted(Pin pin) {
        drawerAdapter.setPinHighlighted(pin);
        drawerAdapter.updateHighlighted(recyclerView);
    }

    @Subscribe
    public void onEvent(PinMessages.PinAddedMessage message) {
        drawerAdapter.onPinAdded(message.pin);
        if (ChanSettings.drawerAutoOpenCount.get() < 5 || ChanSettings.alwaysOpenDrawer.get()) {
            drawerLayout.openDrawer(drawer);
            //max out at 5
            int curCount = ChanSettings.drawerAutoOpenCount.get();
            ChanSettings.drawerAutoOpenCount.set(curCount + 1 > 5 ? 5 : curCount + 1);
            if (ChanSettings.drawerAutoOpenCount.get() < 5 && !ChanSettings.alwaysOpenDrawer.get()) {
                int countLeft = 5 - ChanSettings.drawerAutoOpenCount.get();
                showToast("Drawer will auto-show " + countLeft + " more time" + (countLeft == 1 ? "" : "s")
                        + " as a reminder.");
            }
        }
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinRemovedMessage message) {
        drawerAdapter.onPinRemoved(message.index);
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinChangedMessage message) {
        drawerAdapter.onPinChanged(recyclerView, message.pin);
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinsChangedMessage message) {
        drawerAdapter.notifyDataSetChanged();
        updateBadge();
    }

    public void setDrawerEnabled(boolean enabled) {
        drawerLayout.setDrawerLockMode(enabled ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                Gravity.LEFT
        );
        if (!enabled) {
            drawerLayout.closeDrawer(drawer);
        }
    }

    private void updateBadge() {
        int total = 0;
        boolean color = false;
        for (Pin p : watchManager.getWatchingPins()) {
            if (!PinType.hasWatchNewPostsFlag(p.pinType)) {
                continue;
            }

            total += p.getNewPostCount();
            color = color | p.getNewQuoteCount() > 0;
        }

        if (getTop() != null) {
            getMainToolbarNavigationController().toolbar.getArrowMenuDrawable().setBadge(total, color);
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

        return null;
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
            throw new IllegalStateException(
                    "The child controller of a DrawerController must either be StyledToolbarNavigationController"
                            + "or an DoubleNavigationController that has a ToolbarNavigationController.");
        }

        return navigationController;
    }
}
