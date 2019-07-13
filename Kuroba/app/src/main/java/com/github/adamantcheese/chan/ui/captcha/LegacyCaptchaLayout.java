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
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.FixedRatioThumbnailView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.IOUtils;

import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;

public class LegacyCaptchaLayout extends LinearLayout implements AuthenticationLayoutInterface, View.OnClickListener {
    private FixedRatioThumbnailView image;
    private EditText input;
    private ImageView submit;

    private WebView internalWebView;

    private String baseUrl;
    private String siteKey;
    private AuthenticationLayoutCallback callback;

    private String challenge;

    public LegacyCaptchaLayout(Context context) {
        super(context);
    }

    public LegacyCaptchaLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LegacyCaptchaLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        image = findViewById(R.id.image);
        image.setRatio(300f / 57f);
        image.setOnClickListener(this);
        input = findViewById(R.id.input);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                AndroidUtils.hideKeyboard(input);
                submitCaptcha();
                return true;
            }
            return false;
        });
        submit = findViewById(R.id.submit);
        ThemeHelper.getTheme().sendDrawable.apply(submit);
        setRoundItemBackground(submit);
        submit.setOnClickListener(this);

        // This captcha layout uses a webview in the background
        // Because the script changed significantly we can't just load the image straight up from the challenge data anymore.
        // Now we load a skeleton page in the background, and wait until both the image and challenge key are loaded,
        // then the onCaptchaLoaded is called through the javascript interface.

        internalWebView = new WebView(getContext());
        internalWebView.setWebChromeClient(new WebChromeClient());
        internalWebView.setWebViewClient(new WebViewClient());

        WebSettings settings = internalWebView.getSettings();
        settings.setJavaScriptEnabled(true);

        internalWebView.addJavascriptInterface(new CaptchaInterface(this), "CaptchaCallback");
    }

    @Override
    public void onClick(View v) {
        if (v == submit) {
            submitCaptcha();
        } else if (v == image) {
            reset();
        }
    }

    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback) {
        this.callback = callback;

        SiteAuthentication authentication = site.actions().postAuthenticate();

        this.siteKey = authentication.siteKey;
        this.baseUrl = authentication.baseUrl;
    }

    @Override
    public void hardReset() {
        reset();
    }

    @Override
    public void reset() {
        input.setText("");
        String html = IOUtils.assetAsString(getContext(), "captcha/captcha_legacy.html");
        html = html.replace("__site_key__", siteKey);
        internalWebView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
        image.setUrl(null);
        input.requestFocus();
    }

    private void submitCaptcha() {
        AndroidUtils.hideKeyboard(this);
        callback.onAuthenticationComplete(this, challenge, input.getText().toString());
    }

    private void onCaptchaLoaded(final String imageUrl, final String challenge) {
        this.challenge = challenge;
        image.setUrl(imageUrl);
    }

    public static class CaptchaInterface {
        private final LegacyCaptchaLayout layout;

        public CaptchaInterface(LegacyCaptchaLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaLoaded(final String imageUrl, final String challenge) {
            AndroidUtils.runOnUiThread(() -> layout.onCaptchaLoaded(imageUrl, challenge));
        }
    }
}
