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
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.controller.BrowseController;
import org.floens.chan.ui.controller.RootNavigationController;
import org.floens.chan.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

public class StartActivity extends AppCompatActivity {
    private static final String TAG = "StartActivity";

    private ViewGroup contentView;
    private List<Controller> stack = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.Chan_Theme);
        ThemeHelper.getInstance().reloadPostViewColors(this);

        contentView = (ViewGroup) findViewById(android.R.id.content);

        RootNavigationController rootNavigationController = new RootNavigationController(this);
        rootNavigationController.onCreate();

        setContentView(rootNavigationController.view);
        addController(rootNavigationController);

        rootNavigationController.pushController(new BrowseController(this), false);

        rootNavigationController.onShow();

        // Prevent overdraw
        // Do this after setContentView, or the decor creating will reset the background to a default non-null drawable
        getWindow().setBackgroundDrawable(null);
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
    protected void onPause() {
        super.onPause();

        Chan.getWatchManager().updateDatabase();
    }

    private Controller stackTop() {
        return stack.get(stack.size() - 1);
    }
}
