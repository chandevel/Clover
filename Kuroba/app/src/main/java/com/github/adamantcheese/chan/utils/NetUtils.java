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
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

    /**
     * Request a bitmap without resizing.
     *
     * @param url    The request URL.
     * @param result The callback for this call.
     * @return An enqueued bitmap call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Call makeBitmapRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.BitmapResult result,
            final int width,
            final int height
    ) {
        Pair<Call, Callback> ret = makeBitmapRequest(url, result, width, height, true);
        return ret == null ? null : ret.first;
    }

    /**
     * Request a bitmap with resizing.
     *
     * @param url    The request URL.
     * @param result The callback for this call.
     * @param width  The requested width of the result
     * @param height The requested height of the result
     * @return An enqueued bitmap call. WILL RUN RESULT ON MAIN THREAD!
     */
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
        Call call = instance(OkHttpClientWithUtils.class).newCall(new Request.Builder().url(url)
                .addHeader("Referer", url.toString())
                .build());
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

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        performBitmapFailure(url, new NullPointerException("No response data"));
                        return;
                    }
                    ExceptionCatchingInputStream wrappedStream = new ExceptionCatchingInputStream(body.byteStream());
                    Bitmap bitmap = BitmapUtils.decode(wrappedStream, width, height);
                    if (bitmap == null) {
                        performBitmapFailure(url, new NullPointerException("Bitmap returned is null"));
                        return;
                    }
                    if (wrappedStream.getException() != null) {
                        performBitmapFailure(url, wrappedStream.getException());
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
            BackgroundUtils.runOnMainThread(() -> bitmapResult.onBitmapSuccess(url, bitmap, fromCache));
        }
    }

    private static synchronized void performBitmapFailure(@NonNull final HttpUrl url, Exception e) {
        final List<NetUtilsClasses.BitmapResult> results = resultListeners.remove(url);
        if (results == null) return;
        for (final NetUtilsClasses.BitmapResult bitmapResult : results) {
            if (bitmapResult == null) continue;
            BackgroundUtils.runOnMainThread(() -> bitmapResult.onBitmapFailure(url, e));
        }
    }

    public static Bitmap getCachedBitmap(HttpUrl url) {
        return imageCache.get(url);
    }

    public static void storeExternalBitmap(HttpUrl url, Bitmap bitmap) {
        imageCache.put(url, bitmap);
    }

    /**
     * Request some JSON, no timeout.
     *
     * @param url    The request URL.
     * @param result The callback for this call.
     * @param parser The parser that will process the response into your result
     * @param <T>    Your type
     * @return An enqueued JSON call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.JSONProcessor<T> parser
    ) {
        return makeJsonRequest(url, result, parser, 0);
    }

    /**
     * Request some JSON, with a timeout.
     *
     * @param url       The request URL.
     * @param result    The callback for this call.
     * @param parser    The parser that will process the response into your result
     * @param <T>       Your type
     * @param timeoutMs Optional timeout in milliseconds
     * @return An enqueued JSON call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.JSONProcessor<T> parser,
            int timeoutMs
    ) {
        return makeRequest(url, new NetUtilsClasses.JSONConverter(), parser, result, timeoutMs);
    }

    /**
     * Request some HTML, no timeout.
     *
     * @param url    The request URL.
     * @param result The callback for this call.
     * @param reader The reader that will process the response into your result
     * @param <T>    Your type
     * @return An enqueued HTML call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    @SuppressWarnings("UnusedReturnValue")
    public static <T> Call makeHTMLRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.HTMLProcessor<T> reader
    ) {
        return makeRequest(url, new NetUtilsClasses.HTMLConverter(), reader, result, 0);
    }

    //internal helper method, you probably want the one below this
    private static <T, X> Call makeRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseConverter<X> converter,
            @NonNull final NetUtilsClasses.ResponseProcessor<T, X> reader,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            int timeoutMs
    ) {
        return makeCall(url, converter, reader, result, timeoutMs, true).first;
    }

    /**
     * This is the mothership of this class mostly, it does all the heavy lifting for you once provided the proper stuff
     * Generally don't use this! Use one of the wrapper methods instead.
     *
     * @param url       The request URL.
     * @param converter The converter that will convert the response into a form the reader can process.
     * @param reader    The reader that will process the response into your result.
     * @param result    The callback for this call.
     * @param <T>       Your result type
     * @param <X>       Your processor type
     * @param timeoutMs Optional timeout in milliseconds
     * @param enqueue   whether or not to enqueue this call as a step
     * @return An optionally enqueued call along with the callback it is associated with. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T, X> Pair<Call, Callback> makeCall(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseConverter<X> converter,
            @NonNull final NetUtilsClasses.ResponseProcessor<T, X> reader,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            int timeoutMs,
            boolean enqueue
    ) {
        OkHttpClient.Builder clientBuilder = instance(OkHttpClientWithUtils.class).newBuilder();
        clientBuilder.callTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        Request.Builder builder = new Request.Builder().url(url);
        Call call = clientBuilder.build().newCall(builder.build());
        Callback callback = new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                BackgroundUtils.runOnBackgroundThread(() -> result.onFailure(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    result.onFailure(new NetUtilsClasses.HttpCodeException(response.code()));
                    response.close();
                    return;
                }

                try {
                    T read = reader.process(converter.convert(call.request().url(), response.body()));
                    if (read == null) throw new NullPointerException("Process returned null!");
                    result.onSuccess(read);
                } catch (Exception e) {
                    result.onFailure(e);
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

    /**
     * @param url    The request URL.
     * @param result The callback for this call.
     * @return An enqueued headers call. WILL RUN RESULT ON BACKGROUND THREAD!
     */
    public static Call makeHeadersRequest(
            @NonNull final HttpUrl url, @NonNull final NetUtilsClasses.ResponseResult<Headers> result
    ) {
        Call call = instance(OkHttpClientWithUtils.class).newCall(new Request.Builder().url(url).head().build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                BackgroundUtils.runOnBackgroundThread(() -> result.onFailure(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    BackgroundUtils.runOnBackgroundThread(() -> result.onFailure(new NetUtilsClasses.HttpCodeException(
                            response.code())));
                } else {
                    BackgroundUtils.runOnBackgroundThread(() -> result.onSuccess(response.headers()));
                }
                response.close();
            }
        });
        return call;
    }
}
