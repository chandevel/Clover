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
package com.github.adamantcheese.chan.ui.captcha.v2;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ScrollView;
import android.widget.Toast;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.List;

public class CaptchaNoJsLayoutV2 extends FrameLayout
        implements AuthenticationLayoutInterface,
        CaptchaNoJsPresenterV2.AuthenticationCallbacks, View.OnClickListener {
    private static final String TAG = "CaptchaNoJsLayoutV2";

    private AppCompatTextView captchaChallengeTitle;
    private GridView captchaImagesGrid;
    private AppCompatButton captchaVerifyButton;
    private AppCompatButton useOldCaptchaButton;
    private AppCompatButton reloadCaptchaButton;

    private CaptchaNoJsV2Adapter adapter;
    private CaptchaNoJsPresenterV2 presenter;
    private Context context;
    private AuthenticationLayoutCallback callback;

    public CaptchaNoJsLayoutV2(@NonNull Context context) {
        super(context);
        init(context);
    }

    public CaptchaNoJsLayoutV2(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CaptchaNoJsLayoutV2(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.context = context;
        this.presenter = new CaptchaNoJsPresenterV2(this, context);
        this.adapter = new CaptchaNoJsV2Adapter(context);

        View view = inflate(context, R.layout.layout_captcha_nojs_v2, this);

        captchaChallengeTitle = view.findViewById(R.id.captcha_layout_v2_title);
        captchaImagesGrid = view.findViewById(R.id.captcha_layout_v2_images_grid);
        captchaVerifyButton = view.findViewById(R.id.captcha_layout_v2_verify_button);
        useOldCaptchaButton = view.findViewById(R.id.captcha_layout_v2_use_old_captcha_button);
        reloadCaptchaButton = view.findViewById(R.id.captcha_layout_v2_reload_button);
        ConstraintLayout buttonsHolder = view.findViewById(R.id.captcha_layout_v2_buttons_holder);
        ScrollView background = view.findViewById(R.id.captcha_layout_v2_background);

        background.setBackgroundColor(AndroidUtils.getAttrColor(getContext(), R.attr.backcolor));
        buttonsHolder.setBackgroundColor(AndroidUtils.getAttrColor(getContext(), R.attr.backcolor_secondary));
        captchaChallengeTitle.setBackgroundColor(AndroidUtils.getAttrColor(getContext(), R.attr.backcolor_secondary));
        captchaChallengeTitle.setTextColor(AndroidUtils.getAttrColor(getContext(), R.attr.text_color_primary));
        captchaVerifyButton.setTextColor(AndroidUtils.getAttrColor(getContext(), R.attr.text_color_primary));
        useOldCaptchaButton.setTextColor(AndroidUtils.getAttrColor(getContext(), R.attr.text_color_primary));
        reloadCaptchaButton.setTextColor(AndroidUtils.getAttrColor(getContext(), R.attr.text_color_primary));

        captchaVerifyButton.setOnClickListener(this);
        useOldCaptchaButton.setOnClickListener(this);
        reloadCaptchaButton.setOnClickListener(this);

        captchaVerifyButton.setEnabled(false);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        reset();
    }

    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback) {
        this.callback = callback;

        SiteAuthentication authentication = site.actions().postAuthenticate();
        if (authentication.type != SiteAuthentication.Type.CAPTCHA2_NOJS) {
            callback.onFallbackToV1CaptchaView();
            return;
        }

        presenter.init(authentication.siteKey, authentication.baseUrl);
    }

    @Override
    public void reset() {
        hardReset();
    }

    @Override
    public void hardReset() {
        CaptchaNoJsPresenterV2.RequestCaptchaInfoError captchaInfoError = presenter.requestCaptchaInfo();

        switch (captchaInfoError) {
            case Ok:
                break;
            case HoldYourHorses:
                showToast(getContext().getString(
                        R.string.captcha_layout_v2_you_are_requesting_captcha_too_fast));
                break;
            case AlreadyInProgress:
                showToast(getContext().getString(
                        R.string.captcha_layout_v2_captcha_request_is_already_in_progress));
                break;
            case AlreadyShutdown:
                // do nothing
                break;
        }
    }

    @Override
    public void onCaptchaInfoParsed(CaptchaInfo captchaInfo) {
        // called on a background thread

        AndroidUtils.runOnUiThread(() -> {
            captchaVerifyButton.setEnabled(true);
            renderCaptchaWindow(captchaInfo);
        });
    }

    @Override
    public void onVerificationDone(String verificationToken) {
        // called on a background thread

        AndroidUtils.runOnUiThread(() -> {
            captchaVerifyButton.setEnabled(true);
            callback.onAuthenticationComplete(this, null, verificationToken);
        });
    }

    // Called when we got response from re-captcha but could not parse some part of it
    @Override
    public void onCaptchaInfoParseError(Throwable error) {
        // called on a background thread
        AndroidUtils.runOnUiThread(() -> {
            Logger.e(TAG, "CaptchaV2 error", error);

            String message = error.getMessage();
            showToast(message);

            captchaVerifyButton.setEnabled(true);

            callback.onFallbackToV1CaptchaView();
        });
    }

    private void showToast(String message) {
        AndroidUtils.runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onClick(View v) {
        if (v == captchaVerifyButton) {
            sendVerificationResponse();
        } else if (v == useOldCaptchaButton) {
            callback.onFallbackToV1CaptchaView();
        } else if (v == reloadCaptchaButton) {
            reset();
        }
    }

    private void renderCaptchaWindow(CaptchaInfo captchaInfo) {
        try {
            setCaptchaTitle(captchaInfo);

            captchaImagesGrid.setAdapter(null);
            captchaImagesGrid.setAdapter(adapter);

            int columnsCount;
            int imageSize = captchaImagesGrid.getWidth();

            switch (captchaInfo.getCaptchaType()) {
                case Canonical:
                    columnsCount = 3;
                    imageSize /= columnsCount;
                    break;
                case NoCanonical:
                    columnsCount = 2;
                    imageSize /= columnsCount;
                    break;
                default:
                    throw new IllegalStateException("Unknown captcha type");
            }

            captchaImagesGrid.setNumColumns(columnsCount);

            adapter.setImageSize(imageSize);
            adapter.setImages(captchaInfo.challengeImages);

            captchaVerifyButton.setEnabled(true);
        } catch (Throwable error) {
            if (callback != null) {
                callback.onFallbackToV1CaptchaView();
            }
        }
    }

    private void setCaptchaTitle(CaptchaInfo captchaInfo) {
        if (captchaInfo.getCaptchaTitle().hasBold()) {
            SpannableString spannableString = new SpannableString(captchaInfo.getCaptchaTitle().getTitle());
            spannableString.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    captchaInfo.getCaptchaTitle().getBoldStart(),
                    captchaInfo.getCaptchaTitle().getBoldEnd(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            captchaChallengeTitle.setText(spannableString);
        } else {
            captchaChallengeTitle.setText(captchaInfo.getCaptchaTitle().getTitle());
        }
    }

    private void sendVerificationResponse() {
        List<Integer> selectedIds = adapter.getCheckedImageIds();

        try {
            CaptchaNoJsPresenterV2.VerifyError verifyError = presenter.verify(selectedIds);
            switch (verifyError) {
                case Ok:
                    captchaVerifyButton.setEnabled(false);
                    break;
                case NoImagesSelected:
                    showToast(getContext().getString(R.string.captcha_layout_v2_you_have_to_select_at_least_one_image));
                    break;
                case AlreadyInProgress:
                    showToast(getContext().getString(R.string.captcha_layout_v2_verification_already_in_progress));
                    break;
                case AlreadyShutdown:
                    // do nothing
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
