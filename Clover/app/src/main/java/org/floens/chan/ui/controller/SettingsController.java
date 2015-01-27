package org.floens.chan.ui.controller;

import android.content.Context;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;

public class SettingsController extends Controller {
    public SettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = context.getString(R.string.action_settings);

        view = inflateRes(R.layout.settings_layout);
    }
}
