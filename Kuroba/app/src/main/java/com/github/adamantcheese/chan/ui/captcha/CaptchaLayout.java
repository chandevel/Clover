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
import android.graphics.Canvas;
import android.graphics.Point;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static android.view.View.MeasureSpec.AT_MOST;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.AUTO;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.SPLIT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static com.github.adamantcheese.chan.utils.BackgroundUtils.runOnUiThread;

public class CaptchaLayout
        extends WebView
        implements AuthenticationLayoutInterface {
    private static final String TAG = "CaptchaLayout";
    private static final long RECAPTCHA_TOKEN_LIVE_TIME = TimeUnit.MINUTES.toMillis(2);

    private AuthenticationLayoutCallback callback;
    private boolean loaded = false;
    private String baseUrl;
    private String siteKey;

    private boolean isAutoReply = true;

    @Inject
    CaptchaHolder captchaHolder;

    public CaptchaLayout(Context context) {
        super(context);
        init();
    }

    public CaptchaLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CaptchaLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        inject(this);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback, boolean autoReply) {
        this.callback = callback;
        this.isAutoReply = autoReply;

        SiteAuthentication authentication = site.actions().postAuthenticate();

        this.siteKey = authentication.siteKey;
        this.baseUrl = authentication.baseUrl;

        requestDisallowInterceptTouchEvent(true);

        hideKeyboard(this);

        getSettings().setJavaScriptEnabled(true);

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
                Logger.i(
                        TAG,
                        consoleMessage.lineNumber() + ":" + consoleMessage.message() + " " + consoleMessage.sourceId()
                );
                return true;
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (Uri.parse(url).getHost().equals(Uri.parse(CaptchaLayout.this.baseUrl).getHost())) {
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
            if (captchaHolder.hasToken() && isAutoReply) {
                callback.onAuthenticationComplete(this, null, captchaHolder.getToken(), true);
                return;
            }

            hardReset();
        }
    }

    @Override
    public void hardReset() {
        String html = IOUtils.assetAsString(getContext(), "captcha/captcha2.html");
        html = html.replace("__site_key__", siteKey);
        html = html.replace("__theme__", ThemeHelper.getTheme().isLightTheme ? "light" : "dark");

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
                (isSplitMode ? (containerWidth == displaySize.x * 0.35 ? "left: 0" : "right: 0") : "left: 0")
        );

        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    private void onCaptchaEntered(String challenge, String response) {
        if (TextUtils.isEmpty(response)) {
            reset();
        } else {
            captchaHolder.addNewToken(response, RECAPTCHA_TOKEN_LIVE_TIME);

            String token;

            if (isAutoReply) {
                token = captchaHolder.getToken();
            } else {
                token = response;
            }

            callback.onAuthenticationComplete(this, challenge, token, isAutoReply);
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
        private final CaptchaLayout layout;

        public CaptchaInterface(CaptchaLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaEntered(final String response) {
            runOnUiThread(() -> layout.onCaptchaEntered(null, response));
        }

        @JavascriptInterface
        public void onCaptchaEnteredv1(final String challenge, final String response) {
            runOnUiThread(() -> layout.onCaptchaEntered(challenge, response));
        }
    }
}
