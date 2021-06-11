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
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager.History;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager.SettingNotification;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.adapter.DrawerHistoryAdapter;
import com.github.adamantcheese.chan.ui.adapter.DrawerPinAdapter;
import com.github.adamantcheese.chan.ui.controller.settings.MainSettingsController;
import com.github.adamantcheese.chan.ui.layout.SearchLayout;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.skydoves.balloon.ArrowOrientation;

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
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.database.DatabaseLoadableManager.EPOCH_DATE;
import static com.github.adamantcheese.chan.ui.controller.DrawerController.HeaderAction.CLEAR;
import static com.github.adamantcheese.chan.ui.controller.DrawerController.HeaderAction.CLEAR_ALL;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.clearAnySelectionsAndKeyboards;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class DrawerController
        extends Controller
        implements DrawerPinAdapter.Callback, DrawerHistoryAdapter.Callback {
    protected FrameLayout container;
    protected DrawerLayout drawerLayout;
    protected LinearLayout drawer;

    protected LinearLayout settings;
    protected CrossfadeView buttonSearchSwitch;

    protected RecyclerView recyclerView;

    private LinearLayout message;
    private TextView messageText;
    private TextView messageAction;

    private boolean pinMode = true;
    private boolean inViewMode = true;

    public enum HeaderAction {
        CLEAR,
        CLEAR_ALL
    }

    private final ItemTouchHelper.Callback drawerItemTouchHelperCallback = new ItemTouchHelper.Callback() {
        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(pinMode ? (UP | DOWN) : 0, LEFT | RIGHT);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return pinMode;
        }

        @Override
        public boolean onMove(
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder,
                @NonNull RecyclerView.ViewHolder target
        ) {
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();

            synchronized (watchManager.getAllPins()) {
                Pin item = watchManager.getAllPins().remove(from);
                watchManager.getAllPins().add(to, item);
            }
            watchManager.reorder();
            recyclerView.getAdapter().notifyItemMoved(from, to);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            if (pinMode) {
                synchronized (watchManager.getAllPins()) {
                    onPinRemoved(watchManager.getAllPins().get(viewHolder.getAdapterPosition()));
                }
            } else {
                try {
                    Loadable historyLoadable = ((DrawerHistoryAdapter.HistoryCell) viewHolder).getHistory().loadable;
                    historyLoadable.lastLoadDate = EPOCH_DATE;
                    DatabaseUtils.runTask(instance(DatabaseLoadableManager.class).updateLoadable(historyLoadable,
                            true
                    ));
                    ((DrawerHistoryAdapter) recyclerView.getAdapter()).load();
                } catch (Exception e) {
                    showToast(context, "Failed to mark loadable to not show in history!");
                }
            }
        }
    };
    private final ItemTouchHelper drawerTouchHelper = new ItemTouchHelper(drawerItemTouchHelperCallback);

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Inject
    WatchManager watchManager;

    public DrawerController(Context context) {
        super(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = (ViewGroup) LayoutInflater.from(context)
                .inflate(ChanSettings.reverseDrawer.get()
                        ? R.layout.controller_navigation_drawer_reverse
                        : R.layout.controller_navigation_drawer, null);
        container = view.findViewById(R.id.container);
        drawerLayout = view.findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.panel_shadow, Gravity.LEFT);
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                clearAnySelectionsAndKeyboards(context);
                AndroidUtils.getBaseToolTip(context)
                        .setPreferenceName("DrawerPinHistoryHint")
                        .setArrowOrientation(ArrowOrientation.TOP)
                        .setText("Tap to view history/bookmarks")
                        .build()
                        .showAlignBottom(view.findViewById(R.id.history_pin_mode_toggle));
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {}

            @Override
            public void onDrawerStateChanged(int newState) {}
        });
        drawer = view.findViewById(R.id.drawer);

        settings = view.findViewById(R.id.settings);
        ((TextView) settings.findViewById(R.id.settings_text)).setTypeface(ThemeHelper.getTheme().mainFont);
        onEvent((SettingNotification) null);
        settings.setOnClickListener(v -> openController(new MainSettingsController(context)));

        view.findViewById(R.id.history_pin_mode_toggle).setOnClickListener(v -> {
            togglePinHistoryMode((ImageView) v);
            ((SearchLayout) buttonSearchSwitch.findViewById(R.id.searchview)).setText("");
            ((SearchLayout.SearchLayoutCallback) recyclerView.getAdapter()).onClearPressedWhenEmpty();
            buttonSearchSwitch.toggle(true, true);
            inViewMode = true;
        });

        buttonSearchSwitch = view.findViewById(R.id.header);
        buttonSearchSwitch.toggle(true, false); // initialization step, required

        LinearLayout buttonsHeader = buttonSearchSwitch.findViewById(R.id.buttons);
        buttonsHeader.findViewById(R.id.search).setOnClickListener(v -> {
            inViewMode = !inViewMode;
            buttonSearchSwitch.toggle(inViewMode, true);
            if (!inViewMode) {
                buttonSearchSwitch.findViewById(R.id.searchview).requestFocus();
            }
        });
        ImageView clearButton = buttonsHeader.findViewById(R.id.clear);
        clearButton.setOnClickListener(v -> onHeaderClicked(CLEAR));
        clearButton.setOnLongClickListener(v -> onHeaderClicked(CLEAR_ALL));

        SearchLayout searchLayout = buttonSearchSwitch.findViewById(R.id.searchview);
        searchLayout.setAlwaysShowClear();
        searchLayout.setThemedSearchColors();
        searchLayout.setCallback(new SearchLayout.SearchLayoutCallback() {
            @Override
            public void onSearchEntered(String entered) {
                ((SearchLayout.SearchLayoutCallback) recyclerView.getAdapter()).onSearchEntered(entered);
            }

            @Override
            public void onClearPressedWhenEmpty() {
                ((SearchLayout.SearchLayoutCallback) recyclerView.getAdapter()).onClearPressedWhenEmpty();
                buttonSearchSwitch.toggle(!inViewMode, true);
                inViewMode = !inViewMode;
            }
        });

        recyclerView = view.findViewById(R.id.drawer_recycler_view);
        recyclerView.setHasFixedSize(true);

        recyclerView.setAdapter(new DrawerPinAdapter(this));
        drawerTouchHelper.attachToRecyclerView(recyclerView);

        SwipeRefreshLayout refreshLayout = view.findViewById(R.id.refresh_layout);
        refreshLayout.setOnRefreshListener(() -> {
            refreshLayout.setRefreshing(false);
            if (pinMode) {
                WakeManager.getInstance().onBroadcastReceived(!BuildConfig.DEBUG);
            } else {
                if (recyclerView.getAdapter() == null) return;
                ((DrawerHistoryAdapter) recyclerView.getAdapter()).load();
            }
        });

        updateBadge();

        message = view.findViewById(R.id.message);
        messageText = view.findViewById(R.id.message_text);
        messageAction = view.findViewById(R.id.message_action);
    }

    @Override
    public void onDestroy() {
        drawerTouchHelper.attachToRecyclerView(null);
        recyclerView.setAdapter(null);
        handler.removeCallbacksAndMessages(null);
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
        if (!inViewMode) {
            inViewMode = true;
            buttonSearchSwitch.toggle(inViewMode, true);
            return true;
        } else if (drawerLayout.isDrawerOpen(drawer)) {
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
        if (pinMode) {
            onHeaderClickedInternal(headerAction == CLEAR_ALL || !ChanSettings.watchEnabled.get());
        } else {
            if (headerAction == CLEAR_ALL) {
                DatabaseUtils.runTaskAsync(instance(DatabaseLoadableManager.class).clearHistory());
                togglePinHistoryMode(view.findViewById(R.id.history_pin_mode_toggle));
            } else {
                showToast(context, R.string.clear_history, Toast.LENGTH_LONG);
            }
        }
        return true;
    }

    private void onHeaderClickedInternal(boolean all) {
        final List<Pin> pins = watchManager.clearPins(all);
        if (!pins.isEmpty()) {
            openMessage(getString(R.string.drawer_pins_cleared,
                    getQuantityString(R.plurals.bookmark, pins.size(), pins.size())
            ), v -> watchManager.addAll(pins), getString(R.string.undo));
        } else {
            int text;
            synchronized (watchManager.getAllPins()) {
                text = watchManager.getAllPins().isEmpty()
                        ? R.string.drawer_pins_non_cleared
                        : R.string.drawer_pins_non_cleared_try_all;
            }

            openMessage(getString(text), null, "");
        }
    }

    private void togglePinHistoryMode(ImageView toggleView) {
        if (pinMode) {
            // swap to history mode
            pinMode = false;
            recyclerView.setAdapter(null);
            toggleView.setImageResource(R.drawable.ic_fluent_bookmark_24_filled);
            ((TextView) buttonSearchSwitch.findViewById(R.id.header_text)).setText(R.string.drawer_history);
            handler.removeCallbacksAndMessages(null);
            synchronized (watchManager.getAllPins()) {
                for (Pin p : watchManager.getAllPins()) {
                    p.drawerHighlight = false; // clear all highlights
                }
            }
            recyclerView.setAdapter(new DrawerHistoryAdapter(this));
        } else {
            // swap to pin mode
            pinMode = true;
            recyclerView.setAdapter(null);
            toggleView.setImageResource(R.drawable.ic_fluent_history_24_filled);
            ((TextView) buttonSearchSwitch.findViewById(R.id.header_text)).setText(R.string.drawer_pinned);

            recyclerView.setAdapter(new DrawerPinAdapter(this));
        }
    }

    @Override
    public void onPinRemoved(Pin pin) {
        final Pin undoPin = pin.clone();
        watchManager.deletePin(pin);
        openMessage(getString(R.string.drawer_pin_removed, pin.loadable.title),
                v -> watchManager.createPin(undoPin),
                getString(R.string.undo)
        );
    }

    private final Runnable closeMessageRunnable = new Runnable() {
        @Override
        public void run() {
            messageText.setText(R.string.empty);
            messageAction.setText(R.string.empty);
            messageAction.setOnClickListener(null);
            view.findViewById(R.id.action_divider).setVisibility(GONE);
            message.setVisibility(GONE);
        }
    };

    private void openMessage(
            @NonNull String text, @Nullable View.OnClickListener action, @NonNull String actionText
    ) {
        view.removeCallbacks(closeMessageRunnable);
        messageText.setText(text);
        messageAction.setVisibility(actionText.isEmpty() ? GONE : VISIBLE);
        messageAction.setText(actionText.isEmpty() ? "" : actionText);
        view.findViewById(R.id.action_divider).setVisibility(actionText.isEmpty() ? GONE : VISIBLE);
        message.findViewById(R.id.message_action).setOnClickListener(v -> {
            if (action != null) {
                action.onClick(v);
            }
            closeMessageRunnable.run();
        });
        message.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);

        if (!TextUtils.isEmpty(text)) {
            view.postDelayed(closeMessageRunnable, 5000);
        }
    }

    public void setPinHighlighted(Pin pin) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        ((DrawerPinAdapter) recyclerView.getAdapter()).setHighlightedPin(pin);
    }

    @Subscribe
    public void onEvent(PinMessages.PinAddedMessage message) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        synchronized (watchManager.getAllPins()) {
            recyclerView.getAdapter().notifyItemInserted(watchManager.getAllPins().indexOf(message.pin));
            recyclerView.scrollToPosition(watchManager.getAllPins().indexOf(message.pin));
        }
        if (ChanSettings.alwaysOpenDrawer.get()) {
            drawerLayout.openDrawer(drawer);
        }
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinRemovedMessage message) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        recyclerView.getAdapter().notifyItemRemoved(message.index);
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinChangedMessage message) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        synchronized (watchManager.getAllPins()) {
            // notify with an unused Object to indicate a "partial" update, which prevents onViewRecycled being called
            // this prevents flicker every time a thread updates
            recyclerView.getAdapter().notifyItemChanged(watchManager.getAllPins().indexOf(message.pin), new Object());
        }
        updateBadge();
    }

    @Subscribe
    public void onEvent(PinMessages.PinsChangedMessage message) {
        if (recyclerView.getAdapter() == null || !pinMode) return;
        recyclerView.getAdapter().notifyDataSetChanged();
        updateBadge();
    }

    @Subscribe(sticky = true)
    public void onEvent(SettingNotification notification) {
        if (settings == null) return;
        SettingNotification type = EventBus.getDefault().getStickyEvent(SettingNotification.class);

        ImageView notificationIcon = settings.findViewById(R.id.setting_notification_icon);
        if (type != SettingNotification.Default) {
            notificationIcon.setVisibility(VISIBLE);
            notificationIcon.setImageTintList(ColorStateList.valueOf(getRes().getColor(type.getNotificationIconTintColor())));
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
