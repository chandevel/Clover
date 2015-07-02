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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanHelper;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.controller.BrowseController;
import org.floens.chan.ui.controller.RootNavigationController;
import org.floens.chan.ui.controller.ViewThreadController;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.state.ChanState;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class StartActivity extends AppCompatActivity {
    private static final String TAG = "StartActivity";

    private static final String STATE_KEY = "chan_state";

    private ViewGroup contentView;
    private List<Controller> stack = new ArrayList<>();

    private final BoardManager boardManager;
    private RootNavigationController rootNavigationController;
    private BrowseController browseController;

    private ImagePickDelegate imagePickDelegate;

    public StartActivity() {
        boardManager = Chan.getBoardManager();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeHelper.getInstance().setupContext(this);

        imagePickDelegate = new ImagePickDelegate(this);

        contentView = (ViewGroup) findViewById(android.R.id.content);

        rootNavigationController = new RootNavigationController(this);
        rootNavigationController.onCreate();

        setContentView(rootNavigationController.view);
        addController(rootNavigationController);

        browseController = new BrowseController(this);
        rootNavigationController.pushController(browseController, false);

        rootNavigationController.onShow();

        // Prevent overdraw
        // Do this after setContentView, or the decor creating will reset the background to a default non-null drawable
        getWindow().setBackgroundDrawable(null);

        // Startup from background or url
        boolean loadDefault = true;
        if (savedInstanceState != null) {
            ChanState chanState = savedInstanceState.getParcelable(STATE_KEY);
            if (chanState == null) {
                Logger.w(TAG, "savedInstanceState was not null, but no ChanState was found!");
            } else {
                loadDefault = false;
                Board board = boardManager.getBoardByValue(chanState.board.board);
                browseController.loadBoard(board);

                if (chanState.thread.mode == Loadable.Mode.THREAD) {
                    browseController.showThread(chanState.thread, false);
                }
            }
        } else if (getIntent().getData() != null) {
            Loadable fromUri = ChanHelper.getLoadableFromStartUri(getIntent().getData());
            if (fromUri != null) {
                loadDefault = false;
                Board board = boardManager.getBoardByValue(fromUri.board);
                browseController.loadBoard(board);

                if (fromUri.isThreadMode()) {
                    browseController.showThread(fromUri, false);
                }
            }
        }

        if (loadDefault) {
            browseController.loadBoard(boardManager.getSavedBoards().get(0));
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
            if (pinId != -2 && rootNavigationController.getTop() instanceof BrowseController) {
                if (pinId == -1) {
                    rootNavigationController.onMenuClicked();
                } else {
                    Pin pin = Chan.getWatchManager().findPinById(pinId);
                    if (pin != null) {
                        browseController.showThread(pin.loadable, false);
                    }
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Loadable board = browseController.getLoadable();
        if (board == null) {
            Logger.w(TAG, "Can not save instance state, the board loadable is null");
        } else {
            Loadable thread = null;
            List<Controller> controllers = rootNavigationController.getControllerList();
            for (Controller controller : controllers) {
                if (controller instanceof ViewThreadController) {
                    thread = ((ViewThreadController) controller).getLoadable();
                    break;
                }
            }

            if (thread == null) {
                // Make the parcel happy
                thread = new Loadable();
            }

            outState.putParcelable(STATE_KEY, new ChanState(board, thread));
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
                                stackTop().onHide();
                                StartActivity.super.onBackPressed();
                            }
                        })
                        .show();
            } else {
                // Don't destroy the view, let Android do that or it'll create artifacts
                stackTop().onHide();
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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
