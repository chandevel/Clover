package com.github.adamantcheese.chan.ui.controller.export;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.controller.BaseFloatingController;

public class LoadingViewController extends BaseFloatingController {

    public LoadingViewController(Context context) {
        super(context);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.controller_loading_view;
    }

}
