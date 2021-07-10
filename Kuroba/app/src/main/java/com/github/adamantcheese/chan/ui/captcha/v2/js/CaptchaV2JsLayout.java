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
package com.github.adamantcheese.chan.ui.captcha.v2.js;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.ui.captcha.CaptchaTokenHolder;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import kotlin.io.TextStreamsKt;

import static android.view.View.MeasureSpec.AT_MOST;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.AUTO;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.SPLIT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;

/**
 * Loads a Captcha2 in a custom webview.
 */
public class CaptchaV2JsLayout
        extends WebView
        implements AuthenticationLayoutInterface {
    private static final long RECAPTCHA_TOKEN_LIVE_TIME = TimeUnit.MINUTES.toMillis(2);

    private AuthenticationLayoutCallback callback;
    private boolean loaded = false;
    private String baseUrl;
    private String siteKey;

    private boolean isAutoReply = true;

    public CaptchaV2JsLayout(Context context) {
        this(context, null);
    }

    public CaptchaV2JsLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CaptchaV2JsLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        getSettings().setUserAgentString(NetUtils.USER_AGENT);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void initialize(Loadable loadable, AuthenticationLayoutCallback callback, boolean autoReply) {
        this.callback = callback;
        this.isAutoReply = autoReply;

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptThirdPartyCookies(this, true);

        SiteAuthentication authentication = loadable.site.actions().postAuthenticate(loadable);

        this.siteKey = authentication.siteKey;
        this.baseUrl = authentication.baseUrl;

        requestDisallowInterceptTouchEvent(true);
        hideKeyboard(this);
        getSettings().setJavaScriptEnabled(true);

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
                Logger.i(
                        CaptchaV2JsLayout.this,
                        consoleMessage.lineNumber() + ":" + consoleMessage.message() + " " + consoleMessage.sourceId()
                );
                return true;
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (Uri.parse(url).getHost().equals(Uri.parse(CaptchaV2JsLayout.this.baseUrl).getHost())) {
                    return false;
                } else {
                    openLink(url);
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
            if (CaptchaTokenHolder.getInstance().hasToken() && isAutoReply) {
                callback.onAuthenticationComplete(this, CaptchaTokenHolder.getInstance().getToken(), true);
                return;
            }

            hardReset();
        }
    }

    @Override
    public void hardReset() {
        int[] attr = {R.attr.isLightTheme};
        boolean isLightTheme = getContext().getTheme().obtainStyledAttributes(attr).getBoolean(0, true);
        String html = "";
        try (InputStream htmlStream = getContext().getResources().getAssets().open("html/captcha2.html")) {
            html = TextStreamsKt.readText(new InputStreamReader(htmlStream));
        } catch (Exception ignored) {}
        html = html.replace("__site_key__", siteKey);
        html = html.replace("__theme__", isLightTheme ? "light" : "dark");

        Point displaySize = getDisplaySize();
        boolean isSplitMode =
                ChanSettings.layoutMode.get() == SPLIT || (ChanSettings.layoutMode.get() == AUTO && isTablet());

        measure(
                //0.35 is from SplitNavigationControllerLayout for the smaller side; measure for the larger of the two sides to find left/right
                MeasureSpec.makeMeasureSpec(isSplitMode ? (int) (displaySize.x * 0.65) : displaySize.x, AT_MOST),
                MeasureSpec.makeMeasureSpec(displaySize.y, AT_MOST)
        );
        //for a 2560 wide screen, partitions in split layout are 896(equal) / 2(divider) / 1662 (devicewidth*0.65 - 2(divider))
        //for some reason, the measurement of THIS view's width is larger than the parent view's width; makes no sense
        //but once onDraw is called, the parent has the correct width, so we use that
        int containerWidth = ((View) getParent()).getMeasuredWidth();

        //if split, smaller side has captcha on the left, larger right; otherwise always on the left
        html = html.replace(
                "__positioning_horizontal__",
                //equal is left, greater is right
                isSplitMode ? (containerWidth == displaySize.x * 0.35 ? "left" : "right") : "left"
        );
        html = html.replace(
                "__positioning_vertical__",
                //split mode should always be on the bottom
                isSplitMode ? "bottom" : (ChanSettings.captchaOnBottom.get() ? "bottom" : "top")
        );

        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    private void onCaptchaEntered(String response) {
        if (TextUtils.isEmpty(response)) {
            reset();
        } else {
            CaptchaTokenHolder.getInstance().addNewToken(null, response, RECAPTCHA_TOKEN_LIVE_TIME);

            CaptchaTokenHolder.CaptchaToken token;

            if (isAutoReply) {
                token = CaptchaTokenHolder.getInstance().getToken();
            } else {
                token = new CaptchaTokenHolder.CaptchaToken(null, response, 0);
            }

            callback.onAuthenticationComplete(this, token, isAutoReply);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!loaded) {
            loaded = true;
            hardReset();
        }
        super.onDraw(canvas);
    }

    public static class CaptchaInterface {
        private final CaptchaV2JsLayout layout;

        public CaptchaInterface(CaptchaV2JsLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaEntered(final String response) {
            BackgroundUtils.runOnMainThread(() -> layout.onCaptchaEntered(response));
        }
    }
}
