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
import android.os.Handler;
import android.util.AttributeSet;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.captcha.CaptchaTokenHolder.CaptchaToken;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.concurrent.TimeUnit;

public class GenericWebViewAuthenticationLayout
        extends WebView
        implements AuthenticationLayoutInterface {
    public static final int CHECK_INTERVAL = 500;
    private static final long RECAPTCHA_TOKEN_LIVE_TIME = TimeUnit.MINUTES.toMillis(2);

    private final Handler handler = new Handler();
    private boolean attachedToWindow = false;

    private AuthenticationLayoutCallback callback;
    private SiteAuthentication authentication;
    private boolean resettingFromFoundText = false;
    private final boolean isAutoReply = true;

    public GenericWebViewAuthenticationLayout(Context context) {
        this(context, null);
    }

    public GenericWebViewAuthenticationLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GenericWebViewAuthenticationLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        getSettings().setUserAgentString(NetUtils.USER_AGENT);
        setFocusableInTouchMode(true);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void initialize(Loadable loadable, AuthenticationLayoutCallback callback, boolean ignored) {
        this.callback = callback;

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptThirdPartyCookies(this, true);

        authentication = loadable.site.actions().postAuthenticate(loadable);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        addJavascriptInterface(new WebInterface(this), "WebInterface");
    }

    @Override
    public void reset() {
        if (isAutoReply && CaptchaTokenHolder.getInstance().hasToken()) {
            callback.onAuthenticationComplete(this, CaptchaTokenHolder.getInstance().getToken(), true);
            return;
        }

        loadUrl(authentication.baseUrl);
    }

    @Override
    public void hardReset() {
    }

    private void checkText() {
        loadUrl("javascript:WebInterface.onAllText(document.documentElement.textContent)");
    }

    private void onAllText(String text) {
        boolean retry = text.contains(authentication.retryText);
        boolean success = text.contains(authentication.successText);

        if (retry) {
            if (!resettingFromFoundText) {
                resettingFromFoundText = true;
                postDelayed(() -> {
                    resettingFromFoundText = false;
                    reset();
                }, 1000);
            }
        } else if (success) {
            CaptchaTokenHolder.getInstance().addNewToken("", text, RECAPTCHA_TOKEN_LIVE_TIME);

            CaptchaToken token;

            if (isAutoReply && CaptchaTokenHolder.getInstance().hasToken()) {
                token = CaptchaTokenHolder.getInstance().getToken();
            } else {
                token = new CaptchaToken("", text, 0);
            }

            callback.onAuthenticationComplete(this, token, isAutoReply);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        attachedToWindow = true;
        handler.postDelayed(checkTextRunnable, CHECK_INTERVAL);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        attachedToWindow = false;
        handler.removeCallbacks(checkTextRunnable);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        handler.removeCallbacks(checkTextRunnable);

        if (hasWindowFocus) {
            handler.postDelayed(checkTextRunnable, CHECK_INTERVAL);
        }
    }

    private final Runnable checkTextRunnable = new Runnable() {
        @Override
        public void run() {
            checkText();
            reschedule();
        }

        private void reschedule() {
            handler.removeCallbacks(checkTextRunnable);
            if (attachedToWindow && hasWindowFocus()) {
                handler.postDelayed(checkTextRunnable, CHECK_INTERVAL);
            }
        }
    };

    public static class WebInterface {
        private final GenericWebViewAuthenticationLayout layout;

        public WebInterface(GenericWebViewAuthenticationLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onAllText(String text) {
            BackgroundUtils.runOnMainThread(() -> layout.onAllText(text));
        }
    }
}
