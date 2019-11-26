package com.github.adamantcheese.chan.ui.controller;

import android.app.Activity;
import android.content.Context;
import android.view.Window;

import com.github.adamantcheese.chan.controller.Controller;

import static com.github.adamantcheese.chan.utils.AnimationUtils.animateStatusBar;

public abstract class BaseFloatingController
        extends Controller {
    private static final int TRANSITION_DURATION = 200;
    private int statusBarColorPrevious;

    public BaseFloatingController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(getLayoutId());

        statusBarColorPrevious = getWindow().getStatusBarColor();
        if (statusBarColorPrevious != 0) {
            animateStatusBar(getWindow(), true, statusBarColorPrevious, TRANSITION_DURATION);
        }
    }

    @Override
    public void stopPresenting() {
        super.stopPresenting();

        if (statusBarColorPrevious != 0) {
            animateStatusBar(getWindow(), false, statusBarColorPrevious, TRANSITION_DURATION);
        }
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    protected abstract int getLayoutId();
}
