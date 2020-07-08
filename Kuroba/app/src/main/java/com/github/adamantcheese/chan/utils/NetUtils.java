package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.JsonReader;
import android.util.LruCache;
import android.util.MalformedJsonException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.di.NetModule.ProxiedOkHttpClient;
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
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.highResCells;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getActivityManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static java.nio.charset.StandardCharsets.UTF_8;

public class NetUtils {
    private static final String TAG = "NetUtils";
    // max 1/3 the maximum Dalvik runtime size for hi res cells, 1/4 otherwise; if low RAM, 1/8
    //by default, the max heap size of stock android is 512MiB; keep that in mind if you change things here
    private static final BitmapLruCache imageCache = new BitmapLruCache((int) (Runtime.getRuntime().maxMemory() / (
            getActivityManager().isLowRamDevice()
                    ? 8
                    : (highResCells.get() ? 3 : 4))));

    private static final Map<HttpUrl, List<BitmapResult>> resultListeners = new HashMap<>();
    private static final Bitmap errorBitmap = BitmapFactory.decodeResource(getRes(), R.drawable.error_icon);

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

        instance(ProxiedOkHttpClient.class).getProxiedClient().newCall(request).enqueue(httpCall);
    }

    public static Call makeBitmapRequest(@NonNull final HttpUrl url, @NonNull final BitmapResult result) {
        return makeBitmapRequest(url, result, 0, 0);
    }

    public static Call makeBitmapRequest(
            @NonNull final HttpUrl url, @NonNull final BitmapResult result, final int width, final int height
    ) {
        synchronized (resultListeners) {
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
        Call call = instance(ProxiedOkHttpClient.class).newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Logger.e(TAG, "Error loading bitmap from " + url.toString());
                performBitmapFailure(url, e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() != 200) {
                    performBitmapFailure(url, new HttpCodeException(response.code()));
                    response.close();
                    return;
                }

                try (ResponseBody body = response.body()) {
                    //noinspection ConstantConditions
                    Bitmap bitmap = BitmapUtils.decode(body.bytes(), width, height);
                    if (bitmap == null) {
                        performBitmapFailure(url, new NullPointerException("Bitmap returned is null"));
                        return;
                    }
                    imageCache.put(url, bitmap);
                    performBitmapSuccess(url, bitmap, false);
                } catch (Exception e) {
                    performBitmapFailure(url, e);
                } catch (OutOfMemoryError e) {
                    Runtime.getRuntime().gc();
                    performBitmapFailure(url, new IOException(e));
                }
            }
        });
        return call;
    }

    private static void performBitmapSuccess(@NonNull final HttpUrl url, @NonNull Bitmap bitmap, boolean fromCache) {
        BackgroundUtils.runOnMainThread(() -> {
            synchronized (resultListeners) {
                List<BitmapResult> results = resultListeners.get(url);
                if (results == null) return;
                for (BitmapResult bitmapResult : results) {
                    if (bitmapResult == null) continue;
                    bitmapResult.onBitmapSuccess(bitmap, fromCache);
                }
                resultListeners.remove(url);
            }
        });
    }

    private static void performBitmapFailure(@NonNull final HttpUrl url, Exception e) {
        BackgroundUtils.runOnMainThread(() -> {
            synchronized (resultListeners) {
                List<BitmapResult> results = resultListeners.get(url);
                if (results == null) return;
                for (BitmapResult bitmapResult : results) {
                    if (bitmapResult == null) continue;
                    bitmapResult.onBitmapFailure(NetUtils.errorBitmap, e);
                }
                resultListeners.remove(url);
            }
        });
    }

    public interface BitmapResult {
        void onBitmapFailure(Bitmap errormap, Exception e);

        void onBitmapSuccess(@NonNull Bitmap bitmap, boolean fromCache);
    }

    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url, @NonNull final JsonResult<T> result, @NonNull final JsonParser<T> parser
    ) {
        Call call = instance(ProxiedOkHttpClient.class).newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
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
                try (JsonReader jsonReader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(response.body()
                        .bytes()), UTF_8))) {
                    T read = parser.parse(jsonReader);
                    if (read != null) {
                        BackgroundUtils.runOnMainThread(() -> result.onJsonSuccess(read));
                    } else {
                        BackgroundUtils.runOnMainThread(() -> result.onJsonFailure(new MalformedJsonException(
                                "Json parse returned null object")));
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing JSON: ", e);
                    BackgroundUtils.runOnMainThread(() -> result.onJsonFailure(new MalformedJsonException(e.getMessage())));
                }
                response.close();
            }
        });
        return call;
    }

    public static <T> T makeJsonRequestSync(@NonNull final HttpUrl url, @NonNull final JsonParser<T> parser) {
        Call call = instance(ProxiedOkHttpClient.class).newBuilder()
                .callTimeout(2500, TimeUnit.MILLISECONDS)
                .build()
                .newCall(new Request.Builder().url(url).build());
        try (Response response = call.execute()) {
            if (response.code() != 200) {
                Logger.e(TAG, "Response code was not OK:" + response.code());
                response.close();
                return null;
            }

            //noinspection ConstantConditions
            try (JsonReader jsonReader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(response.body()
                    .bytes()), UTF_8))) {
                return parser.parse(jsonReader);
            } catch (Exception e) {
                Logger.e(TAG, "Error parsing JSON: ", e);
                if (response.body() != null) {
                    //noinspection ConstantConditions
                    Logger.e(TAG, "Bad JSON: " + response.body().string());
                }
                return null;
            }
        } catch (IOException e) {
            Logger.e(TAG, "Error with request: ", e);
            return null;
        }
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
        Call call = instance(ProxiedOkHttpClient.class).newCall(new Request.Builder().url(url).build());
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
