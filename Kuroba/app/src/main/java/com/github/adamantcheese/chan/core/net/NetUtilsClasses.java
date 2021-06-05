package com.github.adamantcheese.chan.core.net;

import android.graphics.Bitmap;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.utils.BackgroundUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Timeout;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This class contains a listing of a bunch of other classes that are used in various helper methods.
 */
public class NetUtilsClasses {

    /**
     * Basically the same as OkHttpClient, but has an extra method for constructing a proxied client for a specific call
     */
    public static class OkHttpClientWithUtils
            extends OkHttpClient {

        public OkHttpClientWithUtils(Builder builder) {
            super(builder);
        }

        // This adds an HTTP redirect follower to the base client
        public OkHttpClient getHttpRedirectClient() {
            return newBuilder().addInterceptor(new HttpEquivRefreshInterceptor()).build();
        }
    }

    public static final CacheControl NO_CACHE = new CacheControl.Builder().noStore().build();
    public static final CacheControl ONE_DAY_CACHE = new CacheControl.Builder().maxAge(1, TimeUnit.DAYS).build();

    /**
     * A wrapper sidestepping an OkHttp callback that only returns what we care about.
     *
     * @param <T> The type of the result
     */
    public interface ResponseResult<T> {
        void onFailure(Exception e);

        void onSuccess(T result);
    }

    public interface NoFailResponseResult<T>
            extends ResponseResult<T> {
        @Override
        default void onFailure(Exception e) {}
    }

    /**
     * This class wraps ResponseResult to return it always on the main thread.
     *
     * @param <T> The type that matches the given ResponseResult
     */
    public static class MainThreadResponseResult<T>
            implements ResponseResult<T> {
        private final ResponseResult<T> responseResult;

        public MainThreadResponseResult(ResponseResult<T> responseResult) {
            this.responseResult = responseResult;
        }

        @Override
        public void onFailure(Exception e) {
            BackgroundUtils.runOnMainThread(() -> responseResult.onFailure(e));
        }

        @Override
        public void onSuccess(T result) {
            BackgroundUtils.runOnMainThread(() -> responseResult.onSuccess(result));
        }
    }

    /**
     * This class wraps ResponseResult to return it always on a background thread.
     *
     * @param <T> The type that matches the given ResponseResult
     */
    public static class BackgroundThreadResponseResult<T>
            implements ResponseResult<T> {
        private final ResponseResult<T> responseResult;

        public BackgroundThreadResponseResult(ResponseResult<T> responseResult) {
            this.responseResult = responseResult;
        }

        @Override
        public void onFailure(Exception e) {
            BackgroundUtils.runOnBackgroundThread(() -> responseResult.onFailure(e));
        }

        @Override
        public void onSuccess(T result) {
            BackgroundUtils.runOnBackgroundThread(() -> responseResult.onSuccess(result));
        }
    }

    /**
     * A response wrapper for Bitmaps
     */
    public interface BitmapResult {
        void onBitmapFailure(@NonNull HttpUrl source, Exception e);

        void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache);
    }

    /**
     * Passes through a bitmap result to a delegate that can be set at a later time.
     */
    public static class PassthroughBitmapResult
            implements BitmapResult {
        protected BitmapResult passthrough;

        public void setPassthrough(BitmapResult passthrough) {
            this.passthrough = passthrough;
        }

        @Override
        public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
            passthrough.onBitmapFailure(source, e);
        }

        @Override
        public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
            passthrough.onBitmapSuccess(source, bitmap, fromCache);
        }
    }

    /**
     * A passthrough that crops the bitmap to a specified location (useful for sprite maps)
     */
    public static class CroppingBitmapResult
            extends PassthroughBitmapResult {

        private final Pair<Integer, Integer> originCropCoords;
        private final Pair<Integer, Integer> dims;

        public CroppingBitmapResult(
                Pair<Integer, Integer> originCropCoords, Pair<Integer, Integer> dims
        ) {
            this.originCropCoords = originCropCoords;
            this.dims = dims;
        }

        @SuppressWarnings("ConstantConditions") // all of the pairs will be non-null
        @Override
        public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
            try {
                Bitmap cropped = Bitmap.createBitmap(bitmap,
                        originCropCoords.first,
                        originCropCoords.second,
                        dims.first,
                        dims.second
                );
                passthrough.onBitmapSuccess(source, cropped, fromCache);
            } catch (Exception e) {
                // any exception pass through the original bitmap
                passthrough.onBitmapSuccess(source, bitmap, fromCache);
            }
        }
    }

    /**
     * Converts input I into output O
     *
     * @param <I> the type that will be processed
     * @param <O> the returned object's type
     */
    public interface Converter<O, I> {
        @Nullable O convert(I input)
                throws Exception;
    }

    /**
     * Converts input I into output O, but allows chaining of converters
     * Opposed to Guava's Converter class, this is one-way, as generally these are used for Responses, which cannot be
     * reversibly converted, nor should they be.
     *
     * @param <I> the type that will be processed
     * @param <O> the returned object's type
     */
    public static class ChainConverter<O, I>
            implements Converter<O, I> {
        private final Converter<O, I> next;

        public ChainConverter(Converter<O, I> finalConverter) {
            next = finalConverter;
        }

        /**
         * An intermediate chained converter, so that some intermediate object can be processed
         *
         * @param intermediate The intermediate converter
         * @param <T>          The intermediate object type
         * @return A ChainConverter that can have additional Converters chained into it
         */
        public <T> ChainConverter<O, T> chain(Converter<I, T> intermediate) {
            return new ChainConverter<>(response -> convert(intermediate.convert(response)));
        }

        @Override
        public O convert(I input)
                throws Exception {
            return next.convert(input);
        }
    }

    /**
     * A bunch of common converters, which all process an OkHttp response to some other useful object. These can be chained
     * with the use of ChainConverter above.
     */
    public static final Converter<Buffer, Response> BUFFER_CONVERTER = response -> {
        Buffer b = new Buffer();
        b.writeAll(response.body().source());
        return b;
    };

    public static final Converter<JsonReader, Response> JSON_CONVERTER =
            response -> new JsonReader(new InputStreamReader(response.body().byteStream(), UTF_8));

    public static final Converter<Document, Response> HTML_CONVERTER =
            response -> Jsoup.parse(response.body().byteStream(), null, response.request().url().toString());

    public static final Converter<String, Response> STRING_CONVERTER = response -> response.body().string();

    public static final Converter<Object, Response> EMPTY_CONVERTER = response -> {
        response.body().source().readAll(Okio.blackhole());
        return new Object();
    };

    /**
     * A wrapper over a regular callback that ignores onFailure calls
     */
    public abstract static class IgnoreFailureCallback
            implements Callback {
        public final void onFailure(@NotNull Call call, @NotNull IOException e) {}

        public abstract void onResponse(@NonNull Call call, @NonNull Response response)
                throws IOException;
    }

    public static class HttpCodeException
            extends Exception {
        public int code;

        public HttpCodeException(Response response) {
            this.code = response.code();
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
                    callback.onResponse(this, execute());
                } catch (IOException e) {
                    callback.onFailure(this, e);
                }
            });
        }

        @NotNull
        @Override
        public Response execute() {
            executed = true;
            return new Response.Builder().code(200)
                    .request(request)
                    .body(new EmptyResponseBody())
                    .protocol(Protocol.HTTP_1_1)
                    .message("OK")
                    .build();
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

    public static class EmptyResponseBody
            extends ResponseBody {

        @Override
        public long contentLength() {
            return 0;
        }

        @Override
        public @Nullable MediaType contentType() {
            return null;
        }

        @Override
        public @NotNull BufferedSource source() {
            return new Buffer();
        }
    }
}
