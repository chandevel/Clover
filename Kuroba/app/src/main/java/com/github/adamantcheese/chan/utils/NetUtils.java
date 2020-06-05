package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.JsonReader;
import android.util.MalformedJsonException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.di.NetModule.ProxiedOkHttpClient;
import com.github.adamantcheese.chan.core.net.BitmapLruImageCache;
import com.github.adamantcheese.chan.core.site.SiteRequestModifier;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static java.nio.charset.StandardCharsets.UTF_8;

public class NetUtils {
    private static final String TAG = "NetUtils";

    public static void makeHttpCall(
            HttpCall httpCall, HttpCall.HttpCallback<? extends HttpCall> callback
    ) {
        makeHttpCall(httpCall, callback, null);
    }

    public static void makeHttpCall(
            HttpCall httpCall,
            HttpCall.HttpCallback<? extends HttpCall> callback,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        httpCall.setCallback(callback);

        Request.Builder requestBuilder = new Request.Builder();

        httpCall.setup(requestBuilder, progressListener);

        if (httpCall.getSite() != null) {
            final SiteRequestModifier siteRequestModifier = httpCall.getSite().requestModifier();
            if (siteRequestModifier != null) {
                siteRequestModifier.modifyHttpCall(httpCall, requestBuilder);
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
        Bitmap errorBitmap = BitmapFactory.decodeResource(getRes(), R.drawable.error_icon);
        Bitmap cachedBitmap = instance(BitmapLruImageCache.class).getBitmap(url.toString());
        if (cachedBitmap != null) {
            result.onBitmapSuccess(cachedBitmap);
            return null;
        }
        Call call = instance(ProxiedOkHttpClient.class).newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Logger.e(TAG, "Error loading bitmap from " + url.toString());
                result.onBitmapFailure(errorBitmap, e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() != 200) {
                    result.onBitmapFailure(errorBitmap, new HttpCodeException(response.code()));
                    response.close();
                    return;
                }

                try (ResponseBody body = response.body()) {
                    Bitmap bitmap = BitmapUtils.decode(body.bytes(), width, height);
                    instance(BitmapLruImageCache.class).putBitmap(url.toString(), bitmap);
                    result.onBitmapSuccess(bitmap);
                } catch (Exception e) {
                    result.onBitmapFailure(errorBitmap, e);
                }
            }
        });
        return call;
    }

    public interface BitmapResult {
        void onBitmapFailure(Bitmap errormap, Exception e);

        void onBitmapSuccess(Bitmap bitmap);
    }

    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url, @NonNull final JsonResult<T> result, @NonNull final JsonParser<T> parser
    ) {
        Call call = instance(ProxiedOkHttpClient.class).newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Logger.e(TAG, "Error with request: ", e);
                result.onJsonFailure(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() != 200) {
                    result.onJsonFailure(new HttpCodeException(response.code()));
                    response.close();
                    return;
                }

                try (JsonReader jsonReader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(response.body()
                        .bytes()), UTF_8))) {
                    T read = parser.parse(jsonReader);
                    if (read != null) {
                        result.onJsonSuccess(read);
                    } else {
                        result.onJsonFailure(new MalformedJsonException("Json parse returned null object"));
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing JSON: ", e);
                    result.onJsonFailure(new MalformedJsonException(e.getMessage()));
                }
                response.close();
            }
        });
        return call;
    }

    public static <T> T makeJsonRequestSync(@NonNull final HttpUrl url, @NonNull final JsonParser<T> parser) {
        Call call = instance(ProxiedOkHttpClient.class).newCall(new Request.Builder().url(url).build());
        try (Response response = call.execute()) {
            if (response.code() != 200) {
                Logger.e(TAG, "Response code was not OK:" + response.code());
                response.close();
                return null;
            }

            try (JsonReader jsonReader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(response.body()
                    .bytes()), UTF_8))) {
                return parser.parse(jsonReader);
            } catch (Exception e) {
                Logger.e(TAG, "Error parsing JSON: ", e);
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

            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() != 200) {
                    result.onHTMLFailure(new HttpCodeException(response.code()));
                    response.close();
                    return;
                }

                try (ByteArrayInputStream baos = new ByteArrayInputStream(response.body().bytes())) {
                    Document document = Jsoup.parse(baos, null, url.toString());

                    T read = reader.read(document);
                    result.onHTMLSuccess(read);
                } catch (Exception e) {
                    result.onHTMLFailure(e);
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
}
