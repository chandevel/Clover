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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteRequestModifier;
import com.github.adamantcheese.chan.ui.helper.PostHelper;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

public class ReportController
        extends Controller {
    private Post post;

    public ReportController(Context context, Post post) {
        super(context);
        this.post = post;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate() {
        super.onCreate();
        navigation.title = getString(R.string.report_screen, PostHelper.getTitle(post, null));

        Site site = post.board.site;
        HttpUrl url = site.endpoints().report(post);

        try {
            WebView webView = new WebView(context);

            SiteRequestModifier siteRequestModifier = site.requestModifier();
            if (siteRequestModifier != null) {
                siteRequestModifier.modifyWebView(webView);
            }

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            webView.loadUrl(url.toString());
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
            view = inflate(context, R.layout.layout_webview_error);
            ((TextView) view.findViewById(R.id.text)).setText(errmsg);
        }
    }
}
