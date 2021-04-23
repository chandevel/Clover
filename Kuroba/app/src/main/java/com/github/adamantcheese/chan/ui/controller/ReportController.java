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
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.helper.PostHelper;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ReportController
        extends WebViewController {

    public ReportController(Context context, Post post, Loadable loadable) {
        super(
                context,
                getString(R.string.report_screen, PostHelper.getTitle(post, loadable)),
                post.board.site.endpoints().report(post).toString()
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ((WebView) view).getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        ((WebView) view).setWebChromeClient(new WebChromeClient() {
            @Override
            public void onCloseWindow(WebView window) {
                super.onCloseWindow(window);
                // some window.close events are routed into here, pop the controller if so
                navigationController.popController(true);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                // cheap hack to capture a JS window close error message
                // when that error occurs for this controller/webview, pop the controllers
                if (consoleMessage.message().contains("close")) {
                    navigationController.popController(true);
                    return true;
                } else {
                    return super.onConsoleMessage(consoleMessage);
                }
            }
        });
    }
}
