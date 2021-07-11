package com.github.adamantcheese.chan.ui.captcha;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.MainThreadResponseResult;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.widget.CancellableToast;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;

public class CustomJsonLayout
        extends LinearLayout
        implements AuthenticationLayoutInterface, ResponseResult<CustomJsonLayout.ParsedJsonStruct> {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isAutoReply;

    private AuthenticationLayoutCallback callback;
    private SiteAuthentication authentication;
    private ParsedJsonStruct currentStruct;

    private ConstraintLayout wrapper;
    private ImageView bg;
    private ImageView fg;
    private SeekBar slider;
    private TextInputEditText input;
    private Button verify;

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
        wrapper = findViewById(R.id.wrapper);
        bg = findViewById(R.id.bg);
        fg = findViewById(R.id.fg);
        slider = findViewById(R.id.slider);
        input = findViewById(R.id.captcha_input);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
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
            CancellableToast.showToast(getContext(), "You have to wait before refreshing!");
            return;
        }

        handler.removeCallbacks(RESET_RUNNABLE);

        bg.setImageBitmap(null);
        fg.setImageBitmap(null);
        slider.setProgress(slider.getMax() / 2);
        input.setText(null);

        NetUtils.makeJsonRequest(HttpUrl.get(authentication.baseUrl), new MainThreadResponseResult<>(this), input -> {
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
                        struct.fg = BitmapFactory.decodeByteArray(fgData, 0, fgData.length);
                        // resize to wrapping view draw area, capped at 4x image size
                        int viewWidth = wrapper.getWidth() - wrapper.getPaddingLeft() - wrapper.getPaddingRight();
                        struct.fg = Bitmap.createScaledBitmap(struct.fg,
                                Math.min(viewWidth, struct.fg.getWidth() * 4),
                                (int) (Math.min(viewWidth, struct.fg.getWidth() * 4) * ((float) struct.fg.getHeight()
                                        / (float) struct.fg.getWidth())),
                                true
                        );
                        break;
                    case "bg":
                        byte[] bgData = Base64.decode(input.nextString(), Base64.DEFAULT);
                        struct.bg = BitmapFactory.decodeByteArray(bgData, 0, bgData.length);
                        // resize to match foreground image height
                        struct.bg = Bitmap.createScaledBitmap(struct.bg,
                                (int) (struct.fg.getHeight() * ((float) struct.bg.getWidth()
                                        / (float) struct.bg.getHeight())),
                                struct.fg.getHeight(),
                                true
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
                throw new Exception(error + ": " + cd + "s left.");
            }
            return struct;
        }, NetUtilsClasses.NO_CACHE);
    }

    @Override
    public void hardReset() {
        reset();
    }

    @Override
    public void onFailure(Exception e) {
        hideKeyboard(input);
        callback.onAuthenticationFailed(e);
    }

    private final Runnable RESET_RUNNABLE = () -> {
        currentStruct = null;
        verify.setOnClickListener(null);
        slider.setOnSeekBarChangeListener(null);
        slider.setVisibility(VISIBLE);
        bg.setTranslationX(0);
        reset();
    };

    @Override
    public void onSuccess(ParsedJsonStruct result) {
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

        handler.postDelayed(RESET_RUNNABLE, TimeUnit.SECONDS.toMillis(currentStruct.ttl));

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
            slider.setVisibility(VISIBLE);
            slider.setMax(currentStruct.fg.getWidth() / 2);
            slider.setProgress(slider.getMax() / 2);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    bg.setTranslationX(progress - slider.getMax() / 2f);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        } else {
            slider.setVisibility(GONE);
            slider.setOnSeekBarChangeListener(null);
        }

        bg.setImageBitmap(currentStruct.bg);
        fg.setImageBitmap(currentStruct.fg);
    }

    protected static class ParsedJsonStruct {
        public String challenge;
        public int ttl;
        public long cdUntil;
        public Bitmap fg;
        public Bitmap bg;
    }
}
