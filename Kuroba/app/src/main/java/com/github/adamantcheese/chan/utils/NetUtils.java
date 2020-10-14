package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;
import android.util.JsonReader;
import android.util.LruCache;
import android.util.MalformedJsonException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.di.NetModule.OkHttpClientWithUtils;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.HttpCall.HttpCallback;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
import static java.nio.charset.StandardCharsets.UTF_8;

public class NetUtils {
    private static final String TAG = "NetUtils";
    // max 1/4 the maximum Dalvik runtime size
    // by default, the max heap size of stock android is 512MiB; keep that in mind if you change things here
    private static final BitmapLruCache imageCache = new BitmapLruCache((int) (getRuntime().maxMemory() / 4));

    private static final Map<HttpUrl, List<BitmapResult>> resultListeners = new HashMap<>();

    public synchronized static void cleanup() {
        resultListeners.clear();
    }

    public static void makeHttpCall(
            HttpCall httpCall, HttpCallback<? extends HttpCall> callback
    ) {
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

    public static Call makeBitmapRequest(@NonNull final HttpUrl url, @NonNull final BitmapResult result) {
        return makeBitmapRequest(url, result, 0, 0);
    }

    public static Call makeBitmapRequest(
            @NonNull final HttpUrl url, @NonNull final BitmapResult result, final int width, final int height
    ) {
        synchronized (NetUtils.class) {
            List<BitmapResult> results = resultListeners.get(url);
            if (results != null) {
                results.add(result);
                return null;
            } else {
                List<BitmapResult> listeners = new ArrayList<>();
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
        call.enqueue(new Callback() {
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
                if (response.code() != 200) {
                    performBitmapFailure(url, new HttpCodeException(response.code()));
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
        });
        return call;
    }

    private static synchronized void performBitmapSuccess(
            @NonNull final HttpUrl url, @NonNull Bitmap bitmap, boolean fromCache
    ) {
        final List<BitmapResult> results = resultListeners.remove(url);
        if (results == null) return;
        for (final BitmapResult bitmapResult : results) {
            if (bitmapResult == null) continue;
            BackgroundUtils.runOnMainThread(() -> bitmapResult.onBitmapSuccess(bitmap, fromCache));
        }
    }

    private static synchronized void performBitmapFailure(@NonNull final HttpUrl url, Exception e) {
        final List<BitmapResult> results = resultListeners.remove(url);
        if (results == null) return;
        for (final BitmapResult bitmapResult : results) {
            if (bitmapResult == null) continue;
            BackgroundUtils.runOnMainThread(() -> bitmapResult.onBitmapFailure(BitmapRepository.error, e));
        }
    }

    public interface BitmapResult {
        void onBitmapFailure(Bitmap errormap, Exception e);

        void onBitmapSuccess(@NonNull Bitmap bitmap, boolean fromCache);
    }

    public static Bitmap getCachedBitmap(HttpUrl url) {
        return imageCache.get(url);
    }

    public static void storeExternalBitmap(HttpUrl url, Bitmap bitmap) {
        imageCache.put(url, bitmap);
    }

    public static <T> Pair<Call, Callback> makeJsonCall(
            @NonNull final HttpUrl url,
            @NonNull final JsonResult<T> result,
            @NonNull final JsonParser<T> parser,
            int timeoutMs
    ) {
        return makeJsonRequest(url, result, parser, timeoutMs, false);
    }

    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final JsonResult<T> result,
            @NonNull final JsonParser<T> parser,
            int timeoutMs
    ) {
        return makeJsonRequest(url, result, parser, timeoutMs, true).first;
    }

    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url, @NonNull final JsonResult<T> result, @NonNull final JsonParser<T> parser
    ) {
        return makeJsonRequest(url, result, parser, 0);
    }

    private static <T> Pair<Call, Callback> makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final JsonResult<T> result,
            @NonNull final JsonParser<T> parser,
            int timeoutMs,
            boolean enqueue
    ) {
        OkHttpClient.Builder clientBuilder = instance(OkHttpClientWithUtils.class).newBuilder();
        clientBuilder.callTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        Call call = clientBuilder.build().newCall(new Request.Builder().url(url).build());
        Callback callback = new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Logger.e(TAG, "Error with request: ", e);
                BackgroundUtils.runOnMainThread(() -> result.onJsonFailure(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() != 200) {
                    BackgroundUtils.runOnMainThread(() -> result.onJsonFailure(new HttpCodeException(response.code())));
                    response.close();
                    return;
                }

                //noinspection ConstantConditions
                try (JsonReader reader = new JsonReader(new InputStreamReader(response.body().byteStream(), UTF_8))) {
                    T read = parser.parse(reader);
                    if (read != null) {
                        BackgroundUtils.runOnMainThread(() -> result.onJsonSuccess(read));
                    } else {
                        BackgroundUtils.runOnMainThread(() -> result.onJsonFailure(new MalformedJsonException(
                                "Json parse returned null object")));
                    }
                } catch (Exception e) {
                    // response is closed at this point because of the try-with-resources block, and response bodies are only one-time read
                    // we can't print out the offending JSON without being horribly memory inefficient
                    Logger.e(TAG, "Error parsing JSON!", e);
                    BackgroundUtils.runOnMainThread(() -> result.onJsonFailure(new MalformedJsonException(e.getMessage())));
                }
            }
        };
        if (enqueue) {
            call.enqueue(callback);
        }
        return new Pair<>(call, callback);
    }

    public interface JsonResult<T> {
        void onJsonFailure(Exception e);

        void onJsonSuccess(T result);
    }

    public interface JsonParser<T> {
        T parse(JsonReader reader)
                throws Exception;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static <T> Call makeHTMLRequest(
            @NonNull final HttpUrl url, @NonNull final HTMLResult<T> result, @NonNull final HTMLReader<T> reader
    ) {
        Call call = instance(OkHttpClientWithUtils.class).newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                BackgroundUtils.runOnMainThread(() -> result.onHTMLFailure(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() != 200) {
                    BackgroundUtils.runOnMainThread(() -> result.onHTMLFailure(new HttpCodeException(response.code())));
                    response.close();
                    return;
                }

                //noinspection ConstantConditions
                try (ByteArrayInputStream baos = new ByteArrayInputStream(response.body().bytes())) {
                    Document document = Jsoup.parse(baos, null, url.toString());

                    T read = reader.read(document);
                    BackgroundUtils.runOnMainThread(() -> result.onHTMLSuccess(read));
                } catch (Exception e) {
                    BackgroundUtils.runOnMainThread(() -> result.onHTMLFailure(e));
                }
                response.close();
            }
        });
        return call;
    }

    public interface HTMLResult<T> {
        void onHTMLFailure(Exception e);

        void onHTMLSuccess(T result);
    }

    public interface HTMLReader<T> {
        T read(Document document);
    }

    public static Call makeHeadersRequest(
            @NonNull final HttpUrl url, @NonNull final HeaderResult result
    ) {
        Call call = instance(OkHttpClientWithUtils.class).newCall(new Request.Builder().url(url).head().build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                BackgroundUtils.runOnMainThread(() -> result.onHeaderFailure(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() != 200) {
                    BackgroundUtils.runOnMainThread(() -> result.onHeaderFailure(new HttpCodeException(response.code())));
                    response.close();
                    return;
                }

                BackgroundUtils.runOnMainThread(() -> result.onHeaderSuccess(response.headers()));
                response.close();
            }
        });
        return call;
    }

    public interface HeaderResult {
        void onHeaderFailure(Exception e);

        void onHeaderSuccess(Headers result);
    }

    public abstract static class IgnoreFailureCallback
            implements Callback {
        public final void onFailure(@NotNull Call call, @NotNull IOException e) {}

        public abstract void onResponse(@NonNull Call call, @NonNull Response response);
    }

    public static class HttpCodeException
            extends Exception {
        public int code;

        public HttpCodeException(int code) {
            this.code = code;
        }

        public boolean isServerErrorNotFound() {
            return code == 404;
        }
    }

    private static class BitmapLruCache
            extends LruCache<HttpUrl, Bitmap> {
        public BitmapLruCache(int maxSize) {
            super(maxSize);
        }

        @Override
        protected int sizeOf(HttpUrl key, Bitmap value) {
            return value.getByteCount();
        }
    }
}
