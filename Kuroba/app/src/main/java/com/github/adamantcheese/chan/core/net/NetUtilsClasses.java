package com.github.adamantcheese.chan.core.net;

import android.graphics.Bitmap;
import android.util.JsonReader;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.utils.BackgroundUtils;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStreamReader;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This class contains a listing of a bunch of other classes that are used in various helper methods.
 */
public class NetUtilsClasses {
    /**
     * A wrapper sidestepping an OkHttp callback that only returns what we care about.
     *
     * @param <T>
     */
    public interface ResponseResult<T> {
        void onFailure(Exception e);

        void onSuccess(T result);
    }

    /**
     * Processes a response X into result T
     *
     * @param <T> the returned object's type
     * @param <X> the response's type that will be processed, usually converted first with a ResponseConverter
     */
    public interface ResponseProcessor<T, X> {
        T process(X response)
                throws Exception;
    }

    /**
     * Processes an OkHttp response body into some other more easily digestible form
     *
     * @param <T> The type of the object we want to get out of the body
     */
    public interface ResponseConverter<T> {
        T convert(HttpUrl baseURL, @Nullable ResponseBody body)
                throws Exception;
    }

    /**
     * A response wrapper for Bitmaps, noting if the bitmap was cached or not
     */
    public interface BitmapResult {
        void onBitmapFailure(@NonNull HttpUrl source, Exception e);

        void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap);
    }

    /**
     * A standard JSON response processor.
     *
     * @param <T> The returned object's type
     */
    public abstract static class JSONProcessor<T>
            implements ResponseProcessor<T, JsonReader> {

        @Override
        public abstract T process(JsonReader response)
                throws Exception;
    }

    /**
     * A standard HTML response processor.
     *
     * @param <T> The returned object's type
     */
    public abstract static class HTMLProcessor<T>
            implements ResponseProcessor<T, Document> {
        @Override
        public abstract T process(Document response)
                throws Exception;
    }

    /**
     * A standard JSON response converter, which outputs a JSONReader.
     */
    public static class JSONConverter
            implements ResponseConverter<JsonReader> {

        @Override
        public JsonReader convert(HttpUrl baseURL, @Nullable ResponseBody body) {
            return new JsonReader(new InputStreamReader(body.byteStream(), UTF_8));
        }
    }

    /**
     * A standard HTML response convertor, which outputs a Jsoup Document.
     */
    public static class HTMLConverter
            implements ResponseConverter<Document> {

        @Override
        public Document convert(HttpUrl baseURL, @Nullable ResponseBody body)
                throws Exception {
            return Jsoup.parse(body.byteStream(), null, baseURL.toString());
        }
    }

    /**
     * A wrapper over a regular callback that ignores onFailure calls
     */
    public abstract static class IgnoreFailureCallback
            implements Callback {
        public final void onFailure(@NotNull Call call, @NotNull IOException e) {}

        public abstract void onResponse(@NonNull Call call, @NonNull Response response) throws IOException;
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

    /**
     * A useful implementation of a regular Call that will never fail, but always returns an empty body.
     */
    public static class NullCall
            implements Call {

        private final Request request;
        private boolean executed;

        public NullCall(HttpUrl url) {
            request = new Request.Builder().url(url).build();
        }

        @Override
        public void cancel() {}

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        @NotNull
        @Override
        public Call clone() {
            return new NullCall(request.url());
        }

        @Override
        public void enqueue(@NotNull Callback callback) {
            executed = true;
            BackgroundUtils.runOnBackgroundThread(() -> { // to emulate an actual call coming from a background thread
                try {
                    callback.onResponse(
                            this,
                            new Response.Builder().code(200)
                                    .request(request)
                                    .protocol(Protocol.HTTP_1_1)
                                    .message("OK")
                                    .build()
                    );
                } catch (IOException e) {
                    callback.onFailure(this, e);
                }
            });
        }

        @NotNull
        @Override
        public Response execute() {
            executed = true;
            return new Response.Builder().code(200).message("OK").build();
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @NotNull
        @Override
        public Request request() {
            return request;
        }

        @NotNull
        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }

    /**
     * An LRU cache for Bitmap objects, used for storing thumbnails and the such
     */
    static class BitmapLruCache
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
