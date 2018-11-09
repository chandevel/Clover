package org.floens.chan.ui.captcha;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.Response;

import org.codejargon.feather.Feather;
import org.floens.chan.R;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.sites.dvach.Dvach2ChaptchaRequest;
import org.floens.chan.ui.view.FixedRatioThumbnailView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import static android.support.constraint.Constraints.TAG;
import static org.floens.chan.Chan.injector;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.getAppContext;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;

public class DvachaptchaLayout extends LinearLayout implements AuthenticationLayoutInterface, View.OnClickListener {

    private FixedRatioThumbnailView image;
    private EditText input;
    private ImageView submit;

    private WebView internalWebView;

    private String baseUrl;
    private String siteKey;
    private AuthenticationLayoutCallback callback;

    private String challenge;

    public DvachaptchaLayout(Context context) {
        super(context);
    }


    public DvachaptchaLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DvachaptchaLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        image = findViewById(R.id.image);
        image.setRatio(300f / 57f);
        image.setOnClickListener(this);
        input = findViewById(R.id.input);
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
        submit = findViewById(R.id.submit);
        theme().sendDrawable.apply(submit);
        setRoundItemBackground(submit);
        submit.setOnClickListener(this);

        input.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                input.post(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager imm = (InputMethodManager) getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            }
        });
        input.requestFocus();
    }

    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback) {
        this.callback = callback;

        SiteAuthentication authentication = site.actions().postAuthenticate();

        this.siteKey = authentication.siteKey;
        this.baseUrl = authentication.baseUrl;
    }

    @Override
    public void onClick(View v) {
        if (v == submit) {
            submitCaptcha();
        } else if (v == image) {
            reset();
        }
    }

    private void submitCaptcha() {
        AndroidUtils.hideKeyboard(this);
        callback.onAuthenticationComplete(this, challenge, input.getText().toString());
    }

    @Override
    public void hardReset() {
        reset();
    }

    @Override
    public void reset() {
        Feather injector = injector();
        RequestQueue rq = injector.instance(RequestQueue.class);
        input.setText("");
        image.setUrl(null, 0, 0);
        challenge = "";

        Dvach2ChaptchaRequest capreq = new Dvach2ChaptchaRequest(new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Logger.e(TAG, "OK", response);
                challenge = response;
                image.setUrl("https://2ch.hk/api/captcha/2chaptcha/image/" + response, 0, 0);
            }
        }, (error) -> {
            Logger.e(TAG, "Failed to get 2chaptcha from server", error);
        });
        rq.add(capreq);
        rq.start();

        input.requestFocus();
    }

}
