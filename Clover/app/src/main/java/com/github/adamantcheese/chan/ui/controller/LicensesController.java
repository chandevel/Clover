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

import android.content.Context;
import android.webkit.WebView;

import com.github.adamantcheese.chan.controller.Controller;

public class LicensesController extends Controller {
    private String title;
    private String url;

    public LicensesController(Context context, String title, String url) {
        super(context);
        this.title = title;
        this.url = url;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.title = title;

        WebView webView = new WebView(context);
        webView.loadUrl(url);
        webView.setBackgroundColor(0xffffffff);
        view = webView;
    }
}
