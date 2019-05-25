package org.floens.chan.ui.controller.export;

import android.content.Context;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.ui.controller.BaseFloatingController;

public class LoadingViewController extends BaseFloatingController {

    public LoadingViewController(Context context) {
        super(context);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.controller_loading_view;
    }

    @Override
    public boolean onBack() {
        Toast.makeText(context, R.string.loading_view_controller_cannot_be_canceled, Toast.LENGTH_SHORT).show();
        return true;
    }
}
