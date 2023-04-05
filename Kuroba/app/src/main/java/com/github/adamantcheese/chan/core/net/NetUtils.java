package com.github.adamantcheese.chan.core.net;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.net.DnsSelector.Mode.IPV4_ONLY;
import static com.github.adamantcheese.chan.core.net.DnsSelector.Mode.SYSTEM;
import static com.github.adamantcheese.chan.core.net.NetUtilsClasses.*;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static java.lang.Runtime.getRuntime;
import static okhttp3.Protocol.HTTP_1_1;
import static okhttp3.Protocol.HTTP_2;

import android.graphics.Bitmap;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;
import androidx.core.util.Pair;

import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.utils.*;
import com.google.common.io.Files;

import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import kotlin.Triple;
import kotlin.io.FilesKt;
import okhttp3.*;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.logging.HttpLoggingInterceptor;

public class NetUtils {
    public static final String USER_AGENT = BuildConfigUtils.VERSION;

    public static final int MB = 1024 * 1024;
    // The OkHttpClient installed cache, used for all requests
    private static final Cache OK_HTTP_CACHE = new Cache(new File(getCacheDir(), "okhttp"),
            ChanSettings.autoLoadThreadImages.get()
                    ? (long) ChanSettings.fileCacheSize.get() * 2 * MB
                    : (long) ChanSettings.fileCacheSize.get() * MB
    );

    public static final OkHttpClientWithUtils applicationClient = new OkHttpClientWithUtils(new OkHttpClient.Builder()
            .cache(OK_HTTP_CACHE)
            .protocols(ChanSettings.okHttpAllowHttp2.get()
                    ? Arrays.asList(HTTP_2, HTTP_1_1)
                    : Collections.singletonList(HTTP_1_1))
            .dns(new DnsSelector(ChanSettings.okHttpAllowIpv6.get() ? SYSTEM : IPV4_ONLY))
            .proxy(ChanSettings.proxy)
            .cookieJar(new WebviewSyncCookieManager(new PersistentCookieJar(new SetCookieCache(),
                    new SharedPrefsCookiePersistor(getAppContext())
            )))
            .addNetworkInterceptor(chain -> {
                // interceptor to add the User-Agent for all requests
                Request request = chain.request().newBuilder().header("User-Agent", USER_AGENT).build();
                return chain.proceed(request);
            }));

    /**
     * @param processor The cookie processor to use for this interceptor
     * @return an interceptor that will deal with cookies when attached to a client (use newBuilder)
     */
    public static Interceptor createCookieParsingInterceptor(NetUtilsClasses.CookieProcessor processor) {
        return chain -> {
            Response r = chain.proceed(chain.request());
            List<Cookie> cookieList = Cookie.parseAll(chain.request().url(), r.headers());
            List<Cookie> newList = new ArrayList<>();
            for (Cookie c : cookieList) {
                newList.addAll(processor.process(c));
            }
            Headers.Builder h = new Headers.Builder().addAll(r.headers());
            h.removeAll("Set-Cookie");
            for (Cookie c : newList) {
                h.add("Set-Cookie", c.toString());
            }
            NetUtils.applicationClient.cookieJar().saveFromResponse(r.request().url(), newList);
            return r.newBuilder().headers(h.build()).build();
        };
    }

    public static void clearAllCookies(HttpUrl url) {
        ((WebviewSyncCookieManager) NetUtils.applicationClient.cookieJar()).clearCookiesForUrl(url, null);
    }

    public static void clearSpecificCookies(HttpUrl url, List<String> cookieNames) {
        ((WebviewSyncCookieManager) NetUtils.applicationClient.cookieJar()).clearCookiesForUrl(url, cookieNames);
    }

    public static void loadWebviewCookies(HttpUrl url) {
        ((WebviewSyncCookieManager) NetUtils.applicationClient.cookieJar()).loadWebviewCookiesIntoJar(url);
    }

    public static Cookie changeCookieDomain(Cookie c, String newDomain) {
        Cookie.Builder builder = c.newBuilder();
        if (c.hostOnly()) {
            builder.hostOnlyDomain(newDomain);
        } else {
            builder.domain(newDomain);
        }
        return builder.build();
    }

    // max 1/4 the maximum Dalvik runtime size
    // by default, the max heap size of stock android is 512MiB; keep that in mind if you change things here
    // url, width, height request -> bitmap
    private static final LruCache<Triple<HttpUrl, Integer, Integer>, Bitmap> imageCache =
            new LruCache<Triple<HttpUrl, Integer, Integer>, Bitmap>((int) (getRuntime().maxMemory() / 4)) {
                @Override
                protected int sizeOf(@NonNull Triple<HttpUrl, Integer, Integer> key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    public static void clearImageCache() {
        imageCache.evictAll();
    }

    public static Call makeHttpCall(HttpCall<?> httpCall) {
        return makeHttpCall(httpCall, Collections.emptyList(), null, true);
    }

    public static Call makeHttpCall(HttpCall<?> httpCall, boolean enqueue) {
        return makeHttpCall(httpCall, Collections.emptyList(), null, enqueue);
    }

    public static Call makeHttpCall(HttpCall<?> httpCall, List<Interceptor> extraInterceptors) {
        return makeHttpCall(httpCall, extraInterceptors, null, true);
    }

    public static Call makeHttpCall(
            HttpCall<?> httpCall,
            List<Interceptor> extraInterceptors,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener,
            boolean enqueue
    ) {
        Request.Builder requestBuilder = new Request.Builder();
        httpCall.setup(requestBuilder, progressListener);
        OkHttpClient client = applicationClient; // default to this client
        if (ChanSettings.verboseLogs.get()) {
            HttpLoggingInterceptor debuggingInterceptor = new HttpLoggingInterceptor();
            debuggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            client = applicationClient.newBuilder().addNetworkInterceptor(debuggingInterceptor).build();
        }
        for (Interceptor i : extraInterceptors) {
            client = client.newBuilder().addInterceptor(i).build();
        }
        Call call = client.newCall(requestBuilder.build());
        if (enqueue) {
            call.enqueue(httpCall);
        }
        return call;
    }

    /**
     * Simple wrapper to check if a url has been cached by OkHttp
     *
     * @param url The url to check
     * @return true if the url has a cached response
     */
    @SuppressWarnings("KotlinInternalInJava")
    public static boolean isCached(HttpUrl url) {
        return OK_HTTP_CACHE.getCache$okhttp().getLruEntries$okhttp().containsKey(Cache.key(url));
    }

    /**
     * Get a raw, cached response.
     * Files will be deleted after an hour, when the application goes into the background!
     *
     * @param url              the url to download as a file
     * @param filename         the name of the cached file you want to make
     * @param fileExt          the extension for the cached file
     * @param result           the result callback
     * @param progressListener an optional progress listener
     * @return An enqueued file call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Call makeFileRequest(
            @NonNull final HttpUrl url,
            @NonNull final String filename,
            @NonNull final String fileExt,
            @NonNull final ResponseResult<File> result,
            @Nullable final ProgressResponseBody.ProgressListener progressListener
    ) {
        return makeRequest(applicationClient.getHttpRedirectClient(),
                url,
                new NetUtilsClasses.TempFileConverter(filename, fileExt),
                new MainThreadResponseResult<>(result),
                progressListener,
                ONE_DAY_CACHE,
                0
        );
    }

    /**
     * Request a bitmap without resizing.
     *
     * @param url    The request URL. If null, null will be returned.
     * @param result The callback for this call.
     * @return An enqueued bitmap call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Call makeBitmapRequest(
            final HttpUrl url, @NonNull final BitmapResult result
    ) {
        return makeBitmapRequest(url, result, 0, 0, null);
    }

    /**
     * Request a bitmap without resizing, with timeout
     *
     * @param url       The request URL. If null, null will be returned.
     * @param result    The callback for this call.
     * @param timeoutMs Optional timeout value, in milliseconds (-1 for no timeout)
     * @return An enqueued bitmap call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Call makeBitmapRequest(
            final HttpUrl url, @NonNull final BitmapResult result, final int timeoutMs
    ) {
        Triple<Call, Callback, Runnable> ret = makeBitmapRequest(url, result, 0, 0, timeoutMs, null, true, true);
        return ret == null ? null : ret.getFirst();
    }

    /**
     * Request a bitmap with resizing.
     *
     * @param url    The request URL. If null, null will be returned.
     * @param result The callback for this call.
     * @param width  The explicit width of the result
     * @param height The explicit height of the result
     * @return An enqueued bitmap call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Call makeBitmapRequest(
            final HttpUrl url,
            @NonNull final BitmapResult result,
            final int width,
            final int height,
            @Nullable ProgressResponseBody.ProgressListener progressListener
    ) {
        Triple<Call, Callback, Runnable> ret =
                makeBitmapRequest(url, result, width, height, -1, progressListener, true, true);
        return ret == null ? null : ret.getFirst();
    }

    /**
     * Request a bitmap with resizing.
     *
     * @param url        The request URL. If null, null will be returned.
     * @param result     The callback for this call. If null, null will be returned.
     * @param width      The requested width of the result
     * @param height     The requested height of the result
     * @param timeoutMs  Optional timeout value, in milliseconds (-1 for no timeout)
     * @param enqueue    Should this be enqueued or not
     * @param mainThread Should this result be run on the main thread or not (most wrappers do)
     * @return An enqueued bitmap call.
     */
    public static Triple<Call, Callback, Runnable> makeBitmapRequest(
            final HttpUrl url,
            final BitmapResult result,
            final int width,
            final int height,
            final int timeoutMs,
            @Nullable final ProgressResponseBody.ProgressListener progressListener,
            boolean enqueue,
            boolean mainThread
    ) {
        BitmapRunnable runnable = new BitmapRunnable(url, null, null, result);
        if (url == null || result == null) return null;
        Bitmap cachedBitmap = imageCache.get(new Triple<>(url, width, height));
        if (cachedBitmap != null) {
            runnable.setBitmap(cachedBitmap);
            runnable.setFromCache(true);
            runOrEnqueueOnMainThread(runnable, mainThread);
            return null;
        }
        OkHttpClient client = applicationClient.getHttpRedirectClient();
        if (timeoutMs != -1) {
            client = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build();
        }
        client = client.newBuilder().addNetworkInterceptor(chain -> {
            Response originalResponse = chain.proceed(chain.request());
            return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse, progressListener))
                    .build();
        }).build();
        Call call = client.newCall(new Request.Builder()
                .url(url)
                .addHeader("Referer", url.toString())
                .cacheControl(ONE_YEAR_CACHE)
                .build());
        Callback callback = new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (!isCancelledException(e)) {
                    Logger.w("NetUtils", "Error loading bitmap from " + url);
                    runnable.setException(e);
                    runOrEnqueueOnMainThread(runnable, mainThread);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        runnable.setException(new NullPointerException("No response data"));
                        runOrEnqueueOnMainThread(runnable, mainThread);
                        return;
                    }

                    if (!response.isSuccessful()) {
                        runnable.setException(new HttpCodeException(response));
                        runOrEnqueueOnMainThread(runnable, mainThread);
                        return;
                    }

                    if ("webm".equalsIgnoreCase(Files.getFileExtension(url.toString()))) {
                        File tempFile = new File(getCacheDir(), UUID.randomUUID().toString());
                        if (!tempFile.createNewFile()) {
                            tempFile.delete();
                            runnable.setException(new IOException("Failed to create temp file for decode."));
                            runOrEnqueueOnMainThread(runnable, mainThread);
                        }
                        FilesKt.writeBytes(tempFile, body.bytes());
                        Bitmap b = BitmapUtils.decodeFilePreviewImage(tempFile, 0, 0, mainThread ? null : bitmap -> {
                            //noinspection ResultOfMethodCallIgnored
                            tempFile.delete();
                            checkBitmap(runnable, url, width, height, bitmap, false);
                        }, false);
                        if (b != null) {
                            //noinspection ResultOfMethodCallIgnored
                            tempFile.delete();
                            checkBitmap(runnable, url, width, height, b, true);
                        }
                    } else {
                        ExceptionCatchingInputStream wrappedStream =
                                new ExceptionCatchingInputStream(body.byteStream());
                        Bitmap b = BitmapUtils.decode(wrappedStream, width, height);
                        if (wrappedStream.getException() != null) {
                            runnable.setException(wrappedStream.getException());
                            runOrEnqueueOnMainThread(runnable, mainThread);
                            return;
                        }
                        checkBitmap(runnable, url, width, height, b, mainThread);
                    }
                } catch (Exception e) {
                    runnable.setException(e);
                    runOrEnqueueOnMainThread(runnable, mainThread);
                } catch (OutOfMemoryError e) {
                    getRuntime().gc();
                    runnable.setException(new IOException(e));
                    runOrEnqueueOnMainThread(runnable, mainThread);
                }
            }
        };
        if (enqueue) {
            call.enqueue(callback);
        }
        return new Triple<>(call, callback, runnable);
    }

    private static void checkBitmap(
            BitmapRunnable runnable,
            HttpUrl url,
            int requestWidth,
            int requestedHeight,
            Bitmap result,
            boolean mainThread
    ) {
        if (result == null) {
            runnable.setException(new NullPointerException("Bitmap returned is null"));
        } else {
            result.prepareToDraw();
            imageCache.put(new Triple<>(url, requestWidth, requestedHeight), result);
            runnable.setBitmap(result);
        }
        runOrEnqueueOnMainThread(runnable, mainThread);
    }

    private static void runOrEnqueueOnMainThread(BitmapRunnable runnable, boolean mainThread) {
        if (mainThread) {
            BackgroundUtils.runOnMainThread(runnable);
        } else {
            runnable.run();
        }
    }

    private static class BitmapRunnable
            implements Runnable {
        private final HttpUrl url;
        private Exception exception;
        private Bitmap bitmap;
        private final BitmapResult result;
        private boolean fromCache = false;

        public BitmapRunnable(
                final HttpUrl url, Exception exception, Bitmap bitmap, BitmapResult result
        ) {
            this.url = url;
            this.exception = exception;
            this.bitmap = bitmap;
            this.result = result;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception e) {
            this.exception = e;
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        public void setFromCache(boolean fromCache) {
            this.fromCache = fromCache;
        }

        @Override
        public void run() {
            if (exception != null) {
                if (isCancelledException(exception)) return;
                result.onBitmapFailure(url, exception);
            } else if (bitmap != null) {
                result.onBitmapSuccess(url, bitmap, fromCache);
            }
        }
    }

    /**
     * Request some JSON, no timeout.
     *
     * @param url          The request URL.
     * @param result       The callback for this call.
     * @param cacheControl Set cache parameters for this request
     * @param <T>          Your type
     * @return An enqueued JSON call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final ResponseResult<T> result,
            @NonNull final Converter<T, JsonReader> reader,
            @Nullable final CacheControl cacheControl
    ) {
        return makeJsonRequest(url, result, reader, cacheControl, null);
    }

    /**
     * Request some JSON, with a timeout. Optional progress listener.
     *
     * @param url              The request URL.
     * @param result           The callback for this call.
     * @param cacheControl     Set cache parameters for this request
     * @param progressListener Optional progress listener.
     * @param timeoutMs        Optional timeout in milliseconds
     * @param <T>              Your type
     * @return An enqueued JSON call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final ResponseResult<T> result,
            @NonNull final Converter<T, JsonReader> reader,
            @Nullable final CacheControl cacheControl,
            @Nullable final ProgressResponseBody.ProgressListener progressListener,
            int timeoutMs
    ) {
        return makeRequest(applicationClient,
                url,
                new ChainConverter<>(reader).chain(JSON_CONVERTER),
                result,
                progressListener,
                cacheControl,
                timeoutMs
        );
    }

    /**
     * Request some JSON, no timeout. Optional progress listener.
     *
     * @param url              The request URL.
     * @param result           The callback for this call
     * @param cacheControl     Set cache parameters for this request
     * @param progressListener Optional progress listener.
     * @param <T>              Your type
     * @return An enqueued JSON call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final ResponseResult<T> result,
            @NonNull final Converter<T, JsonReader> reader,
            @Nullable final CacheControl cacheControl,
            @Nullable final ProgressResponseBody.ProgressListener progressListener
    ) {
        return makeJsonRequest(url, result, reader, cacheControl, progressListener, 0);
    }

    /**
     * Request some HTML, no timeout.
     *
     * @param url          The request URL.
     * @param result       The callback for this call.
     * @param reader       The reader that will process the response into your result
     * @param cacheControl Set cache parameters for this request
     * @param <T>          Your type
     * @return An enqueued HTML call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    @SuppressWarnings("UnusedReturnValue")
    public static <T> Call makeHTMLRequest(
            @NonNull final HttpUrl url,
            @NonNull final ResponseResult<T> result,
            @NonNull final Converter<T, Document> reader,
            @Nullable final CacheControl cacheControl
    ) {
        return makeRequest(applicationClient,
                url,
                new ChainConverter<>(reader).chain(HTML_CONVERTER),
                result,
                null,
                cacheControl,
                0
        );
    }

    /**
     * Request something, no timeout.
     *
     * @param url          The request URL.
     * @param converter    The converter for the response.
     * @param result       The callback for this call.
     * @param cacheControl Set cache parameters for this request
     * @param <T>          Your result type, the something you're requesting
     * @return An enequeued call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeRequest(
            @NonNull final OkHttpClient client,
            @NonNull final HttpUrl url,
            @NonNull final Converter<T, Response> converter,
            @NonNull final ResponseResult<T> result,
            @Nullable final ProgressResponseBody.ProgressListener progressListener,
            @Nullable final CacheControl cacheControl
    ) {
        return makeRequest(client, url, converter, result, progressListener, cacheControl, 0);
    }

    /**
     * Request something, timeout.
     *
     * @param url          The request URL.
     * @param converter    The converter for the response.
     * @param result       The callback for this call.
     * @param cacheControl Set cache parameters for this request
     * @param timeoutMs    Optional timeout in milliseconds
     * @param <T>          Your result type, the something you're requesting
     * @return An enequeued call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    private static <T> Call makeRequest(
            @NonNull final OkHttpClient client,
            @NonNull final HttpUrl url,
            @NonNull final Converter<T, Response> converter,
            @NonNull final ResponseResult<T> result,
            @Nullable final ProgressResponseBody.ProgressListener progressListener,
            @Nullable final CacheControl cacheControl,
            int timeoutMs
    ) {
        return makeCall(client, url, converter, result, progressListener, cacheControl, null, timeoutMs, true).first;
    }

    /**
     * This is the mothership of this class mostly, it does all the heavy lifting for you once provided the proper stuff
     * Generally don't use this! Use one of the wrapper methods instead. This class ensures that all responses are properly
     * closed.
     *
     * @param url              The request URL.
     * @param converter        The converter that will convert the response into a form the reader can process.
     * @param result           The callback for this call.
     * @param progressListener An optional progress listener for this response
     * @param <T>              Your result type
     * @param cacheControl     Set cache parameters for this request
     * @param extraHeaders     Extra headers for this request
     * @param timeoutMs        Optional timeout in milliseconds
     * @param enqueue          whether or not to enqueue this call as a step
     * @return An optionally enqueued call along with the callback it is associated with. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Pair<Call, Callback> makeCall(
            @NonNull OkHttpClient client,
            @NonNull final HttpUrl url,
            @NonNull final Converter<T, Response> converter,
            @NonNull final ResponseResult<T> result,
            @Nullable final ProgressResponseBody.ProgressListener progressListener,
            @Nullable final CacheControl cacheControl,
            @Nullable final Headers extraHeaders,
            int timeoutMs,
            boolean enqueue
    ) {
        OkHttpClient.Builder clientBuilder = client.newBuilder();
        clientBuilder.callTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        clientBuilder.addNetworkInterceptor(chain -> {
            Response originalResponse = chain.proceed(chain.request());
            return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse, progressListener))
                    .build();
        });
        Request.Builder builder = new Request.Builder().url(url).addHeader("Referer", url.toString());
        if (cacheControl != null) {
            builder.cacheControl(cacheControl);
        }
        if (extraHeaders != null) {
            builder.headers(extraHeaders);
        }
        Call call = clientBuilder.build().newCall(builder.build());
        Callback callback = new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                result.onFailure(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (Response r = response) {
                    if (!response.isSuccessful()) {
                        result.onFailure(new HttpCodeException(response));
                        return;
                    }

                    T read = converter.convert(r);
                    if (read == null) throw new NullPointerException("Convert returned null!");
                    result.onSuccess(read);
                } catch (Exception e) {
                    result.onFailure(e);
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
            @NonNull final HttpUrl url, @NonNull final ResponseResult<Headers> result
    ) {
        Call call =
                applicationClient.newCall(new Request.Builder().url(url).head().cacheControl(ONE_YEAR_CACHE).build());
        BackgroundThreadResponseResult<Headers> wrap = new BackgroundThreadResponseResult<>(result);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                wrap.onFailure(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    wrap.onFailure(new HttpCodeException(response));
                } else {
                    wrap.onSuccess(response.headers());
                }
                response.close();
            }
        });
        return call;
    }

    public static boolean isCancelledException(Exception e) {
        return e instanceof StreamResetException || "Canceled".equals(e.getMessage());
    }
}
