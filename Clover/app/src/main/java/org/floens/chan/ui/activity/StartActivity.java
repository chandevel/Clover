package org.floens.chan.ui.activity;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ViewGroup;

import org.floens.chan.ChanApplication;
import org.floens.chan.controller.Controller;
import org.floens.chan.ui.controller.BrowseController;
import org.floens.chan.ui.controller.RootNavigationController;
import org.floens.chan.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

public class StartActivity extends Activity {
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
            // Don't destroy the view, let Android do that or it'll create artifacts
            stackTop().onHide();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stackTop().onDestroy();
        stack.clear();
        System.gc();
        System.gc();
        System.gc();
        System.runFinalization();
    }

    @Override
    protected void onStart() {
        super.onStart();

        ChanApplication.getInstance().activityEnteredForeground();
    }

    @Override
    protected void onStop() {
        super.onStop();

        ChanApplication.getInstance().activityEnteredBackground();
    }

    @Override
    protected void onPause() {
        super.onPause();

        ChanApplication.getWatchManager().updateDatabase();
    }

    private Controller stackTop() {
        return stack.get(stack.size() - 1);
    }
}
