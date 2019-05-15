/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CaptchaNoJsPresenterV2 {
    private static final String TAG = "CaptchaNoJsPresenterV2";
    private static final String userAgentHeader = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.131 Safari/537.36";
    private static final String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3";
    private static final String acceptEncodingHeader = "deflate, br";
    private static final String acceptLanguageHeader = "en-US";

    private static final String recaptchaUrlBase = "https://www.google.com/recaptcha/api/fallback?k=";
    private static final String googleBaseUrl = "https://www.google.com/";
    private static final String encoding = "UTF-8";
    private static final String mediaType = "application/x-www-form-urlencoded";
    private static final String recaptchaChallengeString = "reCAPTCHA challenge";
    private static final String verificationTokenString = "fbc-verification-token";
    private static final String setCookieHeaderName = "set-cookie";
    private static final int SUCCESS_STATUS_CODE = 200;
    private static final long CAPTCHA_REQUEST_THROTTLE_MS = 3000L;

    // this cookie is taken from dashchan
    private static final String defaultGoogleCookies = "NID=87=gkOAkg09AKnvJosKq82kgnDnHj8Om2pLskKhdna02msog8HkdHDlasDf";

    // TODO: inject this in the future when https://github.com/Floens/Clover/pull/678 is merged
    private final OkHttpClient okHttpClient = new OkHttpClient();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CaptchaNoJsHtmlParser parser;

    @Nullable
    private AuthenticationCallbacks callbacks;
    @Nullable
    private CaptchaInfo prevCaptchaInfo = null;
    @NonNull
    // either the default cookie or a real cookie
    private volatile String googleCookie;

    private AtomicBoolean verificationInProgress = new AtomicBoolean(false);
    private AtomicBoolean captchaRequestInProgress = new AtomicBoolean(false);
    private AtomicBoolean refreshCookiesRequestInProgress = new AtomicBoolean(false);
    private String siteKey;
    private String baseUrl;
    private long lastTimeCaptchaRequest = 0L;

    public CaptchaNoJsPresenterV2(@Nullable AuthenticationCallbacks callbacks, Context context) {
        this.callbacks = callbacks;
        this.parser = new CaptchaNoJsHtmlParser(context, okHttpClient);

        this.googleCookie = ChanSettings.googleCookie.get();
    }

    public void init(String siteKey, String baseUrl) {
        this.siteKey = siteKey;
        this.baseUrl = baseUrl;
    }

    /**
     * Send challenge solution back to the recaptcha
     */
    public VerifyError verify(
            List<Integer> selectedIds
    ) throws CaptchaNoJsV2Error {
        if (!verificationInProgress.compareAndSet(false, true)) {
            Logger.d(TAG, "Verify captcha request is already in progress");
            return VerifyError.AlreadyInProgress;
        }

        if (executor.isShutdown()) {
            verificationInProgress.set(false);
            Logger.d(TAG, "Cannot verify, executor has been shut down");
            return VerifyError.AlreadyShutdown;
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

            if (googleCookie.isEmpty()) {
                throw new IllegalStateException("Google cookies are not supposed to be empty here");
            }

            executor.submit(() -> {
                try {
                    String recaptchaUrl = recaptchaUrlBase + siteKey;
                    RequestBody body = createResponseBody(prevCaptchaInfo, selectedIds);

                    Request request = new Request.Builder()
                            .url(recaptchaUrl)
                            .post(body)
                            .header("Referer", recaptchaUrl)
                            .header("User-Agent", userAgentHeader)
                            .header("Accept", acceptHeader)
                            .header("Accept-Encoding", acceptEncodingHeader)
                            .header("Accept-Language", acceptLanguageHeader)
                            .header("Cookie", googleCookie)
                            .build();

                    try (Response response = okHttpClient.newCall(request).execute()) {
                        prevCaptchaInfo = handleGetRecaptchaResponse(response);
                    } finally {
                        verificationInProgress.set(false);
                    }
                } catch (Throwable error) {
                    if (callbacks != null) {
                        try {
                            prevCaptchaInfo = null;
                            callbacks.onCaptchaInfoParseError(error);
                        } finally {
                            verificationInProgress.set(false);
                        }
                    }
                }
            });

            return VerifyError.Ok;
        } catch (Throwable error) {
            verificationInProgress.set(false);
            throw error;
        }
    }

    /**
     * Manually refreshes the google cookie
     * */
    public void refreshCookies() {
        if (!refreshCookiesRequestInProgress.compareAndSet(false, true)) {
            Logger.d(TAG, "Google cookie request is already in progress");
            return;
        }

        if (executor.isShutdown()) {
            refreshCookiesRequestInProgress.set(false);
            Logger.d(TAG, "Cannot request google cookie, executor has been shut down");
            return;
        }

        executor.submit(() -> {
            try {
                googleCookie = getGoogleCookies(true);

                if (callbacks != null) {
                    callbacks.onGoogleCookiesRefreshed();
                }
            } catch (IOException e) {
                if (callbacks != null) {
                    callbacks.onGetGoogleCookieError(false, e);
                }
            } finally {
                refreshCookiesRequestInProgress.set(false);
            }
        });
    }

    /**
     * Requests captcha data, parses it and then passes it to the render function
     */
    public RequestCaptchaInfoError requestCaptchaInfo() {
        if (!captchaRequestInProgress.compareAndSet(false, true)) {
            Logger.d(TAG, "Request captcha request is already in progress");
            return RequestCaptchaInfoError.AlreadyInProgress;
        }

        try {
            // recaptcha may become very angry at you if your are fetching it too fast
            if (System.currentTimeMillis() - lastTimeCaptchaRequest < CAPTCHA_REQUEST_THROTTLE_MS) {
                captchaRequestInProgress.set(false);
                Logger.d(TAG, "Requesting captcha info too fast");
                return RequestCaptchaInfoError.HoldYourHorses;
            }

            if (executor.isShutdown()) {
                captchaRequestInProgress.set(false);
                Logger.d(TAG, "Cannot request captcha info, executor has been shut down");
                return RequestCaptchaInfoError.AlreadyShutdown;
            }

            lastTimeCaptchaRequest = System.currentTimeMillis();

            executor.submit(() -> {
                try {
                    try {
                        googleCookie = getGoogleCookies(false);
                    } catch (Throwable error) {
                        if (callbacks != null) {
                            callbacks.onGetGoogleCookieError(true, error);
                        }

                        throw error;
                    }

                    try {
                        prevCaptchaInfo = getCaptchaInfo();
                    } catch (Throwable error) {
                        if (callbacks != null) {
                            callbacks.onCaptchaInfoParseError(error);
                        }

                        throw error;
                    }
                } catch (Throwable error) {
                    Logger.e(TAG, "Error while executing captcha requests", error);

                    prevCaptchaInfo = null;
                    googleCookie = defaultGoogleCookies;
                } finally {
                    captchaRequestInProgress.set(false);
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

    @NonNull
    private String getGoogleCookies(boolean forced) throws IOException {
        if (BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must not be executed on the main thread");
        }

        if (!ChanSettings.useRealGoogleCookies.get()) {
            Logger.d(TAG, "Google cookies request is disabled in the settings, using the default ones");
            return defaultGoogleCookies;
        }

        if (!forced && !googleCookie.isEmpty()) {
            Logger.d(TAG, "We already have google cookies");
            return googleCookie;
        }

        Request request = new Request.Builder()
                .url(googleBaseUrl)
                .header("User-Agent", userAgentHeader)
                .header("Accept", acceptHeader)
                .header("Accept-Encoding", acceptEncodingHeader)
                .header("Accept-Language", acceptLanguageHeader)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String newCookie = handleGetGoogleCookiesResponse(response);
            ChanSettings.googleCookie.set(newCookie);

            Logger.d(TAG, "Successfully refreshed google cookies, new cookie = " + newCookie);
            return newCookie;
        }
    }

    @Nullable
    private CaptchaInfo getCaptchaInfo() throws IOException {
        if (BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must not be executed on the main thread");
        }

        if (googleCookie.isEmpty()) {
            throw new IllegalStateException("Google cookies are not supposed to be null here");
        }

        String recaptchaUrl = recaptchaUrlBase + siteKey;

        Request request = new Request.Builder()
                .url(recaptchaUrl)
                .header("Referer", baseUrl)
                .header("User-Agent", userAgentHeader)
                .header("Accept", acceptHeader)
                .header("Accept-Encoding", acceptEncodingHeader)
                .header("Accept-Language", acceptLanguageHeader)
                .header("Cookie", googleCookie)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            return handleGetRecaptchaResponse(response);
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

    @NonNull
    private String handleGetGoogleCookiesResponse(Response response) {
        if (response.code() != SUCCESS_STATUS_CODE) {
            Logger.w(TAG, "Get google cookies request returned bad status code = " + response.code());
            return defaultGoogleCookies;
        }

        Headers headers = response.headers();

        for (String headerName : headers.names()) {
            if (headerName.equalsIgnoreCase(setCookieHeaderName)) {
                String setCookieHeader = headers.get(headerName);
                if (setCookieHeader != null) {
                    String[] split = setCookieHeader.split(";");
                    for (String splitPart : split) {
                        if (splitPart.startsWith("NID")) {
                            return splitPart;
                        }
                    }
                }
            }
        }

        Logger.d(TAG, "Could not find the NID cookie in the headers");
        return defaultGoogleCookies;
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
                Logger.d(TAG, "Got the verification token");

                if (callbacks != null) {
                    callbacks.onVerificationDone(verificationToken);
                }

                return null;
            } else {
                // got the challenge
                CaptchaInfo captchaInfo = parser.parseHtml(bodyString, siteKey);
                Logger.d(TAG, "Got new challenge");

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
        AlreadyInProgress,
        AlreadyShutdown
    }

    public enum RequestCaptchaInfoError {
        Ok,
        AlreadyInProgress,
        HoldYourHorses,
        AlreadyShutdown
    }

    public interface AuthenticationCallbacks {
        void onGetGoogleCookieError(boolean shouldFallback, Throwable error);

        void onGoogleCookiesRefreshed();

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
