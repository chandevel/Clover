package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.di.NetModule.OkHttpClientWithUtils;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.HttpCall.HttpCallback;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.Chan.instance;
import static java.lang.Runtime.getRuntime;

public class NetUtils {
    private static final String TAG = "NetUtils";
    // max 1/4 the maximum Dalvik runtime size
    // by default, the max heap size of stock android is 512MiB; keep that in mind if you change things here
    private static final NetUtilsClasses.BitmapLruCache imageCache =
            new NetUtilsClasses.BitmapLruCache((int) (getRuntime().maxMemory() / 4));

    private static final Map<HttpUrl, List<NetUtilsClasses.BitmapResult>> resultListeners = new HashMap<>();

    public synchronized static void cleanup() {
        resultListeners.clear();
    }

    public static void makeHttpCall(HttpCall httpCall, HttpCallback<? extends HttpCall> callback) {
        makeHttpCall(httpCall, callback, null);
    }

    public static void makeHttpCall(
            HttpCall httpCall,
            HttpCallback<? extends HttpCall> callback,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        httpCall.setCallback(callback);

        Request.Builder requestBuilder = new Request.Builder();

        httpCall.setup(requestBuilder, progressListener);

        if (httpCall.getSite() != null) {
            final CommonSite.CommonCallModifier siteCallModifier = httpCall.getSite().callModifier();
            if (siteCallModifier != null) {
                siteCallModifier.modifyHttpCall(httpCall, requestBuilder);
            }
        }

        requestBuilder.header("User-Agent", NetModule.USER_AGENT);
        Request request = requestBuilder.build();

        instance(OkHttpClientWithUtils.class).getProxiedClient().newCall(request).enqueue(httpCall);
    }

    public static Call makeBitmapRequest(
            @NonNull final HttpUrl url, @NonNull final NetUtilsClasses.BitmapResult result
    ) {
        return makeBitmapRequest(url, result, 0, 0);
    }

    public static Call makeBitmapRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.BitmapResult result,
            final int width,
            final int height
    ) {
        Pair<Call, Callback> ret = makeBitmapRequest(url, result, width, height, true);
        return ret == null ? null : ret.first;
    }

    public static Pair<Call, Callback> makeBitmapRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.BitmapResult result,
            final int width,
            final int height,
            boolean enqueue
    ) {
        synchronized (NetUtils.class) {
            List<NetUtilsClasses.BitmapResult> results = resultListeners.get(url);
            if (results != null) {
                results.add(result);
                return null;
            } else {
                List<NetUtilsClasses.BitmapResult> listeners = new ArrayList<>();
                listeners.add(result);
                resultListeners.put(url, listeners);
            }
        }
        Bitmap cachedBitmap = imageCache.get(url);
        if (cachedBitmap != null) {
            performBitmapSuccess(url, cachedBitmap, true);
            return null;
        }
        Call call =
                instance(OkHttpClientWithUtils.class).getBitmapClient().newCall(new Request.Builder().url(url).build());
        Callback callback = new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (!"Canceled".equals(e.getMessage())) {
                    Logger.e(TAG, "Error loading bitmap from " + url.toString());
                    performBitmapFailure(url, e);
                    return;
                }
                synchronized (NetUtils.class) {
                    resultListeners.remove(url);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    performBitmapFailure(url, new NetUtilsClasses.HttpCodeException(response.code()));
                    response.close();
                    return;
                }

                BackgroundUtils.runOnBackgroundThread(() -> {
                    try (ResponseBody body = response.body()) {
                        if (body == null) {
                            performBitmapFailure(url, new NullPointerException("No response data"));
                            return;
                        }
                        Bitmap bitmap = BitmapUtils.decode(body.byteStream(), width, height);
                        if (bitmap == null) {
                            performBitmapFailure(url, new NullPointerException("Bitmap returned is null"));
                            return;
                        }
                        imageCache.put(url, bitmap);
                        performBitmapSuccess(url, bitmap, false);
                    } catch (Exception e) {
                        performBitmapFailure(url, e);
                    } catch (OutOfMemoryError e) {
                        getRuntime().gc();
                        performBitmapFailure(url, new IOException(e));
                    }
                });
            }
        };
        if (enqueue) {
            call.enqueue(callback);
        }
        return new Pair<>(call, callback);
    }

    private static synchronized void performBitmapSuccess(
            @NonNull final HttpUrl url, @NonNull Bitmap bitmap, boolean fromCache
    ) {
        final List<NetUtilsClasses.BitmapResult> results = resultListeners.remove(url);
        if (results == null) return;
        for (final NetUtilsClasses.BitmapResult bitmapResult : results) {
            if (bitmapResult == null) continue;
            BackgroundUtils.runOnMainThread(() -> bitmapResult.onBitmapSuccess(bitmap, fromCache));
        }
    }

    private static synchronized void performBitmapFailure(@NonNull final HttpUrl url, Exception e) {
        final List<NetUtilsClasses.BitmapResult> results = resultListeners.remove(url);
        if (results == null) return;
        for (final NetUtilsClasses.BitmapResult bitmapResult : results) {
            if (bitmapResult == null) continue;
            BackgroundUtils.runOnMainThread(() -> bitmapResult.onBitmapFailure(e));
        }
    }

    public static Bitmap getCachedBitmap(HttpUrl url) {
        return imageCache.get(url);
    }

    public static void storeExternalBitmap(HttpUrl url, Bitmap bitmap) {
        imageCache.put(url, bitmap);
    }

    public static <T> Pair<Call, Callback> makePostJsonCall(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.JSONProcessor<T> parser,
            int timeoutMs,
            @NonNull final String postData,
            @NonNull final String contentType
    ) {
        return makeRequest(
                url,
                new NetUtilsClasses.JSONConverter(),
                parser,
                result,
                timeoutMs,
                false,
                new Pair<>(postData, contentType)
        );
    }

    public static <T> Pair<Call, Callback> makeJsonCall(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.JSONProcessor<T> parser,
            int timeoutMs
    ) {
        return makeRequest(url, new NetUtilsClasses.JSONConverter(), parser, result, timeoutMs, false, null);
    }

    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.JSONProcessor<T> parser,
            int timeoutMs
    ) {
        return makeRequest(url, new NetUtilsClasses.JSONConverter(), parser, result, timeoutMs, true, null).first;
    }

    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.JSONProcessor<T> parser
    ) {
        return makeJsonRequest(url, result, parser, 0);
    }

    public static <T> Pair<Call, Callback> makeHTMLCall(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.HTMLProcessor<T> reader,
            int timeoutMs
    ) {
        return makeRequest(url, new NetUtilsClasses.HTMLConverter(), reader, result, timeoutMs, false, null);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static <T> Call makeHTMLRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.HTMLProcessor<T> reader
    ) {
        return makeRequest(url, new NetUtilsClasses.HTMLConverter(), reader, result, 0, true, null).first;
    }

    /**
     * This is the mothership of this class mostly, it does all the heavy lifting for you once provided the proper stuff
     */
    private static <T, X> Pair<Call, Callback> makeRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseConverter<X> converter,
            @NonNull final NetUtilsClasses.ResponseProcessor<T, X> reader,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            int timeoutMs,
            boolean enqueue,
            final Pair<String, String> postData
    ) {
        OkHttpClient.Builder clientBuilder = instance(OkHttpClientWithUtils.class).newBuilder();
        clientBuilder.callTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        Request.Builder builder = new Request.Builder().url(url);
        if (postData != null) {
            builder.header("Origin", url.host())
                    .header("Referer", url.host())
                    .post(RequestBody.create(postData.first, MediaType.get(postData.second)));
        }
        Call call = clientBuilder.build().newCall(builder.build());
        Callback callback = new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                BackgroundUtils.runOnMainThread(() -> result.onFailure(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    BackgroundUtils.runOnMainThread(() -> result.onFailure(new NetUtilsClasses.HttpCodeException(
                            response.code())));
                    response.close();
                    return;
                }

                try {
                    T read = reader.process(converter.convert(response.body()));
                    if (read == null) throw new NullPointerException("Process returned null!");
                    BackgroundUtils.runOnMainThread(() -> result.onSuccess(read));
                } catch (Exception e) {
                    BackgroundUtils.runOnMainThread(() -> result.onFailure(e));
                } finally {
                    response.close();
                }
            }
        };
        if (enqueue) {
            call.enqueue(callback);
        }
        return new Pair<>(call, callback);
    }

    public static Call makeHeadersRequest(
            @NonNull final HttpUrl url, @NonNull final NetUtilsClasses.ResponseResult<Headers> result
    ) {
        Call call = instance(OkHttpClientWithUtils.class).newCall(new Request.Builder().url(url).head().build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                BackgroundUtils.runOnMainThread(() -> result.onFailure(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    BackgroundUtils.runOnMainThread(() -> result.onFailure(new NetUtilsClasses.HttpCodeException(
                            response.code())));
                    response.close();
                    return;
                }

                BackgroundUtils.runOnMainThread(() -> result.onSuccess(response.headers()));
                response.close();
            }
        });
        return call;
    }
}
