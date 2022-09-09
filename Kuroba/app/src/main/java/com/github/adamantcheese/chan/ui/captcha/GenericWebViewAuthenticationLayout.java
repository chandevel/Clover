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
import android.util.AttributeSet;
import android.webkit.*;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.captcha.CaptchaTokenHolder.CaptchaToken;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.concurrent.TimeUnit;

public class GenericWebViewAuthenticationLayout
        extends WebView
        implements AuthenticationLayoutInterface {
    private static final long RECAPTCHA_TOKEN_LIVE_TIME = TimeUnit.MINUTES.toMillis(2);

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

        authentication = loadable.site.api().postAuthenticate(loadable);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        addJavascriptInterface(new WebInterface(this), "WebInterface");
    }

    @Override
    public void reset() {
        if (isAutoReply && CaptchaTokenHolder.getInstance().hasToken()) {
            callback.onAuthenticationComplete(CaptchaTokenHolder.getInstance().getToken(), true);
            return;
        }

        loadUrl(authentication.baseUrl);
    }

    @Override
    public void hardReset() {
    }

    @Subscribe
    private void onSystemTick(String tick) {
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

            callback.onAuthenticationComplete(token, isAutoReply);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            EventBus.getDefault().register(this);
        } else {
            EventBus.getDefault().unregister(this);
        }
    }

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
