package org.floens.chan.ui.controller;

import android.content.Context;
import android.widget.FrameLayout;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;

public class BoardEditController extends Controller {
    public BoardEditController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.board_edit);

        view = new FrameLayout(context);
        view.setBackgroundColor(0xffffffff);
    }
}
