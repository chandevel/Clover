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
package com.github.adamantcheese.chan.ui.captcha;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;

public class CaptchaLayout extends WebView implements AuthenticationLayoutInterface {
    private static final String TAG = "CaptchaLayout";

    private AuthenticationLayoutCallback callback;
    private boolean loaded = false;
    private String baseUrl;
    private String siteKey;

    public CaptchaLayout(Context context) {
        super(context);
    }

    public CaptchaLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CaptchaLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback) {
        this.callback = callback;

        SiteAuthentication authentication = site.actions().postAuthenticate();

        this.siteKey = authentication.siteKey;
        this.baseUrl = authentication.baseUrl;

        requestDisallowInterceptTouchEvent(true);

        AndroidUtils.hideKeyboard(this);

        getSettings().setJavaScriptEnabled(true);

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
                Logger.i(TAG, consoleMessage.lineNumber() + ":" + consoleMessage.message()
                        + " " + consoleMessage.sourceId());
                return true;
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (Uri.parse(url).getHost().equals(Uri.parse(CaptchaLayout.this.baseUrl).getHost())) {
                    return false;
                } else {
                    AndroidUtils.openLink(url);
                    return true;
                }
            }
        });
        setBackgroundColor(0x00000000);

        addJavascriptInterface(new CaptchaInterface(this), "CaptchaCallback");
    }

    public void reset() {
        if (loaded) {
            loadUrl("javascript:grecaptcha.reset()");
        } else {
            hardReset();
        }
    }

    @Override
    public void hardReset() {
        loaded = true;

        String html = IOUtils.assetAsString(getContext(), "captcha/captcha2.html");
        html = html.replace("__site_key__", siteKey);
        html = html.replace("__theme__", ThemeHelper.getTheme().isLightTheme ? "light" : "dark");

        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    private void onCaptchaEntered(String challenge, String response) {
        if (TextUtils.isEmpty(response)) {
            reset();
        } else {
            callback.onAuthenticationComplete(this, challenge, response);
        }
    }

    public static class CaptchaInterface {
        private final CaptchaLayout layout;

        public CaptchaInterface(CaptchaLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaEntered(final String response) {
            AndroidUtils.runOnUiThread(() -> layout.onCaptchaEntered(null, response));
        }

        @JavascriptInterface
        public void onCaptchaEnteredv1(final String challenge, final String response) {
            AndroidUtils.runOnUiThread(() -> layout.onCaptchaEntered(challenge, response));
        }
    }
}
