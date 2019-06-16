package com.github.adamantcheese.chan.ui.helper;

import android.content.Context;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.ui.controller.LoadingViewController;

public class ThreadPrefetchHelper {
    private Context context;
    private ThreadPrefetchHelperCallback callback;

    @Nullable
    private LoadingViewController loadingViewController;

    public ThreadPrefetchHelper(Context context, ThreadPrefetchHelperCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void present() {
        if (loadingViewController == null) {
            loadingViewController = new LoadingViewController(context, false);
            callback.presentLoadingViewController(loadingViewController);
        }
    }

    public void updateProgress(int percent) {
        if (loadingViewController != null) {
            loadingViewController.updateProgress(percent);
        }
    }

    public void dismiss() {
        if (loadingViewController != null) {
            loadingViewController.stopPresenting();
            loadingViewController = null;
        }
    }

    public interface ThreadPrefetchHelperCallback {
        void presentLoadingViewController(Controller controller);
    }
}
