package org.floens.chan.ui.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.ui.layout.ThreadLayout;

public class ViewThreadController extends Controller implements ThreadLayout.ThreadLayoutCallback {
    private ThreadLayout threadLayout;
    private Loadable loadable;

    public ViewThreadController(Context context) {
        super(context);
    }

    public void setLoadable(Loadable loadable) {
        this.loadable = loadable;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        threadLayout = new ThreadLayout(context);
        threadLayout.setCallback(this);
        view = threadLayout;
        view.setBackgroundColor(0xffffffff);

        threadLayout.getPresenter().bindLoadable(loadable);
        threadLayout.getPresenter().requestData();

        navigationItem.title = loadable.title;
    }

    @Override
    public void openThread(Loadable threadLoadable) {
        // TODO implement, scroll to post and fix title
        new AlertDialog.Builder(context)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
//                        threadManagerListener.onOpenThread(thread, link.postId);
                    }
                })
                .setTitle(R.string.open_thread_confirmation)
                .setMessage("/" + threadLoadable.board + "/" + threadLoadable.no)
                .show();
    }
}
