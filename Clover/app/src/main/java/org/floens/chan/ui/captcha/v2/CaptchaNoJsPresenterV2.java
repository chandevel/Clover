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
package org.floens.chan.ui.captcha.v2;

import android.content.Context;
import android.support.annotation.Nullable;

import org.floens.chan.utils.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CaptchaNoJsPresenterV2 {
    private static final String TAG = "CaptchaNoJsPresenterV2";
    // TODO: change useragent?
    private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36";
    private static final String recaptchaUrlBase = "https://www.google.com/recaptcha/api/fallback?k=";
    private static final String encoding = "UTF-8";
    private static final String mediaType = "application/x-www-form-urlencoded";
    private static final String recaptchaChallengeString = "reCAPTCHA challenge";
    private static final String verificationTokenString = "fbc-verification-token";
    private static final int SUCCESS_STATUS_CODE = 200;
    private static final long CAPTCHA_REQUEST_THROTTLE_MS = 3000L;

    // this should be updated once in 3 months IIRC
    private static final String googleCookies =
                    "SID=gjaHjfFJPAN5HO3MVVZpjHFKa_249dsfjHa9klsiaflsd99.asHqjsM2lAS; " +
                    "HSID=j7m0aFJ82lPF7Hd9d; " +
                    "SSID=nJKpa81jOskq7Jsps; " +
                    "NID=87=gkOAkg09AKnvJosKq82kgnDnHj8Om2pLskKhdna02msog8HkdHDlasDf";

    // TODO: inject this in the future when https://github.com/Floens/Clover/pull/678 is merged
    private final OkHttpClient okHttpClient = new OkHttpClient();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CaptchaNoJsHtmlParser parser;

    @Nullable
    private AuthenticationCallbacks callbacks;
    @Nullable
    private CaptchaInfo prevCaptchaInfo = null;
    private AtomicBoolean verificationInProgress = new AtomicBoolean(false);
    private AtomicBoolean captchaRequestInProgress = new AtomicBoolean(false);

    private String siteKey;
    private String baseUrl;
    private long lastTimeCaptchRequest = 0L;

    public CaptchaNoJsPresenterV2(@Nullable AuthenticationCallbacks callbacks, Context context) {
        this.callbacks = callbacks;
        this.parser = new CaptchaNoJsHtmlParser(context, okHttpClient);
    }

    public void init(String siteKey, String baseUrl) {
        this.siteKey = siteKey;
        this.baseUrl = baseUrl;
    }

    /**
     * Send challenge solution back
     */
    public VerifyError verify(
            List<Integer> selectedIds
    ) throws CaptchaNoJsV2Error, UnsupportedEncodingException {
        if (!verificationInProgress.compareAndSet(false, true)) {
            return VerifyError.AlreadyInProgress;
        }

        try {
            if (selectedIds.isEmpty()) {
                verificationInProgress.set(false);
                return VerifyError.NoImagesSelected;
            }

            if (prevCaptchaInfo == null) {
                throw new CaptchaNoJsV2Error("prevCaptchaInfo is null");
            }

            if (prevCaptchaInfo.getcParameter() == null) {
                throw new CaptchaNoJsV2Error("C parameter is null");
            }

            String recaptchaUrl = recaptchaUrlBase + siteKey;
            RequestBody body = createResponseBody(prevCaptchaInfo, selectedIds);

            Request request = new Request.Builder()
                    .url(recaptchaUrl)
                    .post(body)
                    .header("User-Agent", userAgent)
                    .header("Referer", recaptchaUrl)
                    .header("Accept-Language", "en-US")
                    .header("Cookie", googleCookies)
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (callbacks != null) {
                        try {
                            prevCaptchaInfo = null;
                            callbacks.onCaptchaInfoParseError(e);
                        } finally {
                            verificationInProgress.set(false);
                        }
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    executor.execute(() -> {
                        // to avoid okhttp's threads to hang

                        try {
                            prevCaptchaInfo = handleGetRecaptchaResponse(response);
                        } finally {
                            verificationInProgress.set(false);
                            response.close();
                        }
                    });
                }
            });

            return VerifyError.Ok;
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
            return RequestCaptchaInfoError.AlreadyInProgress;
        }

        try {
            // recaptcha may become very angry at you if your are fetching it too fast
            if (System.currentTimeMillis() - lastTimeCaptchRequest < CAPTCHA_REQUEST_THROTTLE_MS) {
                captchaRequestInProgress.set(false);
                return RequestCaptchaInfoError.HoldYourHorses;
            }

            lastTimeCaptchRequest = System.currentTimeMillis();
            String recaptchaUrl = recaptchaUrlBase + siteKey;

            Request request = new Request.Builder()
                    .url(recaptchaUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", baseUrl)
                    .header("Accept-Language", "en-US")
                    .header("Cookie", googleCookies)
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (callbacks != null) {
                        try {
                            prevCaptchaInfo = null;
                            callbacks.onCaptchaInfoParseError(e);
                        } finally {
                            captchaRequestInProgress.set(false);
                        }
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    executor.execute(() -> {
                        // to avoid okhttp's threads to hang

                        try {
                            prevCaptchaInfo = handleGetRecaptchaResponse(response);
                        } finally {
                            captchaRequestInProgress.set(false);
                            response.close();
                        }
                    });
                }
            });

            return RequestCaptchaInfoError.Ok;
        } catch (Throwable error) {
            captchaRequestInProgress.set(false);

            if (callbacks != null) {
                callbacks.onCaptchaInfoParseError(error);
            }

            // return ok here too because we already handled this exception in the callback
            return RequestCaptchaInfoError.Ok;
        }
    }

    private RequestBody createResponseBody(
            CaptchaInfo prevCaptchaInfo,
            List<Integer> selectedIds
    ) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();

        sb.append(URLEncoder.encode("c", encoding));
        sb.append("=");
        sb.append(URLEncoder.encode(prevCaptchaInfo.getcParameter(), encoding));
        sb.append("&");

        for (Integer selectedImageId : selectedIds) {
            sb.append(URLEncoder.encode("response", encoding));
            sb.append("=");
            sb.append(URLEncoder.encode(String.valueOf(selectedImageId), encoding));
            sb.append("&");
        }

        String resultBody;

        if (selectedIds.size() > 0) {
            resultBody = sb.deleteCharAt(sb.length() - 1).toString();
        } else {
            resultBody = sb.toString();
        }

        return MultipartBody.create(
                MediaType.parse(mediaType),
                resultBody);
    }

    @Nullable
    private CaptchaInfo handleGetRecaptchaResponse(Response response) {
        try {
            if (response.code() != SUCCESS_STATUS_CODE) {
                if (callbacks != null) {
                    callbacks.onCaptchaInfoParseError(
                            new IOException("Bad status code for captcha request = " + response.code()));
                }

                return null;
            }

            ResponseBody body = response.body();
            if (body == null) {
                if (callbacks != null) {
                    callbacks.onCaptchaInfoParseError(
                            new IOException("Captcha response body is empty (null)"));
                }

                return null;
            }

            String bodyString = body.string();
            if (!bodyString.contains(recaptchaChallengeString)) {
                throw new IllegalStateException("Response body does not contain \"reCAPTCHA challenge\" string");
            }

            if (bodyString.contains(verificationTokenString)) {
                // got the token
                String verificationToken = parser.parseVerificationToken(bodyString);

                if (callbacks != null) {
                    callbacks.onVerificationDone(verificationToken);
                }

                return null;
            } else {
                // got the challenge
                CaptchaInfo captchaInfo = parser.parseHtml(bodyString, siteKey);

                if (callbacks != null) {
                    callbacks.onCaptchaInfoParsed(captchaInfo);
                } else {
                    // Return null when callbacks are null to reset prevCaptchaInfo so that we won't
                    // get stuck without captchaInfo and disabled buttons forever
                    return null;
                }

                return captchaInfo;
            }
        } catch (Throwable e) {
            Logger.e(TAG, "Error while trying to parse captcha html data", e);

            if (callbacks != null) {
                callbacks.onCaptchaInfoParseError(e);
            }

            return null;
        }
    }

    public void onDestroy() {
        this.callbacks = null;
        this.prevCaptchaInfo = null;
        this.verificationInProgress.set(false);
        this.captchaRequestInProgress.set(false);

        executor.shutdown();
    }

    public enum VerifyError {
        Ok,
        NoImagesSelected,
        AlreadyInProgress
    }

    public enum RequestCaptchaInfoError {
        Ok,
        AlreadyInProgress,
        HoldYourHorses
    }

    public interface AuthenticationCallbacks {
        void onCaptchaInfoParsed(CaptchaInfo captchaInfo);

        void onCaptchaInfoParseError(Throwable error);

        void onVerificationDone(String verificationToken);
    }

    public static class CaptchaNoJsV2Error extends Exception {
        public CaptchaNoJsV2Error(String message) {
            super(message);
        }
    }
}
