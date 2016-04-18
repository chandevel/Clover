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
import android.util.AttributeSet;
import android.view.KeyEvent;
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
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.ui.view.FixedRatioThumbnailView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;

import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;

public class LegacyCaptchaLayout extends LinearLayout implements CaptchaLayoutInterface, View.OnClickListener {
    private FixedRatioThumbnailView image;
    private EditText input;
    private ImageView submit;

    private WebView internalWebView;

    private String baseUrl;
    private String siteKey;
    private CaptchaCallback callback;

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

        image = (FixedRatioThumbnailView) findViewById(R.id.image);
        image.setRatio(300f / 57f);
        image.setOnClickListener(this);
        input = (EditText) findViewById(R.id.input);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    AndroidUtils.hideKeyboard(input);
                    submitCaptcha();
                    return true;
                }
                return false;
            }
        });
        submit = (ImageView) findViewById(R.id.submit);
        theme().sendDrawable.apply(submit);
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
    public void initCaptcha(String baseUrl, String siteKey, boolean lightTheme, CaptchaCallback callback) {
        this.baseUrl = baseUrl;
        this.siteKey = siteKey;
        this.callback = callback;
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
        image.setUrl(null, 0, 0);
        input.requestFocus();
    }

    private void submitCaptcha() {
        AndroidUtils.hideKeyboard(this);
        callback.captchaEntered(this, challenge, input.getText().toString());
    }

    private void onCaptchaLoaded(final String imageUrl, final String challenge) {
        this.challenge = challenge;
        image.setUrl(imageUrl, 300, 57);
    }

    public static class CaptchaInterface {
        private final LegacyCaptchaLayout layout;

        public CaptchaInterface(LegacyCaptchaLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaLoaded(final String imageUrl, final String challenge) {
            AndroidUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    layout.onCaptchaLoaded(imageUrl, challenge);
                }
            });
        }
    }
}
