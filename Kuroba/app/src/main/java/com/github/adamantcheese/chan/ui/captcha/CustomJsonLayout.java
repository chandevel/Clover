package com.github.adamantcheese.chan.ui.captcha;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.MainThreadResponseResult;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;

public class CustomJsonLayout
        extends LinearLayout
        implements AuthenticationLayoutInterface, ResponseResult<CustomJsonLayout.ParsedJsonStruct> {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isAutoReply;

    private AuthenticationLayoutCallback callback;
    private SiteAuthentication authentication;
    private Call captchaCall;
    private ParsedJsonStruct currentStruct;
    private boolean internalRefresh;

    private TextView topText;
    private ConstraintLayout wrapper;
    private ImageView bg;
    private ImageView fg;
    private SeekBar slider;
    private TextInputEditText input;
    private ImageView verify;

    public CustomJsonLayout(Context context) {
        super(context);
    }

    public CustomJsonLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomJsonLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        topText = findViewById(R.id.top_text);
        wrapper = findViewById(R.id.wrapper);
        bg = findViewById(R.id.bg);
        fg = findViewById(R.id.fg);
        slider = findViewById(R.id.slider);
        input = findViewById(R.id.captcha_input);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                verify.callOnClick();
                return true;
            }
            return false;
        });
        verify = findViewById(R.id.verify);
    }

    @Override
    public void initialize(
            Loadable loadable, AuthenticationLayoutCallback callback, boolean autoReply
    ) {
        this.callback = callback;
        authentication = loadable.site.actions().postAuthenticate(loadable);
        isAutoReply = autoReply;
    }

    @Override
    public void reset() {
        if (CaptchaTokenHolder.getInstance().hasToken() && isAutoReply) {
            hideKeyboard(input);
            callback.onAuthenticationComplete(this, CaptchaTokenHolder.getInstance().getToken(), true);
            return;
        }

        if (currentStruct != null && System.currentTimeMillis() / 1000L < currentStruct.cdUntil) {
            showToast(getContext(), "You have to wait before refreshing!");
            return;
        }

        handler.removeCallbacks(RESET_RUNNABLE);
        if (captchaCall != null) {
            captchaCall.cancel();
        }

        bg.setImageBitmap(null);
        fg.setImageBitmap(null);
        slider.setVisibility(GONE);
        slider.setProgress(slider.getMax() / 2);
        input.setText(null);

        captchaCall = NetUtils.makeJsonRequest(HttpUrl.get(authentication.baseUrl),
                new MainThreadResponseResult<>(this),
                input -> {
                    ParsedJsonStruct struct = new ParsedJsonStruct();
                    int cd = 0;
                    String error = null;
                    input.beginObject();
                    while (input.hasNext()) {
                        switch (input.nextName()) {
                            case "challenge":
                                struct.challenge = input.nextString();
                                break;
                            case "cd_until":
                                struct.cdUntil = input.nextLong();
                                break;
                            case "img":
                                byte[] fgData = Base64.decode(input.nextString(), Base64.DEFAULT);
                                struct.origFg = BitmapFactory.decodeByteArray(fgData, 0, fgData.length);
                                // resize to wrapping view draw area, capped at 4x image size
                                int viewWidth =
                                        wrapper.getWidth() - wrapper.getPaddingLeft() - wrapper.getPaddingRight();
                                struct.scale = Math.min(viewWidth / (float) struct.origFg.getWidth(), 4.0f);
                                int cappedWidth = (int) (struct.origFg.getWidth() * struct.scale);
                                float fgAspectRatio = struct.origFg.getWidth() / (float) struct.origFg.getHeight();
                                struct.fg = Bitmap.createScaledBitmap(struct.origFg,
                                        cappedWidth,
                                        (int) (cappedWidth / fgAspectRatio),
                                        false
                                );
                                break;
                            case "bg":
                                byte[] bgData = Base64.decode(input.nextString(), Base64.DEFAULT);
                                struct.origBg = BitmapFactory.decodeByteArray(bgData, 0, bgData.length);
                                // resize to match foreground image height
                                float bgAspectRatio = struct.origBg.getWidth() / (float) struct.origBg.getHeight();
                                struct.bg = Bitmap.createScaledBitmap(struct.origBg,
                                        (int) (struct.fg.getHeight() * bgAspectRatio),
                                        struct.fg.getHeight(),
                                        false
                                );
                                break;
                            case "error":
                                error = input.nextString();
                                break;
                            case "ttl":
                                struct.ttl = input.nextInt();
                                break;
                            case "cd":
                                cd = input.nextInt();
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
                        handler.postDelayed(RESET_RUNNABLE, TimeUnit.SECONDS.toMillis(cd + 1));
                        throw new Exception(error + ": " + cd + "s left. This will automatically refresh.");
                    }
                    return struct;
                },
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
        verify.setOnClickListener(null);
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
            CaptchaTokenHolder.getInstance()
                    .addNewToken(currentStruct.challenge,
                            input.getText().toString(),
                            TimeUnit.SECONDS.toMillis(currentStruct.ttl)
                    );
            callback.onAuthenticationComplete(this, CaptchaTokenHolder.getInstance().getToken(), true);
            return;
        }

        // auto refresh captcha a little after captcha expires
        handler.postDelayed(RESET_RUNNABLE, TimeUnit.SECONDS.toMillis(currentStruct.ttl + 5));

        verify.setOnClickListener(v -> {
            handler.removeCallbacks(RESET_RUNNABLE);
            hideKeyboard(input);

            CaptchaTokenHolder.getInstance()
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

            callback.onAuthenticationComplete(this, token, isAutoReply);
        });

        if (currentStruct.bg != null) {
            topText.setText("Slide the slider so the images line up.\n Then enter the text below.");
            slider.setVisibility(VISIBLE);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    bg.setTranslationX((progress - slider.getMax() / 2f) * currentStruct.scale);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            // TODO autosolve
            //slider.setProgress(tryAutoSolve());
        } else {
            topText.setText("Enter the text below.");
            slider.setVisibility(GONE);
            slider.setOnSeekBarChangeListener(null);
        }

        bg.setImageBitmap(currentStruct.bg);
        fg.setImageBitmap(currentStruct.fg);
    }

    public void destroy() {
        currentStruct = null;
        if (captchaCall != null) {
            captchaCall.cancel();
            captchaCall = null;
        }
        handler.removeCallbacks(RESET_RUNNABLE);
    }

    private int tryAutoSolve() {
        Bitmap fgSizedBitmap = currentStruct.origFg.copy(currentStruct.origFg.getConfig(), true);
        Canvas temp = new Canvas(fgSizedBitmap);
        int bestProgress = 0;
        float bestBlackRatio = 0;
        for (int i = 0; i <= 50; i++) {
            temp.drawBitmap(currentStruct.origBg, i - 25, 0, null);
            temp.drawBitmap(currentStruct.origFg, 0, 0, null);
            int[] pixels = new int[fgSizedBitmap.getByteCount()];
            fgSizedBitmap.getPixels(pixels,
                    0,
                    fgSizedBitmap.getWidth(),
                    0,
                    0,
                    fgSizedBitmap.getWidth(),
                    fgSizedBitmap.getHeight()
            );
            int blackPixels = 0;
            for (int j = fgSizedBitmap.getWidth(); j < pixels.length - fgSizedBitmap.getWidth(); j++) {
                if (pixels[j] == Color.BLACK && pixels[j - fgSizedBitmap.getWidth()] == Color.BLACK
                        && pixels[j + fgSizedBitmap.getWidth()] == Color.BLACK && pixels[j + 1] == Color.BLACK
                        && pixels[j - 1] == Color.BLACK) blackPixels++;
            }
            if (blackPixels / (float) pixels.length > bestBlackRatio) {
                bestProgress = i;
                bestBlackRatio = blackPixels / (float) pixels.length;
            }
        }
        return bestProgress;
    }

    protected static class ParsedJsonStruct {
        public String challenge;
        public int ttl;
        public long cdUntil;
        public Bitmap origFg;
        public Bitmap origBg;
        public float scale;
        public Bitmap fg; // foreground image, always appears if an image appears
        public Bitmap bg; // background image, appears if there's a slider
    }
}
