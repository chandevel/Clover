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
package org.floens.chan.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanHelper;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.core.database.DatabaseLoadableManager;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.Sites;
import org.floens.chan.ui.controller.BrowseController;
import org.floens.chan.ui.controller.DoubleNavigationController;
import org.floens.chan.ui.controller.DrawerController;
import org.floens.chan.ui.controller.SiteSetupController;
import org.floens.chan.ui.controller.SplitNavigationController;
import org.floens.chan.ui.controller.StyledToolbarNavigationController;
import org.floens.chan.ui.controller.ThreadSlideController;
import org.floens.chan.ui.controller.ViewThreadController;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.helper.RuntimePermissionsHelper;
import org.floens.chan.ui.helper.VersionHandler;
import org.floens.chan.ui.state.ChanState;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;

public class StartActivity extends AppCompatActivity implements NfcAdapter.CreateNdefMessageCallback {
    private static final String TAG = "StartActivity";

    private static final String STATE_KEY = "chan_state";

    private ViewGroup contentView;
    private List<Controller> stack = new ArrayList<>();

    private DrawerController drawerController;
    private NavigationController mainNavigationController;
    private BrowseController browseController;

    private ImagePickDelegate imagePickDelegate;
    private RuntimePermissionsHelper runtimePermissionsHelper;
    private VersionHandler versionHandler;

    @Inject
    DatabaseManager databaseManager;

    @Inject
    BoardManager boardManager;

    @Inject
    WatchManager watchManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getGraph().inject(this);

        ThemeHelper.getInstance().setupContext(this);

        imagePickDelegate = new ImagePickDelegate(this);
        runtimePermissionsHelper = new RuntimePermissionsHelper(this);
        versionHandler = new VersionHandler(this, runtimePermissionsHelper);

        contentView = (ViewGroup) findViewById(android.R.id.content);

        // Setup base controllers, and decide if to use the split layout for tablets
        drawerController = new DrawerController(this);
        drawerController.onCreate();
        drawerController.onShow();

        setupLayout();

        setContentView(drawerController.view);
        addController(drawerController);

        // Prevent overdraw
        // Do this after setContentView, or the decor creating will reset the background to a default non-null drawable
        getWindow().setBackgroundDrawable(null);

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter != null) {
            adapter.setNdefPushMessageCallback(this, this);
        }

        setupFromStateOrFreshLaunch(savedInstanceState);

        versionHandler.run();
    }

    private void setupFromStateOrFreshLaunch(Bundle savedInstanceState) {
        boolean loadDefault = true;
        if (savedInstanceState != null) {
            // Restore the activity state from the previously saved state.
            ChanState chanState = savedInstanceState.getParcelable(STATE_KEY);
            if (chanState == null) {
                Logger.w(TAG, "savedInstanceState was not null, but no ChanState was found!");
            } else {
                Pair<Loadable, Loadable> boardThreadPair = resolveChanState(chanState);

                if (boardThreadPair != null && boardThreadPair.first != null) {
                    loadDefault = false;

                    browseController.loadBoard(boardThreadPair.first.board);

                    if (boardThreadPair.second != null) {
                        browseController.showThread(boardThreadPair.second);
                    }
                }
            }
        } else {
            final Uri data = getIntent().getData();
            // Start from an url launch.
            if (data != null) {
                Loadable fromUri = ChanHelper.getLoadableFromStartUri(data);
                if (fromUri != null) {
                    loadDefault = false;
                    browseController.loadBoard(fromUri.board);

                    if (fromUri.isThreadMode()) {
                        browseController.showThread(fromUri, false);
                    }
                } else {
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.open_link_not_matched)
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    AndroidUtils.openLink(data.toString());
                                }
                            })
                            .show();
                }
            }
        }

        // Not from a state or from an url, launch the setup controller if no boards are setup up yet,
        // otherwise load the default saved board.
        if (loadDefault) {
            if (boardManager.getSavedBoards().isEmpty()) {
                mainNavigationController.pushController(new SiteSetupController(this), false);
            } else {
                browseController.loadDefault();
            }
        }
    }

    private Pair<Loadable, Loadable> resolveChanState(ChanState state) {
        DatabaseLoadableManager loadableManager = databaseManager.getDatabaseLoadableManager();

        Site site = Sites.forId(state.board.siteId);
        Board board = site.board(state.board.boardCode);
        if (board != null) {
            state.board.site = site;
            state.board.board = board;
            state.thread.site = site;
            state.thread.board = board;

            Loadable boardLoadable = loadableManager.get(state.board);
            Loadable threadLoadable = loadableManager.get(state.thread);

            return new Pair<>(boardLoadable, threadLoadable.mode == Loadable.Mode.THREAD ? threadLoadable : null);
        }

        return null;
    }

    private void setupLayout() {
        mainNavigationController = new StyledToolbarNavigationController(this);

        ChanSettings.LayoutMode layoutMode = ChanSettings.layoutMode.get();
        if (layoutMode == ChanSettings.LayoutMode.AUTO) {
            if (AndroidUtils.isTablet(this)) {
                layoutMode = ChanSettings.LayoutMode.SPLIT;
            } else {
                layoutMode = ChanSettings.LayoutMode.SLIDE;
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

        if (layoutMode == ChanSettings.LayoutMode.SLIDE) {
            ThreadSlideController slideController = new ThreadSlideController(this);
            slideController.setEmptyView((ViewGroup) LayoutInflater.from(this).inflate(R.layout.layout_split_empty, null));
            mainNavigationController.pushController(slideController, false);
            slideController.setLeftController(browseController);
        } else {
            mainNavigationController.pushController(browseController, false);
        }
    }

    public void restart() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Handle WatchNotifier clicks
        if (intent.getExtras() != null) {
            int pinId = intent.getExtras().getInt("pin_id", -2);
            if (pinId != -2 && mainNavigationController.getTop() instanceof BrowseController) {
                if (pinId == -1) {
                    drawerController.onMenuClicked();
                } else {
                    Pin pin = watchManager.findPinById(pinId);
                    if (pin != null) {
                        browseController.showThread(pin.loadable, false);
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

        return stackTop().dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Loadable board = browseController.getLoadable();
        if (board == null) {
            Logger.w(TAG, "Can not save instance state, the board loadable is null");
        } else {
            Loadable thread = null;

            if (drawerController.childControllers.get(0) instanceof SplitNavigationController) {
                SplitNavigationController doubleNav = (SplitNavigationController) drawerController.childControllers.get(0);
                if (doubleNav.getRightController() instanceof NavigationController) {
                    NavigationController rightNavigationController = (NavigationController) doubleNav.getRightController();
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

            outState.putParcelable(STATE_KEY, new ChanState(board.copy(), thread.copy()));
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        Controller threadController = null;
        if (drawerController.childControllers.get(0) instanceof DoubleNavigationController) {
            SplitNavigationController splitNavigationController = (SplitNavigationController) drawerController.childControllers.get(0);
            if (splitNavigationController.rightController instanceof NavigationController) {
                NavigationController rightNavigationController = (NavigationController) splitNavigationController.rightController;
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

    public void addController(Controller controller) {
        stack.add(controller);
    }

    public void removeController(Controller controller) {
        stack.remove(controller);
    }

    public ViewGroup getContentView() {
        return contentView;
    }

    public ImagePickDelegate getImagePickDelegate() {
        return imagePickDelegate;
    }

    public VersionHandler getVersionHandler() {
        return versionHandler;
    }

    public RuntimePermissionsHelper getRuntimePermissionsHelper() {
        return runtimePermissionsHelper;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (Controller controller : stack) {
            controller.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onBackPressed() {
        if (!stackTop().onBack()) {
            if (ChanSettings.confirmExit.get()) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.setting_confirm_exit_title)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.exit, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                StartActivity.super.onBackPressed();
                            }
                        })
                        .show();
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        runtimePermissionsHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stackTop().onHide();
        stackTop().onDestroy();
        stack.clear();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Chan.getInstance().activityEnteredForeground();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Chan.getInstance().activityEnteredBackground();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        imagePickDelegate.onActivityResult(requestCode, resultCode, data);
    }

    private Controller stackTop() {
        return stack.get(stack.size() - 1);
    }
}
