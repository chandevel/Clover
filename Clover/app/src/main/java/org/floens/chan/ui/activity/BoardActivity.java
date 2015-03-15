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

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ViewGroup;

import org.floens.chan.controller.Controller;
import org.floens.chan.ui.controller.BrowseController;
import org.floens.chan.ui.controller.RootNavigationController;
import org.floens.chan.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * StartActivity
 * <p/>
 * (Not actually called StartActivity because then the launcher icon would disappear.
 * Instead it's called like the old launcher activity, BoardActivity.)
 */
public class BoardActivity extends Activity {
    private static final String TAG = "StartActivity";

    private ViewGroup contentView;
    private List<Controller> stack = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
            super.onBackPressed();
        }
    }

    private Controller stackTop() {
        return stack.get(stack.size() - 1);
    }

    /*
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // http://stackoverflow.com/a/7748416/1001608
        // Possible work around for market launches. See http://code.google.com/p/android/issues/detail?id=2373
        // for more details. Essentially, the market launches the main activity on top of other activities.
        // we never want this to happen. Instead, we check if we are the root and if not, we finish.
        if (!isTaskRoot()) {
            final Intent intent = getIntent();
            final String intentAction = intent.getAction();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intentAction != null && intentAction.equals(Intent.ACTION_MAIN)) {
                Logger.w(TAG, "StartActivity is not the root. Finishing StartActivity instead of launching.");
                finish();
                return;
            }
        }

        Intent intent = new Intent(this, ChanActivity.class);
        startActivity(intent);
        finish();
    }*/
}
