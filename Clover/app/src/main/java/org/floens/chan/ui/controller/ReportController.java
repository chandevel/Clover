/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebView;

import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.PostHelper;

public class ReportController extends Controller {
    private Post post;

    public ReportController(Context context, Post post) {
        super(context);
        this.post = post;
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate() {
        super.onCreate();
        navigationItem.title = context.getString(R.string.report_screen, PostHelper.getTitle(post, null));

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
        if (ChanSettings.passLoggedIn()) {
            for (String cookie : ChanUrls.getReportCookies(ChanSettings.passId.get())) {
                cookieManager.setCookie(ChanUrls.getReportDomain(), cookie);
            }
        }

        WebView webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.loadUrl(ChanUrls.getReportUrl(post.board, post.no));
        view = webView;
    }
}
