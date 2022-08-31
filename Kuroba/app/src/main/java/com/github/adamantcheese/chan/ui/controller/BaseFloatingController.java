package com.github.adamantcheese.chan.ui.controller;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getWindow;
import static com.github.adamantcheese.chan.utils.AnimationUtils.animateStatusBar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.github.adamantcheese.chan.controller.Controller;

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

        view = (ViewGroup) LayoutInflater.from(context).inflate(getLayoutId(), null);
        view.setBackgroundColor(0x88000000);

        statusBarColorPrevious = getWindow(context).getStatusBarColor();
        if (statusBarColorPrevious != 0) {
            animateStatusBar(getWindow(context), true, statusBarColorPrevious, TRANSITION_DURATION);
        }
    }

    @Override
    public void stopPresenting() {
        super.stopPresenting();

        if (statusBarColorPrevious != 0) {
            animateStatusBar(getWindow(context), false, statusBarColorPrevious, TRANSITION_DURATION);
        }
    }

    protected abstract int getLayoutId();
}
