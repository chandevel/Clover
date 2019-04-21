package org.floens.chan.ui.controller.export;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.Window;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.utils.AndroidUtils;

public class LoadingViewController extends Controller {
    private static final int TRANSITION_DURATION = 200;

    private int statusBarColorPrevious;

    public LoadingViewController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_loading_view);

        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = getWindow().getStatusBarColor();
            if (statusBarColorPrevious != 0) {
                AndroidUtils.animateStatusBar(getWindow(), true, statusBarColorPrevious, TRANSITION_DURATION);
            }
        }
    }

    @Override
    public void stopPresenting() {
        super.stopPresenting();

        if (Build.VERSION.SDK_INT >= 21) {
            if (statusBarColorPrevious != 0) {
                AndroidUtils.animateStatusBar(getWindow(), true, statusBarColorPrevious, TRANSITION_DURATION);
            }
        }
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

}
