/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AndroidRuntimeException;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.net.NetUtils;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class WebViewController
        extends Controller {
    private final String navTitle;
    private final String initialUrl;

    public WebViewController(Context context, String title, String url) {
        super(context);
        navTitle = title;
        initialUrl = url;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate() {
        super.onCreate();
        navigation.title = navTitle;

        try {
            WebView webView = new WebView(context);
            webView.setWebViewClient(new WebViewClient());
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUserAgentString(NetUtils.USER_AGENT);
            webView.loadUrl(initialUrl);
            view = webView;
        } catch (Throwable error) {
            String errmsg = "";
            if (error instanceof AndroidRuntimeException && error.getMessage() != null) {
                if (error.getMessage().contains("MissingWebViewPackageException")) {
                    errmsg = getString(R.string.fail_reason_webview_is_not_installed);
                }
            } else {
                errmsg = getString(R.string.fail_reason_some_part_of_webview_not_initialized, error.getMessage());
            }
            view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.layout_webview_error, null);
            ((TextView) view.findViewById(R.id.text)).setText(errmsg);
        }
    }
}
