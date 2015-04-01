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
package org.floens.chan.ui.layout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;

public class CaptchaLayout extends WebView {
    private CaptchaCallback callback;
    private boolean loaded = false;
    private String baseUrl;
    private String siteKey;
    private boolean lightTheme;

    public CaptchaLayout(Context context) {
        super(context);
    }

    public CaptchaLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CaptchaLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void initCaptcha(String baseUrl, String siteKey, boolean lightTheme, String userAgent, CaptchaCallback callback) {
        this.callback = callback;
        this.baseUrl = baseUrl;
        this.siteKey = siteKey;
        this.lightTheme = lightTheme;

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUserAgentString(userAgent);

        addJavascriptInterface(new CaptchaInterface(this), "CaptchaCallback");
    }

    public void load() {
        if (!loaded) {
            loaded = true;

            String html = IOUtils.assetAsString(getContext(), "captcha/captcha.html");
            html = html.replace("__site_key__", siteKey);
            html = html.replace("__theme__", lightTheme ? "light" : "dark");

            loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
        }
    }

    public void reset() {
        if (loaded) {
            loadUrl("javascript:grecaptcha.reset()");
        } else {
            load();
        }
    }

    private void onCaptchaLoaded() {
        callback.captchaLoaded(this);
    }

    private void onCaptchaEntered(String response) {
        if (TextUtils.isEmpty(response)) {
            reset();
        } else {
            callback.captchaEntered(this, response);
        }
    }

    public interface CaptchaCallback {
        public void captchaLoaded(CaptchaLayout captchaLayout);

        public void captchaEntered(CaptchaLayout captchaLayout, String response);
    }

    public static class CaptchaInterface {
        private final CaptchaLayout layout;

        public CaptchaInterface(CaptchaLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaLoaded() {
            AndroidUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    layout.onCaptchaLoaded();
                }
            });
        }

        @JavascriptInterface
        public void onCaptchaEntered(final String response) {
            AndroidUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    layout.onCaptchaEntered(response);
                }
            });
        }
    }
}
