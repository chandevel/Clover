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
package com.github.adamantcheese.chan.ui.captcha.v2.nojs;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.ui.captcha.CaptchaTokenHolder;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.adamantcheese.chan.core.site.SiteAuthentication.Type.CAPTCHA2_NOJS;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

public class CaptchaV2NoJsLayout
        extends FrameLayout
        implements AuthenticationLayoutInterface, CaptchaV2NoJsPresenter.AuthenticationCallbacks {
    private static final long RECAPTCHA_TOKEN_LIVE_TIME = TimeUnit.MINUTES.toMillis(2);

    private final TextView captchaChallengeTitle;
    private final GridView captchaImagesGrid;
    private final Button captchaVerifyButton;

    private final CaptchaV2NoJsAdapter adapter;
    private final CaptchaV2NoJsPresenter presenter;
    private AuthenticationLayoutCallback callback;

    private boolean isAutoReply = true;

    public CaptchaV2NoJsLayout(@NonNull Context context) {
        this(context, null, 0);
    }

    public CaptchaV2NoJsLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CaptchaV2NoJsLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.presenter = new CaptchaV2NoJsPresenter(this);
        this.adapter = new CaptchaV2NoJsAdapter();

        View view = inflate(context, R.layout.layout_captcha_nojs_v2, this);

        if (ChanSettings.moveInputToBottom.get()) {
            LinearLayout topLevel = findViewById(R.id.captcha_layout_v2_top_level);
            topLevel.setGravity(Gravity.BOTTOM);
        }
        captchaChallengeTitle = view.findViewById(R.id.captcha_layout_v2_title);
        captchaImagesGrid = view.findViewById(R.id.captcha_layout_v2_images_grid);
        captchaVerifyButton = view.findViewById(R.id.captcha_layout_v2_verify_button);
        Button useOldCaptchaButton = view.findViewById(R.id.captcha_layout_v2_use_old_captcha_button);
        Button reloadCaptchaButton = view.findViewById(R.id.captcha_layout_v2_reload_button);

        captchaVerifyButton.setOnClickListener(v -> sendVerificationResponse());
        useOldCaptchaButton.setOnClickListener(v -> callback.onFallbackToV1CaptchaView(isAutoReply));
        reloadCaptchaButton.setOnClickListener(v -> reset());
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        //note: this is here because if we kept the captcha images on rotate, a bunch of recycled bitmap errors are thrown
        //instead of dealing with that, just get a new, fresh captcha
        reset();
    }

    @Override
    public void initialize(Loadable loadable, AuthenticationLayoutCallback callback, boolean autoReply) {
        this.callback = callback;
        this.isAutoReply = autoReply;

        SiteAuthentication authentication = loadable.site.actions().postAuthenticate(loadable);
        if (authentication.type != CAPTCHA2_NOJS) {
            callback.onFallbackToV1CaptchaView(isAutoReply);
            return;
        }

        presenter.init(authentication.siteKey, authentication.baseUrl);
    }

    @Override
    public void reset() {
        if (CaptchaTokenHolder.getInstance().hasToken() && isAutoReply) {
            callback.onAuthenticationComplete(this, CaptchaTokenHolder.getInstance().getToken(), true);
            return;
        }

        hardReset();
    }

    @Override
    public void hardReset() {
        CaptchaV2NoJsPresenter.RequestCaptchaInfoError captchaInfoError = presenter.requestCaptchaInfo();

        switch (captchaInfoError) {
            case OK:
                break;
            case HOLD_YOUR_HORSES:
                showToast(
                        getContext(),
                        R.string.captcha_layout_v2_you_are_requesting_captcha_too_fast,
                        Toast.LENGTH_LONG
                );
                break;
            case ALREADY_IN_PROGRESS:
                showToast(
                        getContext(),
                        R.string.captcha_layout_v2_captcha_request_is_already_in_progress,
                        Toast.LENGTH_LONG
                );
                break;
        }
    }

    @Override
    public void onCaptchaInfoParsed(CaptchaV2NoJsInfo captchaV2NoJsInfo) {
        BackgroundUtils.runOnMainThread(() -> {
            captchaVerifyButton.setEnabled(true);
            renderCaptchaWindow(captchaV2NoJsInfo);
        });
    }

    @Override
    public void onVerificationDone(String verificationToken) {
        BackgroundUtils.runOnMainThread(() -> {
            CaptchaTokenHolder.getInstance().addNewToken(null, verificationToken, RECAPTCHA_TOKEN_LIVE_TIME);

            CaptchaTokenHolder.CaptchaToken token;

            if (isAutoReply) {
                token = CaptchaTokenHolder.getInstance().getToken();
            } else {
                token = new CaptchaTokenHolder.CaptchaToken(null, verificationToken, 0);
            }

            captchaVerifyButton.setEnabled(true);
            callback.onAuthenticationComplete(this, token, isAutoReply);
        });
    }

    // Called when we got response from re-captcha but could not parse some part of it
    @Override
    public void onCaptchaInfoParseError(@NonNull Throwable error) {
        BackgroundUtils.runOnMainThread(() -> {
            Logger.e(CaptchaV2NoJsLayout.this, "CaptchaV2 error", error);
            showToast(getContext(), error.getMessage(), Toast.LENGTH_LONG);
            captchaVerifyButton.setEnabled(true);
            callback.onFallbackToV1CaptchaView(isAutoReply);
        });
    }

    private void renderCaptchaWindow(CaptchaV2NoJsInfo captchaV2NoJsInfo) {
        try {
            setCaptchaTitle(captchaV2NoJsInfo);

            captchaImagesGrid.setAdapter(adapter);
            int columnsCount = captchaV2NoJsInfo.captchaType.columnCount;
            int imageSize = Math.min(getWidth(), getHeight() - dp(104));
            //40 + 64dp from layout xml; width for left-right full span, height minus for top-bottom full span inc buttons and titlebar
            ViewGroup.LayoutParams layoutParams = captchaImagesGrid.getLayoutParams();
            layoutParams.height = imageSize;
            layoutParams.width = imageSize;
            captchaImagesGrid.setLayoutParams(layoutParams);

            imageSize /= columnsCount;
            captchaImagesGrid.setNumColumns(columnsCount);

            adapter.setImageSize(imageSize);
            adapter.setImages(captchaV2NoJsInfo.challengeImages);

            captchaImagesGrid.postInvalidate();

            captchaVerifyButton.setEnabled(true);
        } catch (Throwable error) {
            if (callback != null) {
                callback.onFallbackToV1CaptchaView(isAutoReply);
            }
        }
    }

    private void setCaptchaTitle(CaptchaV2NoJsInfo captchaV2NoJsInfo) {
        captchaChallengeTitle.setText(captchaV2NoJsInfo.captchaTitle);
    }

    private void sendVerificationResponse() {
        List<Integer> selectedIds = adapter.getCheckedImageIds();

        try {
            CaptchaV2NoJsPresenter.VerifyError verifyError = presenter.verify(selectedIds);
            switch (verifyError) {
                case OK:
                    captchaVerifyButton.setEnabled(false);
                    break;
                case NO_IMAGES_SELECTED:
                    showToast(
                            getContext(),
                            R.string.captcha_layout_v2_you_have_to_select_at_least_one_image,
                            Toast.LENGTH_LONG
                    );
                    break;
                case ALREADY_IN_PROGRESS:
                    showToast(
                            getContext(),
                            R.string.captcha_layout_v2_verification_already_in_progress,
                            Toast.LENGTH_LONG
                    );
                    break;
            }
        } catch (Throwable error) {
            onCaptchaInfoParseError(error);
        }
    }

    public void onDestroy() {
        adapter.onDestroy();
        presenter.onDestroy();
    }
}
