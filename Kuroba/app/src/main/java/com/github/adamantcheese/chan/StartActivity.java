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
package com.github.adamantcheese.chan;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.OnLifecycleEvent;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.UpdateManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteResolver;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
import com.github.adamantcheese.chan.ui.controller.BrowseController;
import com.github.adamantcheese.chan.ui.controller.DoubleNavigationController;
import com.github.adamantcheese.chan.ui.controller.DrawerController;
import com.github.adamantcheese.chan.ui.controller.ImageViewerNavigationController;
import com.github.adamantcheese.chan.ui.controller.SplitNavigationController;
import com.github.adamantcheese.chan.ui.controller.StyledToolbarNavigationController;
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController;
import com.github.adamantcheese.chan.ui.controller.ViewThreadController;
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Stack;

import javax.inject.Inject;

import kotlin.jvm.functions.Function1;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.AUTO;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.PHONE;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.SLIDE;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.SPLIT;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static java.util.concurrent.TimeUnit.HOURS;

public class StartActivity
        extends AppCompatActivity
        implements NfcAdapter.CreateNdefMessageCallback, FSAFActivityCallbacks {
    private static final String STATE_KEY = "chan_state";

    private final Stack<Controller> stack = new Stack<>();

    private DrawerController drawerController;
    private NavigationController mainNavigationController;
    private BrowseController browseController;

    private ImagePickDelegate imagePickDelegate;
    private RuntimePermissionsHelper runtimePermissionsHelper;
    private UpdateManager updateManager;

    private boolean intentMismatchWorkaroundActive = false;

    public static boolean loadedFromURL = false;
    private int currentNightModeBits;

    @Inject
    DatabaseLoadableManager databaseLoadableManager;
    @Inject
    SiteRepository siteRepository;
    @Inject
    FileChooser fileChooser;
    @Inject
    SiteResolver siteResolver;
    @Inject
    WatchManager watchManager;
    @Inject
    FilterWatchManager filterWatchManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        currentNightModeBits = this.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        super.onCreate(savedInstanceState);

        inject(this);

        if (intentMismatchWorkaround()) {
            return;
        }

        EmbeddingEngine.initEngine(this);

        ThemeHelper.init();
        ThemeHelper.setupContext(this);
        fileChooser.setCallbacks(this);
        imagePickDelegate = new ImagePickDelegate(this);
        runtimePermissionsHelper = new RuntimePermissionsHelper(this);
        updateManager = new UpdateManager(this);

        // Setup base controllers, and decide if to use the split layout for tablets
        drawerController = new DrawerController(this);
        drawerController.onCreate();
        drawerController.onShow();

        setupLayout();

        setContentView(drawerController.view);
        pushController(drawerController);

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter != null) {
            adapter.setNdefPushMessageCallback(this, this);
        }

        setupFromStateOrFreshLaunch(savedInstanceState);
        updateManager.autoUpdateCheck();

        if (ChanSettings.fullUserRotationEnable.get()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
    }

    private void setupFromStateOrFreshLaunch(Bundle savedInstanceState) {
        boolean handled;
        if (savedInstanceState != null) {
            handled = restoreFromSavedState(savedInstanceState);
        } else {
            handled = restoreFromUrl();
        }

        // Not from a state or from an url, launch the setup controller if no boards are setup up yet,
        // otherwise load the default saved board.
        if (!handled) {
            restoreFresh();
        }
    }

    private void restoreFresh() {
        if (siteRepository.all().getAll().isEmpty()) {
            browseController.showSitesNotSetup();
        } else {
            browseController.loadWithDefaultBoard();
        }
    }

    private boolean restoreFromUrl() {
        final Uri data = getIntent().getData();
        // Start from an url launch.
        if (data != null) {
            final Loadable loadableResult = siteResolver.resolveLoadableForUrl(data.toString());

            if (loadableResult != null) {
                loadedFromURL = true;

                browseController.setBoard(loadableResult.board);

                if (loadableResult.isThreadMode()) {
                    browseController.showThread(loadableResult);
                }
                return true;
            } else {
                getDefaultAlertBuilder(this).setMessage(getString(R.string.open_link_not_matched,
                        BuildConfig.APP_LABEL
                ))
                        .setPositiveButton(R.string.ok, (dialog, which) -> openLink(data.toString()))
                        .show();
            }
        }

        return false;
    }

    private boolean restoreFromSavedState(Bundle savedInstanceState) {
        // Restore the activity state from the previously saved state.
        ChanState chanState = savedInstanceState.getParcelable(STATE_KEY);
        if (chanState == null) {
            Logger.w(this, "savedInstanceState was not null, but no ChanState was found!");
        } else {
            Pair<Loadable, Loadable> boardThreadPair = resolveChanState(chanState);

            if (boardThreadPair.first != null) {
                browseController.setBoard(boardThreadPair.first.board);

                if (boardThreadPair.second != null) {
                    browseController.showThread(boardThreadPair.second);
                }
                return true;
            }
        }

        return false;
    }

    private Pair<Loadable, Loadable> resolveChanState(ChanState state) {
        Loadable boardLoadable = resolveLoadable(state.board, false);
        Loadable threadLoadable = resolveLoadable(state.thread, true);

        return new Pair<>(boardLoadable, threadLoadable);
    }

    private Loadable resolveLoadable(Loadable stateLoadable, boolean forThread) {
        // invalid (no state saved).
        if (stateLoadable.mode != (forThread ? Loadable.Mode.THREAD : Loadable.Mode.CATALOG)) {
            return null;
        }

        Site site = siteRepository.forId(stateLoadable.siteId);
        if (site != null) {
            Board board = site.board(stateLoadable.boardCode);
            if (board != null) {
                stateLoadable.site = site;
                stateLoadable.board = board;
                if (forThread && stateLoadable.id == 0) {
                    stateLoadable = databaseLoadableManager.get(stateLoadable);
                }

                return stateLoadable;
            }
        }

        return null;
    }

    private void setupLayout() {
        mainNavigationController = new StyledToolbarNavigationController(this);

        ChanSettings.LayoutMode layoutMode = ChanSettings.layoutMode.get();
        if (layoutMode == AUTO) {
            if (isTablet()) {
                layoutMode = SPLIT;
            } else {
                layoutMode = SLIDE;
            }
        }

        switch (layoutMode) {
            case SPLIT:
                SplitNavigationController split = new SplitNavigationController(this);
                split.setEmptyView((ViewGroup) LayoutInflater.from(this).inflate(R.layout.layout_split_empty, null));

                drawerController.setChildController(split);

                split.setLeftController(mainNavigationController);
                break;
            case PHONE:
            case SLIDE:
                drawerController.setChildController(mainNavigationController);
                break;
        }

        browseController = new BrowseController(this);

        if (layoutMode == SLIDE || layoutMode == PHONE) {
            ThreadSlideController slideController = new ThreadSlideController(this);
            slideController.setEmptyView((ViewGroup) LayoutInflater.from(this)
                    .inflate(R.layout.layout_split_empty, null));
            mainNavigationController.pushController(slideController, false);
            slideController.setLeftController(browseController);
        } else {
            mainNavigationController.pushController(browseController, false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle WatchNotification clicks
        // pop any image viewers
        popAllControllerClass(ImageViewerNavigationController.class);
        if (intent.getExtras() != null) {
            int pinId = intent.getExtras().getInt("pin_id", -2);
            if (pinId != -2 && mainNavigationController.getTop() instanceof BrowseController) {
                if (pinId == -1) {
                    drawerController.onMenuClicked();
                } else {
                    Pin pin = watchManager.findPinById(pinId);
                    if (pin != null) {
                        browseController.showThread(pin.loadable);
                    }
                }
            } else if (pinId != -2 && mainNavigationController.getTop() instanceof ThreadSlideController) {
                if (pinId == -1) {
                    drawerController.onMenuClicked();
                } else {
                    Pin pin = watchManager.findPinById(pinId);
                    if (pin != null) {
                        List<Controller> controllers = mainNavigationController.childControllers;
                        for (Controller controller : controllers) {
                            if (controller instanceof ViewThreadController) {
                                ((ViewThreadController) controller).loadThread(pin.loadable);
                                break;
                            } else if (controller instanceof ThreadSlideController) {
                                ThreadSlideController slideNav = (ThreadSlideController) controller;
                                if (slideNav.getRightController() instanceof ViewThreadController) {
                                    ((ViewThreadController) slideNav.getRightController()).loadThread(pin.loadable);
                                } else {
                                    ViewThreadController v = new ViewThreadController(this, pin.loadable);
                                    slideNav.setRightController(v);
                                }
                                slideNav.switchToController(false);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_DOWN) {
            drawerController.onMenuClicked();
            return true;
        }

        return stack.peek().dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Loadable board = browseController.getLoadable();
        if (board == null) {
            Logger.w(this, "Can not save instance state, the board loadable is null");
        } else {
            Loadable thread = null;

            if (drawerController.childControllers.get(0) instanceof SplitNavigationController) {
                SplitNavigationController dblNav = (SplitNavigationController) drawerController.childControllers.get(0);
                if (dblNav.getRightController() instanceof NavigationController) {
                    NavigationController rightNavigationController = (NavigationController) dblNav.getRightController();
                    for (Controller controller : rightNavigationController.childControllers) {
                        if (controller instanceof ViewThreadController) {
                            thread = ((ViewThreadController) controller).getLoadable();
                            break;
                        }
                    }
                }
            } else {
                List<Controller> controllers = mainNavigationController.childControllers;
                for (Controller controller : controllers) {
                    if (controller instanceof ViewThreadController) {
                        thread = ((ViewThreadController) controller).getLoadable();
                        break;
                    } else if (controller instanceof ThreadSlideController) {
                        ThreadSlideController slideNav = (ThreadSlideController) controller;
                        if (slideNav.getRightController() instanceof ViewThreadController) {
                            thread = ((ViewThreadController) slideNav.getRightController()).getLoadable();
                            break;
                        }
                    }
                }
            }

            if (thread == null) {
                // Make the parcel happy
                thread = Loadable.emptyLoadable();
            }

            outState.putParcelable(STATE_KEY, new ChanState(board.clone(), thread.clone()));
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        Controller threadController = null;
        if (drawerController.childControllers.get(0) instanceof DoubleNavigationController) {
            SplitNavigationController splitNavigationController =
                    (SplitNavigationController) drawerController.childControllers.get(0);
            if (splitNavigationController.rightController instanceof NavigationController) {
                NavigationController rightNavigationController =
                        (NavigationController) splitNavigationController.rightController;
                for (Controller controller : rightNavigationController.childControllers) {
                    if (controller instanceof NfcAdapter.CreateNdefMessageCallback) {
                        threadController = controller;
                        break;
                    }
                }
            }
        }

        if (threadController == null) {
            threadController = mainNavigationController.getTop();
        }

        if (threadController instanceof NfcAdapter.CreateNdefMessageCallback) {
            return ((NfcAdapter.CreateNdefMessageCallback) threadController).createNdefMessage(event);
        } else {
            return null;
        }
    }

    public void pushController(Controller controller) {
        stack.push(controller);
    }

    public boolean isControllerAdded(Function1<Controller, Boolean> predicate) {
        for (Controller controller : stack) {
            if (predicate.invoke(controller)) {
                return true;
            }
        }

        return false;
    }

    public void popController(Controller controller) {
        //we permit removal of things not on the top of the stack, but everything gets shifted down so the top of the stack
        //remains the same
        stack.remove(controller);
    }

    public void popAllControllerClass(Class<? extends Controller> controllerClass) {
        for (Controller controller : stack) {
            if (controller.getClass().equals(controllerClass)) {
                controller.stopPresenting();
            }
        }
    }

    public ViewGroup getContentView() {
        return findViewById(android.R.id.content);
    }

    public ImagePickDelegate getImagePickDelegate() {
        return imagePickDelegate;
    }

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public RuntimePermissionsHelper getRuntimePermissionsHelper() {
        return runtimePermissionsHelper;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (isAndroid10() && (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) != currentNightModeBits
                && ThemeHelper.areDayAndNightThemesDifferent()) {
            restartApp();
        }

        for (Controller controller : stack) {
            controller.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onBackPressed() {
        if (!stack.peek().onBack()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        runtimePermissionsHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (intentMismatchWorkaround()) {
            return;
        }

        fileChooser.removeCallbacks();

        while (!stack.isEmpty()) {
            Controller controller = stack.pop();

            controller.onHide();
            controller.onDestroy();
        }

        NetUtils.applicationClient.dispatcher().cancelAll();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (fileChooser.onActivityResult(requestCode, resultCode, data)) {
            return;
        }

        imagePickDelegate.onActivityResult(requestCode, resultCode, data);
    }

    private boolean intentMismatchWorkaround() {
        // Workaround for an intent mismatch that causes a new activity instance to be started
        // every time the app is launched from the launcher.
        // See https://issuetracker.google.com/issues/36907463
        // Still unfixed as of 5/15/2019
        if (intentMismatchWorkaroundActive) {
            return true;
        }

        if (!isTaskRoot()) {
            Intent intent = getIntent();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN.equals(intent.getAction())) {
                Logger.w(this, "Workaround for intent mismatch.");
                intentMismatchWorkaroundActive = true;
                finish();
                return true;
            }
        }
        return false;
    }

    public void restartApp() {
        Intent intent = new Intent(this, StartActivity.class);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();

        Runtime.getRuntime().exit(0);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(Chan.ForegroundChangedMessage message) {
        if (!message.inForeground) {
            DatabaseUtils.runTaskAsync(databaseLoadableManager.purgeOld());
            File requestedFiles = new File(getCacheDir(), "requested");
            File[] files = requestedFiles.listFiles();
            if (files == null) return;
            for (File f : files) {
                if (System.currentTimeMillis() > f.lastModified() + HOURS.toMillis(1)) {
                    f.delete();
                }
            }
        }
    }

    @Override
    public void fsafStartActivityForResult(@NotNull Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }
}
