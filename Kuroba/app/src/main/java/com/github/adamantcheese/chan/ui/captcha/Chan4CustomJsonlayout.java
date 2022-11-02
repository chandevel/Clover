package com.github.adamantcheese.chan.ui.captcha;

import static com.github.adamantcheese.chan.core.net.NetUtilsClasses.JSON_CONVERTER;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getThemeAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.util.*;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.MainThreadResponseResult;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.*;
import com.google.android.material.textfield.TextInputEditText;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;

public class Chan4CustomJsonlayout
        extends LinearLayout
        implements AuthenticationLayoutInterface, ResponseResult<Chan4CustomJsonlayout.ParsedJsonStruct> {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isAutoReply;

    private AuthenticationLayoutCallback callback;
    private SiteAuthentication authentication;
    private Call captchaCall;
    private ParsedJsonStruct currentStruct;
    private boolean internalRefresh;
    private boolean webviewReady;

    private TextView topText;
    private ImageView colorMatch;
    private ImageView bg;
    private ImageView fg;
    private SeekBar slider;
    private EditText input;
    private Button autoSolve;
    private ImageView verify;
    private WebView captchaAutosolve;

    public Chan4CustomJsonlayout(Context context) {
        super(context);
    }

    public Chan4CustomJsonlayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public Chan4CustomJsonlayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        topText = findViewById(R.id.top_text);
        topText.setText("Initializing some cookies, please wait...");
        colorMatch = findViewById(R.id.color_match);
        bg = findViewById(R.id.bg);
        fg = findViewById(R.id.fg);
        slider = findViewById(R.id.slider);
        input = findViewById(R.id.captcha_input);
        InputFilter[] editFilters = input.getFilters();
        InputFilter[] newFilters = new InputFilter[editFilters.length + 1];
        System.arraycopy(editFilters, 0, newFilters, 0, editFilters.length);
        newFilters[editFilters.length] = new InputFilter.AllCaps();
        input.setFilters(newFilters);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                    && event.getAction() == KeyEvent.ACTION_DOWN || actionId == EditorInfo.IME_ACTION_DONE) {
                verify.callOnClick();
                return true;
            }
            return false;
        });
        autoSolve = findViewById(R.id.autosolve);
        autoSolve.setText("Solve");
        autoSolve.setOnClickListener(v -> tryAutoSolve());
        verify = findViewById(R.id.verify);
        verify.setOnClickListener(v -> {
            handler.removeCallbacks(RESET_RUNNABLE);
            hideKeyboard(input);

            CaptchaTokenHolder
                    .getInstance()
                    .addNewToken(currentStruct.challenge,
                            input.getText().toString(),
                            TimeUnit.SECONDS.toMillis(currentStruct.ttl)
                    );

            CaptchaTokenHolder.CaptchaToken token;

            if (isAutoReply && CaptchaTokenHolder.getInstance().hasToken()) {
                token = CaptchaTokenHolder.getInstance().getToken();
            } else {
                token = new CaptchaTokenHolder.CaptchaToken("", input.getText().toString(), 0);
            }

            callback.onAuthenticationComplete(token, isAutoReply);
        });

        captchaAutosolve = findViewById(R.id.captcha_autosolve);
        captchaAutosolve.getSettings().setJavaScriptEnabled(true);
        captchaAutosolve.getSettings().setDomStorageEnabled(true);
        captchaAutosolve.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        captchaAutosolve.addJavascriptInterface(new CaptchaAutoSolveCompleteInterface(Chan4CustomJsonlayout.this),
                "CaptchaAutocomplete"
        );
        // for debugging, render it on-screen, otherwise render it off-screen
        captchaAutosolve.setTranslationY(BuildConfig.DEBUG ? 0 : 1000000);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain("application.internal")
                .addPathHandler("/js/", new WebViewAssetLoader.AssetsPathHandler(getContext()))
                .build();
        captchaAutosolve.setWebViewClient(new WebViewClientCompat() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webviewReady = true;
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequestCompat(
                    @NonNull WebView view, @NonNull String url
            ) {
                return loader.shouldInterceptRequest(Uri.parse(url));
            }
        });
        captchaAutosolve.loadUrl("file:///android_asset/html/captcha_autosolve.html");
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
    public void initialize(
            Loadable loadable, AuthenticationLayoutCallback callback, boolean autoReply
    ) {
        this.callback = callback;
        authentication = loadable.site.api().postAuthenticate(loadable);
        isAutoReply = autoReply;
    }

    @Override
    public void reset() {
        if (CaptchaTokenHolder.getInstance().hasToken() && isAutoReply) {
            hideKeyboard(input);
            callback.onAuthenticationComplete(CaptchaTokenHolder.getInstance().getToken(), true);
            return;
        }

        handler.removeCallbacks(RESET_RUNNABLE);
        if (captchaCall != null) {
            captchaCall.cancel();
        }

        bg.setImageBitmap(null);
        fg.setImageBitmap(null);
        colorMatch.setImageResource(R.color.transparent);
        slider.setVisibility(GONE);
        slider.setProgress(1);
        input.setText(null);
        verify.setEnabled(false);
        autoSolve.setText("Solve");
        autoSolve.setEnabled(true);
        captchaAutosolve.loadUrl("javascript:reset();");

        captchaCall = NetUtils.makeRequest(NetUtils.applicationClient.getCloudflareClient(getContext()),
                HttpUrl.get(authentication.baseUrl),
                new NetUtilsClasses.ChainConverter<>((NetUtilsClasses.Converter<ParsedJsonStruct, JsonReader>) input -> {
                    ParsedJsonStruct struct = new ParsedJsonStruct();
                    String error = null;
                    input.beginObject();
                    while (input.hasNext()) {
                        switch (input.nextName()) {
                            case "challenge":
                                struct.challenge = input.nextString();
                                break;
                            case "img":
                                byte[] fgData = Base64.decode(input.nextString(), Base64.DEFAULT);
                                struct.origFg = BitmapFactory.decodeByteArray(fgData, 0, fgData.length);
                                break;
                            case "bg":
                                byte[] bgData = Base64.decode(input.nextString(), Base64.DEFAULT);
                                struct.origBg = BitmapFactory.decodeByteArray(bgData, 0, bgData.length);
                                break;
                            case "error":
                                error = input.nextString();
                                break;
                            case "ttl":
                                struct.ttl = input.nextInt();
                                break;
                            case "cd":
                                struct.cd = input.nextInt() + 2;
                                break;
                            case "img_width":
                            case "bg_width":
                            case "valid_until":
                            case "img_height":
                                // unused
                            default:
                                input.skipValue();
                                break;
                        }
                    }
                    input.endObject();
                    if (error != null) {
                        internalRefresh = true;
                        handler.removeCallbacks(RESET_RUNNABLE);
                        handler.postDelayed(RESET_RUNNABLE, TimeUnit.SECONDS.toMillis(struct.cd));
                        throw new Exception(error + ": " + struct.cd + "s left. This will automatically refresh.");
                    }
                    return struct;
                }).chain(JSON_CONVERTER),
                new MainThreadResponseResult<>(this),
                null,
                NetUtilsClasses.NO_CACHE
        );
    }

    @Override
    public void hardReset() {
        internalRefresh = true;
        reset();
    }

    @Override
    public void onFailure(Exception e) {
        topText.setText("Wait for a refresh!\nHard-refresh to check status.");
        if (internalRefresh) {
            showToast(getContext(), e.getMessage());
            internalRefresh = false;
            return;
        }
        hideKeyboard(input);
        callback.onAuthenticationFailed(e);
    }

    private final Runnable RESET_RUNNABLE = () -> {
        internalRefresh = true;
        currentStruct = null;
        verify.setEnabled(false);
        slider.setOnSeekBarChangeListener(null);
        slider.setVisibility(VISIBLE);
        bg.setTranslationX(0);
        reset();
    };

    @Override
    public void onSuccess(ParsedJsonStruct result) {
        internalRefresh = false;
        currentStruct = result;

        if ("noop".equals(currentStruct.challenge)) {
            hideKeyboard(input);
            CaptchaTokenHolder
                    .getInstance()
                    .addNewToken(currentStruct.challenge,
                            input.getText().toString(),
                            TimeUnit.SECONDS.toMillis(currentStruct.ttl)
                    );
            callback.onAuthenticationComplete(CaptchaTokenHolder.getInstance().getToken(), true);
            return;
        }

        // auto refresh captcha at the cooldown, or the ttl, whatever is longer
        handler.postDelayed(RESET_RUNNABLE, TimeUnit.SECONDS.toMillis(Math.max(currentStruct.ttl, currentStruct.cd)));
        verify.setEnabled(true);

        if (currentStruct.origFg != null) {
            final float scale = fg.getHeight() / (float) currentStruct.origFg.getHeight();
            final float centering = (colorMatch.getWidth() - currentStruct.origFg.getWidth() * scale) / 2f;

            Matrix centerScaleMatrix = new Matrix();
            centerScaleMatrix.postScale(scale, scale);
            centerScaleMatrix.postTranslate(centering, 0);

            topText.setText("Move the slider until text is legible.\n Then enter the text below.");
            slider.setVisibility(VISIBLE);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    Matrix centerScaleTranslateMatrix = new Matrix(centerScaleMatrix);
                    centerScaleTranslateMatrix.postTranslate(-progress * scale, 0);
                    bg.setImageMatrix(centerScaleTranslateMatrix);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            bg.setImageBitmap(currentStruct.origBg);
            bg.setImageMatrix(centerScaleMatrix);

            fg.setImageBitmap(currentStruct.origFg);
            fg.setImageMatrix(centerScaleMatrix);

            colorMatch.setImageResource(R.color.md_grey_200);
        } else {
            topText.setText("Enter the text below.");
            slider.setVisibility(GONE);
            slider.setOnSeekBarChangeListener(null);
        }

        doColorFiltering();

        if (Chan4.captchaAutosolve.get()) {
            tryAutoSolve();
        }
    }

    private void doColorFiltering() {
        ColorMatrix adjustmentMatrix = new ColorMatrix();

        if (ChanSettings.captchaInvertColors.get()) {
            //@formatter:off
            float[] invertValues =
                    {-1, 0, 0, 0, 255,
                            0, -1, 0, 0, 255,
                            0, 0, -1, 0, 255,
                            0, 0, 0, 1, 0};
            ColorMatrix invertMatrix = new ColorMatrix(invertValues);
            //@formatter:on
            adjustmentMatrix.postConcat(invertMatrix);
        }

        if (ChanSettings.captchaMatchColors.get()) {
            int matchColor = getThemeAttrColor(ThemeHelper.getTheme(), R.attr.backcolor);
            float redMult = Color.red(matchColor) / 255f;
            float greenMult = Color.green(matchColor) / 255f;
            float blueMult = Color.blue(matchColor) / 255f;
            ColorMatrix colorMatchMatrix = new ColorMatrix();
            colorMatchMatrix.setScale(redMult, greenMult, blueMult, 1);
            adjustmentMatrix.postConcat(colorMatchMatrix);
        }

        ColorMatrixColorFilter adjustmentFilter = new ColorMatrixColorFilter(adjustmentMatrix);

        colorMatch.setColorFilter(adjustmentFilter);
        bg.setColorFilter(adjustmentFilter);
        fg.setColorFilter(adjustmentFilter);
    }

    public void tryAutoSolve() {
        if (currentStruct == null) return;
        autoSolve.setText("Solving...");
        autoSolve.setEnabled(false);
        if (!webviewReady) {
            post(this::tryAutoSolve);
            return;
        }

        String bgHex = "\"url('data:image/png;base64, " + BitmapUtils.asBase64(currentStruct.origBg) + "')\"";
        String fgHex = "\"url('data:image/png;base64, " + BitmapUtils.asBase64(currentStruct.origFg) + "')\"";
        //@formatter:off
        captchaAutosolve.loadUrl("javascript:"
                + "document.getElementById(\"t-fg\").style.backgroundImage=" + fgHex +";"
                + (currentStruct.origBg != null ? "document.getElementById(\"t-bg\").style.backgroundImage=" + bgHex + ";" : "")
                + "solve(false).then(result => {"
                // The sleep(1000) is required because the loop inside of the solver itself delays by a second
                    + "sleep(1000).then(result2 => {"
                        + "var resultString = document.getElementById(\"t-resp\").value;"
                        + "var resultSlide = document.getElementById(\"t-slider\").value;"
                        + "CaptchaAutocomplete.onAutosolveComplete(resultString, resultSlide);"
                    + "})"
                + "});");
        //@formatter:on
    }

    private static class CaptchaAutoSolveCompleteInterface {
        private final Chan4CustomJsonlayout layout;

        public CaptchaAutoSolveCompleteInterface(Chan4CustomJsonlayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onAutosolveComplete(final String solvedString, final String solvedProgress) {
            BackgroundUtils.runOnMainThread(() -> {
                layout.input.setText(solvedString);
                int sliderValue = Integer.parseInt(solvedProgress) / 2;
                layout.slider.setProgress(Math.max(sliderValue, 1));
                layout.autoSolve.setText("Solve");
                layout.autoSolve.setEnabled(true);
            });
        }
    }

    @Subscribe
    public void onEvent(ChanSettings.SettingChanged<?> settingChanged) {
        if (settingChanged.setting == ChanSettings.captchaMatchColors
                || settingChanged.setting == ChanSettings.captchaInvertColors) {
            doColorFiltering();
        }
    }

    public void destroy() {
        currentStruct = null;
        if (captchaCall != null) {
            captchaCall.cancel();
            captchaCall = null;
        }
        handler.removeCallbacks(RESET_RUNNABLE);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof WebView) {
                removeView(child);
                ((WebView) child).destroy();
            }
        }
    }

    protected static class ParsedJsonStruct {
        public String challenge;
        public int ttl; // how long this captcha will be valid for
        public int cd; // the cooldown until another captcha will be available
        public Bitmap origFg;// foreground image, always appears if an image appears
        public Bitmap origBg;// background image, appears if there's a slider
    }
}
