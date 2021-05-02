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

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.Chan.inject;
import static java.util.concurrent.TimeUnit.SECONDS;

public class CaptchaV2NoJsPresenter {
    private static final String recaptchaUrlBase = "https://www.google.com/recaptcha/api/fallback?k=";

    private final CaptchaV2NoJsHtmlParser parser;

    @Nullable
    private AuthenticationCallbacks callbacks;
    @Nullable
    private CaptchaV2NoJsInfo prevCaptchaV2NoJsInfo = null;

    private final AtomicBoolean verificationInProgress = new AtomicBoolean(false);
    private final AtomicBoolean captchaRequestInProgress = new AtomicBoolean(false);
    private String siteKey;
    private String baseUrl;
    private long lastTimeCaptchaRequest = 0L;

    public CaptchaV2NoJsPresenter(@Nullable AuthenticationCallbacks callbacks) {
        inject(this);
        this.callbacks = callbacks;
        this.parser = new CaptchaV2NoJsHtmlParser();
    }

    public void init(String siteKey, String baseUrl) {
        this.siteKey = siteKey;
        this.baseUrl = baseUrl;
    }

    /**
     * Send challenge solution back to the recaptcha
     */
    public VerifyError verify(List<Integer> selectedIds)
            throws CaptchaNoJsV2Error {
        if (!verificationInProgress.compareAndSet(false, true)) {
            Logger.d(this, "Verify captcha request is already in progress");
            return VerifyError.ALREADY_IN_PROGRESS;
        }

        try {
            if (selectedIds.isEmpty()) {
                verificationInProgress.set(false);
                return VerifyError.NO_IMAGES_SELECTED;
            }

            if (prevCaptchaV2NoJsInfo == null) {
                throw new CaptchaNoJsV2Error("prevCaptchaInfo is null");
            }

            if (prevCaptchaV2NoJsInfo.cParameter == null) {
                throw new CaptchaNoJsV2Error("C parameter is null");
            }

            BackgroundUtils.runOnBackgroundThread(() -> {
                try {
                    String recaptchaUrl = recaptchaUrlBase + siteKey;
                    RequestBody body = createRequestBody(prevCaptchaV2NoJsInfo, selectedIds);

                    Logger.d(CaptchaV2NoJsPresenter.this, "Verify called");

                    Request request = new Request.Builder().url(recaptchaUrl)
                            .post(body)
                            .addHeader("Referer", recaptchaUrl)
                            .build();

                    try (Response response = NetUtils.applicationClient.newCall(request).execute()) {
                        prevCaptchaV2NoJsInfo = handleGetRecaptchaResponse(response);
                    } finally {
                        verificationInProgress.set(false);
                    }
                } catch (Throwable error) {
                    if (callbacks != null) {
                        try {
                            prevCaptchaV2NoJsInfo = null;
                            callbacks.onCaptchaInfoParseError(error);
                        } finally {
                            verificationInProgress.set(false);
                        }
                    }
                }
            });

            return VerifyError.OK;
        } catch (Throwable error) {
            verificationInProgress.set(false);
            throw error;
        }
    }

    /**
     * Requests captcha data, parses it and then passes it to the render function
     */
    public RequestCaptchaInfoError requestCaptchaInfo() {
        if (!captchaRequestInProgress.compareAndSet(false, true)) {
            Logger.d(this, "Request captcha request is already in progress");
            return RequestCaptchaInfoError.ALREADY_IN_PROGRESS;
        }

        try {
            // recaptcha may become very angry at you if your are fetching it too fast
            if (System.currentTimeMillis() - lastTimeCaptchaRequest < SECONDS.toMillis(3)) {
                captchaRequestInProgress.set(false);
                Logger.d(this, "Requesting captcha info too fast");
                return RequestCaptchaInfoError.HOLD_YOUR_HORSES;
            }

            lastTimeCaptchaRequest = System.currentTimeMillis();

            BackgroundUtils.runOnBackgroundThread(() -> {
                try {
                    try {
                        prevCaptchaV2NoJsInfo = getCaptchaInfo();
                    } catch (Throwable error) {
                        if (callbacks != null) {
                            callbacks.onCaptchaInfoParseError(error);
                        }

                        throw error;
                    }
                } catch (Throwable error) {
                    Logger.e(CaptchaV2NoJsPresenter.this, "Error while executing captcha requests", error);

                    prevCaptchaV2NoJsInfo = null;
                } finally {
                    captchaRequestInProgress.set(false);
                }
            });

            return RequestCaptchaInfoError.OK;
        } catch (Throwable error) {
            captchaRequestInProgress.set(false);

            if (callbacks != null) {
                callbacks.onCaptchaInfoParseError(error);
            }

            // return ok here too because we already handled this exception in the callback
            return RequestCaptchaInfoError.OK;
        }
    }

    @Nullable
    private CaptchaV2NoJsInfo getCaptchaInfo()
            throws IOException {
        BackgroundUtils.ensureBackgroundThread();

        Request request = new Request.Builder().url(recaptchaUrlBase + siteKey)
                .cacheControl(NetUtilsClasses.NO_CACHE)
                .addHeader("Referer", baseUrl)
                .build();

        try (Response response = NetUtils.applicationClient.newCall(request).execute()) {
            return handleGetRecaptchaResponse(response);
        }
    }

    private RequestBody createRequestBody(CaptchaV2NoJsInfo prevCaptchaV2NoJsInfo, List<Integer> selectedIds)
            throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();

        sb.append(URLEncoder.encode("c", "utf-8"));
        sb.append("=");
        sb.append(URLEncoder.encode(prevCaptchaV2NoJsInfo.cParameter, "utf-8"));
        sb.append("&");

        for (Integer selectedImageId : selectedIds) {
            sb.append(URLEncoder.encode("response", "utf-8"));
            sb.append("=");
            sb.append(URLEncoder.encode(String.valueOf(selectedImageId), "utf-8"));
            sb.append("&");
        }

        String resultBody;

        if (selectedIds.size() > 0) {
            // trim trailing &
            resultBody = sb.deleteCharAt(sb.length() - 1).toString();
        } else {
            resultBody = sb.toString();
        }

        return MultipartBody.create(resultBody, MediaType.parse("application/x-www-form-urlencoded"));
    }

    @Nullable
    private CaptchaV2NoJsInfo handleGetRecaptchaResponse(Response response) {
        try {
            if (!response.isSuccessful()) {
                if (callbacks != null) {
                    callbacks.onCaptchaInfoParseError(new IOException(
                            "Bad status code for captcha request = " + response.code()));
                }

                return null;
            }

            ResponseBody body = response.body();
            if (body == null) {
                if (callbacks != null) {
                    callbacks.onCaptchaInfoParseError(new IOException("Captcha response body is empty (null)"));
                }

                return null;
            }

            String bodyString = body.string();
            if (!bodyString.contains("reCAPTCHA challenge")) {
                throw new IllegalStateException("Response body does not contain \"reCAPTCHA challenge\" string");
            }

            if (bodyString.contains("fbc-verification-token")) {
                // got the token
                String verificationToken = parser.parseVerificationToken(bodyString);
                Logger.d(this, "Got the verification token");

                if (callbacks != null) {
                    callbacks.onVerificationDone(verificationToken);
                }

                return null;
            } else {
                // got the challenge
                CaptchaV2NoJsInfo captchaV2NoJsInfo = parser.parseHtml(bodyString, siteKey);
                Logger.d(this, "Got new challenge");

                if (callbacks != null) {
                    callbacks.onCaptchaInfoParsed(captchaV2NoJsInfo);
                } else {
                    // Return null when callbacks are null to reset prevCaptchaInfo so that we won't
                    // get stuck without captchaInfo and disabled buttons forever
                    return null;
                }

                return captchaV2NoJsInfo;
            }
        } catch (Throwable e) {
            Logger.e(this, "Error while trying to parse captcha html data", e);

            if (callbacks != null) {
                callbacks.onCaptchaInfoParseError(e);
            }

            return null;
        }
    }

    public void onDestroy() {
        this.callbacks = null;
        this.prevCaptchaV2NoJsInfo = null;
        this.verificationInProgress.set(false);
        this.captchaRequestInProgress.set(false);
    }

    public enum VerifyError {
        OK,
        NO_IMAGES_SELECTED,
        ALREADY_IN_PROGRESS
    }

    public enum RequestCaptchaInfoError {
        OK,
        ALREADY_IN_PROGRESS,
        HOLD_YOUR_HORSES
    }

    public interface AuthenticationCallbacks {
        void onCaptchaInfoParsed(CaptchaV2NoJsInfo captchaV2NoJsInfo);

        void onCaptchaInfoParseError(Throwable error);

        void onVerificationDone(String verificationToken);
    }

    public static class CaptchaNoJsV2Error
            extends Exception {
        public CaptchaNoJsV2Error(String message) {
            super(message);
        }
    }
}
