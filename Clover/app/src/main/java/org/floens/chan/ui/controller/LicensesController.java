package org.floens.chan.ui.controller;

import android.content.Context;
import android.webkit.WebView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;

public class LicensesController extends Controller {
    public LicensesController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.setting_screen_licenses);

        WebView webView = new WebView(context);
        webView.loadUrl("file:///android_asset/html/licenses.html");
        webView.setBackgroundColor(0xffffffff);
        view = webView;
    }
}
