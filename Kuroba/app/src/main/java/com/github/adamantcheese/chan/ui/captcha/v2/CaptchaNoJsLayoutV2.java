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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

public class CaptchaNoJsLayoutV2 extends FrameLayout
        implements AuthenticationLayoutInterface,
        CaptchaNoJsPresenterV2.AuthenticationCallbacks {
    private static final String TAG = "CaptchaNoJsLayoutV2";

    private AppCompatTextView captchaChallengeTitle;
    private GridView captchaImagesGrid;
    private AppCompatButton captchaVerifyButton;

    private CaptchaNoJsV2Adapter adapter;
    private CaptchaNoJsPresenterV2 presenter;
    private Context context;
    private AuthenticationLayoutCallback callback;

    public CaptchaNoJsLayoutV2(@NonNull Context context) {
        this(context, null, 0);
    }

    public CaptchaNoJsLayoutV2(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CaptchaNoJsLayoutV2(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        this.presenter = new CaptchaNoJsPresenterV2(this, context);
        this.adapter = new CaptchaNoJsV2Adapter(context);

        View view = inflate(context, R.layout.layout_captcha_nojs_v2, this);

        captchaChallengeTitle = view.findViewById(R.id.captcha_layout_v2_title);
        captchaImagesGrid = view.findViewById(R.id.captcha_layout_v2_images_grid);
        captchaVerifyButton = view.findViewById(R.id.captcha_layout_v2_verify_button);
        AppCompatButton useOldCaptchaButton = view.findViewById(R.id.captcha_layout_v2_use_old_captcha_button);
        AppCompatButton reloadCaptchaButton = view.findViewById(R.id.captcha_layout_v2_reload_button);

        captchaVerifyButton.setOnClickListener(v -> sendVerificationResponse());
        useOldCaptchaButton.setOnClickListener(v -> callback.onFallbackToV1CaptchaView());
        reloadCaptchaButton.setOnClickListener(v -> reset());
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        //note: this is here because if we kept the captcha images on rotate, a bunch of recycled bitmap errors are thrown
        //instead of dealing with that, just get a new, fresh captcha
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
            case OK:
            case ALREADY_SHUTDOWN:
                break;
            case HOLD_YOUR_HORSES:
                showToast(getContext().getString(
                        R.string.captcha_layout_v2_you_are_requesting_captcha_too_fast));
                break;
            case ALREADY_IN_PROGRESS:
                showToast(getContext().getString(
                        R.string.captcha_layout_v2_captcha_request_is_already_in_progress));
                break;
        }
    }

    @Override
    public void onCaptchaInfoParsed(CaptchaInfo captchaInfo) {
        AndroidUtils.runOnUiThread(() -> {
            captchaVerifyButton.setEnabled(true);
            renderCaptchaWindow(captchaInfo);
        });
    }

    @Override
    public void onVerificationDone(String verificationToken) {
        AndroidUtils.runOnUiThread(() -> {
            captchaVerifyButton.setEnabled(true);
            callback.onAuthenticationComplete(this, null, verificationToken);
        });
    }

    // Called when we got response from re-captcha but could not parse some part of it
    @Override
    public void onCaptchaInfoParseError(Throwable error) {
        AndroidUtils.runOnUiThread(() -> {
            Logger.e(TAG, "CaptchaV2 error", error);
            showToast(error.getMessage());
            captchaVerifyButton.setEnabled(true);
            callback.onFallbackToV1CaptchaView();
        });
    }

    private void showToast(String message) {
        AndroidUtils.runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    private void renderCaptchaWindow(CaptchaInfo captchaInfo) {
        try {
            setCaptchaTitle(captchaInfo);

            captchaImagesGrid.setAdapter(adapter);
            int columnsCount;
            int imageSize = Math.min(getWidth(), getHeight() - dp(104));
            //40 + 64dp from layout xml; width for left-right full span, height minus for top-bottom full span inc buttons and titlebar
            ViewGroup.LayoutParams layoutParams = captchaImagesGrid.getLayoutParams();
            layoutParams.height = imageSize;
            layoutParams.width = imageSize;
            captchaImagesGrid.setLayoutParams(layoutParams);
            switch (captchaInfo.getCaptchaType()) {
                case CANONICAL:
                    columnsCount = 3;
                    break;
                case NO_CANONICAL:
                    columnsCount = 2;
                    break;
                default:
                    throw new IllegalStateException("Unknown captcha type");
            }
            imageSize /= columnsCount;
            captchaImagesGrid.setNumColumns(columnsCount);

            adapter.setImageSize(imageSize);
            adapter.setImages(captchaInfo.challengeImages);

            captchaImagesGrid.postInvalidate();

            captchaVerifyButton.setEnabled(true);
        } catch (Throwable error) {
            if (callback != null) {
                callback.onFallbackToV1CaptchaView();
            }
        }
    }

    private void setCaptchaTitle(CaptchaInfo captchaInfo) {
        if (captchaInfo.getCaptchaTitle() != null) {
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
    }

    private void sendVerificationResponse() {
        List<Integer> selectedIds = adapter.getCheckedImageIds();

        try {
            CaptchaNoJsPresenterV2.VerifyError verifyError = presenter.verify(selectedIds);
            switch (verifyError) {
                case OK:
                    captchaVerifyButton.setEnabled(false);
                    break;
                case NO_IMAGES_SELECTED:
                    showToast(getContext().getString(R.string.captcha_layout_v2_you_have_to_select_at_least_one_image));
                    break;
                case ALREADY_IN_PROGRESS:
                    showToast(getContext().getString(R.string.captcha_layout_v2_verification_already_in_progress));
                    break;
                case ALREADY_SHUTDOWN:
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
