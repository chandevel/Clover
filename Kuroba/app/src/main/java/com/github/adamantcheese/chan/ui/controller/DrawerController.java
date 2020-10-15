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
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager.History;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager.SettingNotification;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.adapter.DrawerHistoryAdapter;
import com.github.adamantcheese.chan.ui.adapter.DrawerPinAdapter;
import com.github.adamantcheese.chan.ui.controller.settings.MainSettingsController;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
import static androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED;
import static androidx.recyclerview.widget.ItemTouchHelper.DOWN;
import static androidx.recyclerview.widget.ItemTouchHelper.LEFT;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;
import static androidx.recyclerview.widget.ItemTouchHelper.UP;
import static com.github.adamantcheese.chan.ui.controller.DrawerController.HeaderAction.CLEAR;
import static com.github.adamantcheese.chan.ui.controller.DrawerController.HeaderAction.CLEAR_ALL;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;
import static java.util.concurrent.TimeUnit.MINUTES;

public class DrawerController
        extends Controller
        implements DrawerPinAdapter.Callback, DrawerHistoryAdapter.Callback {
    protected FrameLayout container;
    protected DrawerLayout drawerLayout;
    protected LinearLayout drawer;

    protected LinearLayout settings;
    protected LinearLayout header;

    protected RecyclerView recyclerView;

    private boolean pinMode = true;

    public enum HeaderAction {
        CLEAR,
        CLEAR_ALL
    }

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            header.findViewById(R.id.refresh).setVisibility(VISIBLE); // TODO
        }
    };

    private final ItemTouchHelper.Callback pinItemTouchHelperCallback = new ItemTouchHelper.Callback() {
        @Override
        public int getMovementFlags(
                @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder
        ) {
            return makeMovementFlags(UP | DOWN, RIGHT | LEFT);
        }

        @Override
        public boolean onMove(
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder,
                @NonNull RecyclerView.ViewHolder target
        ) {
            if (!pinMode) return false;
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();

            synchronized (watchManager.getAllPins()) {
                Pin item = watchManager.getAllPins().remove(from);
                watchManager.getAllPins().add(to, item);
            }
            watchManager.reorder();
            getPinAdapter().notifyItemMoved(from, to);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            synchronized (watchManager.getAllPins()) {
                onPinRemoved(watchManager.getAllPins().get(viewHolder.getAdapterPosition()));
            }
        }
    };
    private ItemTouchHelper pinTouchHelper = new ItemTouchHelper(pinItemTouchHelperCallback);

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    WatchManager watchManager;

    @Inject
    WakeManager wakeManager;

    public DrawerController(Context context) {
        super(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context,
                ChanSettings.reverseDrawer.get()
                        ? R.layout.controller_navigation_drawer_reverse
                        : R.layout.controller_navigation_drawer
        );
        container = view.findViewById(R.id.container);
        drawerLayout = view.findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.panel_shadow, Gravity.LEFT);
        drawer = view.findViewById(R.id.drawer);

        settings = view.findViewById(R.id.settings);
        ((TextView) settings.findViewById(R.id.settings_text)).setTypeface(ThemeHelper.getTheme().mainFont);
        onEvent((SettingNotification) null);
        settings.setOnClickListener(v -> openController(new MainSettingsController(context)));

        view.findViewById(R.id.history).setOnClickListener(this::toggleHistoryMode);

        header = view.findViewById(R.id.header);
        header.findViewById(R.id.refresh).setOnClickListener(v -> {
            if (pinMode) {
                wakeManager.onBroadcastReceived(false);
                v.setVisibility(GONE);
                mainHandler.postDelayed(refreshRunnable, MINUTES.toMillis(5));
            } else {
                if (recyclerView.getAdapter() == null) return;
                ((DrawerHistoryAdapter) recyclerView.getAdapter()).load();
            }
        });
        header.findViewById(R.id.clear).setOnClickListener(v -> onHeaderClicked(CLEAR));
        header.findViewById(R.id.clear).setOnLongClickListener(v -> onHeaderClicked(CLEAR_ALL));

        recyclerView = view.findViewById(R.id.drawer_recycler_view);
        recyclerView.setHasFixedSize(true);

        recyclerView.setAdapter(new DrawerPinAdapter(this));
        pinTouchHelper.attachToRecyclerView(recyclerView);

        updateBadge();
    }

    @Override
    public void onDestroy() {
        pinTouchHelper.attachToRecyclerView(null);
        recyclerView.setAdapter(null);
        mainHandler.removeCallbacks(refreshRunnable);
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void setChildController(Controller childController) {
        addChildController(childController);
        childController.attachToParentView(container);
        childController.onShow();
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
        ThreadController threadController = getTopThreadController();
        if (threadController != null) {
            if (threadController instanceof BrowseController) {
                threadController.showThread(pin.loadable);
            } else if (threadController instanceof ViewThreadController) {
                ((ViewThreadController) threadController).loadThread(pin.loadable);
            }
        }
        // Post it to avoid animation jumping because the first frame is heavy.
        drawerLayout.post(() -> drawerLayout.closeDrawers());
    }

    @Override
    public void onHistoryClicked(History history) {
        ThreadController threadController = getTopThreadController();
        if (history != null && history.loadable != null && threadController != null) {
            if (threadController instanceof BrowseController) {
                threadController.showThread(history.loadable);
            } else if (threadController instanceof ViewThreadController) {
                ((ViewThreadController) threadController).loadThread(history.loadable);
            }
        } else {
            showToast(context, "Error opening history!");
        }
        // Post it to avoid animation jumping because the first frame is heavy.
        drawerLayout.post(() -> drawerLayout.closeDrawers());
    }

    public boolean onHeaderClicked(HeaderAction headerAction) {
        onHeaderClickedInternal(headerAction == CLEAR_ALL || !ChanSettings.watchEnabled.get());
        return true;
    }

    private void onHeaderClickedInternal(boolean all) {
        final List<Pin> pins = watchManager.clearPins(all);
        if (!pins.isEmpty()) {
            String text = getQuantityString(R.plurals.bookmark, pins.size(), pins.size());
            Snackbar snackbar = Snackbar.make(drawerLayout, getString(R.string.drawer_pins_cleared, text), 4000);
            snackbar.setGestureInsetBottomIgnored(true);
            snackbar.setAction(R.string.undo, v -> watchManager.addAll(pins));
            snackbar.show();
        } else {
            int text;
            synchronized (watchManager.getAllPins()) {
                text = watchManager.getAllPins().isEmpty()
                        ? R.string.drawer_pins_non_cleared
                        : R.string.drawer_pins_non_cleared_try_all;
            }
            Snackbar snackbar = Snackbar.make(drawerLayout, text, Snackbar.LENGTH_LONG);
            snackbar.setGestureInsetBottomIgnored(true);
            snackbar.show();
        }
    }

    private void toggleHistoryMode(View historyView) {
        if (pinMode) {
            // swap to history mode
            pinMode = false;
            recyclerView.setAdapter(null);
            ((ImageView) historyView).setImageResource(R.drawable.ic_bookmark_themed_24dp);
            ((TextView) header.findViewById(R.id.header_text)).setText(R.string.drawer_history);
            mainHandler.removeCallbacks(refreshRunnable);
            header.findViewById(R.id.clear).setVisibility(GONE);
            header.findViewById(R.id.refresh).setVisibility(VISIBLE);

            pinTouchHelper.attachToRecyclerView(null);
            recyclerView.setAdapter(new DrawerHistoryAdapter(this));
        } else {
            // swap to pin mode
            pinMode = true;
            recyclerView.setAdapter(null);
            ((ImageView) historyView).setImageResource(R.drawable.ic_history_themed_24dp);
            ((TextView) header.findViewById(R.id.header_text)).setText(R.string.drawer_pinned);
            header.findViewById(R.id.clear).setVisibility(VISIBLE);

            pinTouchHelper.attachToRecyclerView(recyclerView);
            recyclerView.setAdapter(new DrawerPinAdapter(this));
        }
    }

    @Override
    public void onPinRemoved(Pin pin) {
        final Pin undoPin = pin.clone();
        watchManager.deletePin(pin);

        Snackbar snackbar;

        snackbar = Snackbar.make(drawerLayout,
                getString(R.string.drawer_pin_removed, pin.loadable.title),
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction(R.string.undo, v -> watchManager.createPin(undoPin));
        snackbar.setGestureInsetBottomIgnored(true);
        snackbar.show();
    }

    public void setPinHighlighted(Pin pin) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        getPinAdapter().setHighlightedPin(pin);
    }

    @Subscribe
    public void onEvent(PinMessages.PinAddedMessage message) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        synchronized (watchManager.getAllPins()) {
            getPinAdapter().notifyItemInserted(watchManager.getAllPins().indexOf(message.pin));
        }
        if (ChanSettings.drawerAutoOpenCount.get() < 5 || ChanSettings.alwaysOpenDrawer.get()) {
            drawerLayout.openDrawer(drawer);
            //max out at 5
            int curCount = ChanSettings.drawerAutoOpenCount.get();
            ChanSettings.drawerAutoOpenCount.set(Math.min(curCount + 1, 5));
            if (ChanSettings.drawerAutoOpenCount.get() < 5 && !ChanSettings.alwaysOpenDrawer.get()) {
                int countLeft = 5 - ChanSettings.drawerAutoOpenCount.get();
                showToast(context,
                        "Drawer will auto-show " + countLeft + " more time" + (countLeft == 1 ? "" : "s")
                                + " as a reminder."
                );
            }
        }
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinRemovedMessage message) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        getPinAdapter().notifyItemRemoved(message.index);
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinChangedMessage message) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        synchronized (watchManager.getAllPins()) {
            // notify with an unused Object to indicate a "partial" update, which prevents onViewRecycled being called
            // this prevents flicker every time a thread updates
            getPinAdapter().notifyItemChanged(watchManager.getAllPins().indexOf(message.pin), new Object());
        }
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinsChangedMessage message) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        getPinAdapter().notifyDataSetChanged();
        updateBadge();
    }

    private DrawerPinAdapter getPinAdapter() {
        return (DrawerPinAdapter) recyclerView.getAdapter();
    }

    @Subscribe(sticky = true)
    public void onEvent(SettingNotification notification) {
        if (settings == null) return;
        SettingNotification type = EventBus.getDefault().getStickyEvent(SettingNotification.class);

        ImageView notificationIcon = settings.findViewById(R.id.setting_notification_icon);
        if (type != SettingNotification.Default) {
            notificationIcon.setVisibility(VISIBLE);
            notificationIcon.setColorFilter(getRes().getColor(type.getNotificationIconTintColor()));
        } else {
            notificationIcon.setVisibility(GONE);
        }
    }

    public void setDrawerEnabled(boolean enabled) {
        drawerLayout.setDrawerLockMode(enabled ? LOCK_MODE_UNLOCKED : LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
        if (!enabled) {
            drawerLayout.closeDrawer(drawer);
        }
    }

    private void updateBadge() {
        int total = 0;
        boolean color = false;
        for (Pin p : watchManager.getWatchingPins()) {
            if (p.watching || p.archived) {
                total += p.getNewPostCount();
                color = color | p.getNewQuoteCount() > 0;
            }
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
