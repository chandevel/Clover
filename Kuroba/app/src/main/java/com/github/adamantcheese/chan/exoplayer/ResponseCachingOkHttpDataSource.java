/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.adamantcheese.chan.exoplayer;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

import static java.lang.Math.min;

/**
 * An {@link HttpDataSource} that delegates to Square's {@link OkHttpClient}.
 * <p>
 * This has been modified from ExoPlayer's OkHttpDataSource to keep track of and reuse Response objects, so that
 * if the provided OkHttpClient instance has caching enabled, the entire stream will be cached using that cache.
 * ExoPlayer's cache need not apply in this case.
 * <p>
 * Note: HTTP request headers will be set using all parameters passed via (in order of decreasing
 * priority) the {@code dataSpec}, {@link #setRequestProperty} and the default parameters used to
 * construct the instance.
 */
public class ResponseCachingOkHttpDataSource
        extends BaseDataSource
        implements HttpDataSource {

    static {
        ExoPlayerLibraryInfo.registerModule("goog.exo.okhttp.mod");
    }

    /**
     * {@link DataSource.Factory} for {@link ResponseCachingOkHttpDataSource} instances.
     */
    public static final class Factory
            extends HttpDataSource.BaseFactory {

        private final OkHttpClient client;

        /**
         * Creates an instance.
         *
         * @param client A {@link OkHttpClient} for use by the sources created by the factory.
         */
        public Factory(@NonNull OkHttpClient client) {
            this.client = client;
        }

        @Override
        protected HttpDataSource createDataSourceInternal(@NonNull RequestProperties defaultRequestProperties) {
            return new ResponseCachingOkHttpDataSource(client, defaultRequestProperties);
        }
    }

    // Maps data specs to existing responses
    private static final Map<Uri, RequestInfo> cachedResponses = new HashMap<>();

    private static class RequestInfo {
        int referenceCount;
        Response response;
        BufferedSource responseStream;

        long bytesToSkip;
        long bytesToRead;

        long bytesRead;
        long bytesSkipped;

        public RequestInfo(Response response) {
            referenceCount = 1;
            this.response = response;
            responseStream = response.body().source();

            bytesToRead = response.body().contentLength();
            bytesToSkip = 0;

            bytesRead = 0;
            bytesSkipped = 0;
        }
    }

    private final OkHttpClient client;

    private final RequestProperties requestProperties;
    private final RequestProperties defaultRequestProperties;

    @Nullable
    private DataSpec dataSpec;

    private boolean opened;

    private ResponseCachingOkHttpDataSource(
            @NonNull OkHttpClient client, @Nullable RequestProperties defaultRequestProperties
    ) {
        super(/* isNetwork= */ true);
        this.client = Assertions.checkNotNull(client);
        this.defaultRequestProperties =
                defaultRequestProperties == null ? new RequestProperties() : defaultRequestProperties;
        this.requestProperties = new RequestProperties();
    }

    @Override
    @Nullable
    public Uri getUri() {
        return dataSpec == null ? null : dataSpec.uri;
    }

    @Override
    public int getResponseCode() {
        return getInfoForDataSpec() == null ? -1 : getInfoForDataSpec().response.code();
    }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return getInfoForDataSpec() == null
                ? Collections.emptyMap()
                : getInfoForDataSpec().response.headers().toMultimap();
    }

    @Override
    public void setRequestProperty(@NonNull String name, @NonNull String value) {
        Assertions.checkNotNull(name);
        Assertions.checkNotNull(value);
        requestProperties.set(name, value);
    }

    @Override
    public void clearRequestProperty(@NonNull String name) {
        Assertions.checkNotNull(name);
        requestProperties.remove(name);
    }

    @Override
    public void clearAllRequestProperties() {
        requestProperties.clear();
    }

    @Override
    public long open(@NonNull DataSpec dataSpec)
            throws HttpDataSourceException {
        this.dataSpec = dataSpec;
        transferInitializing(dataSpec);

        RequestInfo cached = getInfoForDataSpec();
        Response response = cached == null ? null : cached.response;
        try {
            if (response == null) {
                response = client.newCall(makeRequest(dataSpec)).execute();
                Assertions.checkNotNull(response.body());
            }

            if (cached != null) {
                cached.referenceCount += 1;
            } else {
                cached = new RequestInfo(response);
            }
            cached.bytesRead = 0;
            cached.bytesSkipped = 0;
            cachedResponses.put(dataSpec.uri, cached);
        } catch (IOException e) {
            @Nullable
            String message = e.getMessage();
            if (message != null && Ascii.toLowerCase(message).matches("cleartext communication.*not permitted.*")) {
                throw new CleartextNotPermittedException(e, dataSpec);
            }
            throw new HttpDataSourceException("Unable to connect", e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        // Check for a valid response code.
        if (!response.isSuccessful()) {
            byte[] errorResponseBody;
            try {
                errorResponseBody = Util.toByteArray(Assertions.checkNotNull(response.body().byteStream()));
            } catch (IOException e) {
                errorResponseBody = Util.EMPTY_BYTE_ARRAY;
            }
            Map<String, List<String>> headers = response.headers().toMultimap();
            closeConnectionQuietly();
            InvalidResponseCodeException exception = new InvalidResponseCodeException(response.code(),
                    response.message(),
                    headers,
                    dataSpec,
                    errorResponseBody
            );
            if (response.code() == 416) {
                exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
            }
            throw exception;
        }

        // If we requested a range starting from a non-zero position and received a 200 rather than a
        // 206, then the server does not support partial requests. We'll need to manually skip to the
        // requested position.
        cached.bytesToSkip = cached.response.code() == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

        // Determine the length of the data to be read, after skipping.
        if (dataSpec.length != C.LENGTH_UNSET) {
            cached.bytesToRead = dataSpec.length;
        } else {
            long contentLength = cached.response.body().contentLength();
            cached.bytesToRead = contentLength != -1 ? (contentLength - cached.bytesToSkip) : C.LENGTH_UNSET;
        }

        opened = true;
        transferStarted(dataSpec);

        return cached.bytesToRead;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int readLength)
            throws HttpDataSourceException {
        try {
            RequestInfo info = getInfoForDataSpec();
            if (info == null) {
                throw new IOException("Nothing to read from");
            }
            //skipInternal(); original method
            int readLen = (int) (info.bytesToSkip - info.bytesSkipped);
            info.responseStream.skip(readLen);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException();
            }
            if (info.responseStream.exhausted() && readLen > 0) {
                // End of stream reached having not read sufficient data.
                throw new EOFException();
            }
            info.bytesSkipped += readLen;
            bytesTransferred(readLen);

            //return readInternal(buffer, offset, readLength); original method
            if (readLength == 0) {
                return 0;
            }
            if (info.bytesToRead > 0) {
                long bytesRemaining = info.bytesToRead - info.bytesRead;
                if (bytesRemaining == 0) {
                    return C.RESULT_END_OF_INPUT;
                }
                readLength = (int) min(readLength, bytesRemaining);
            }

            int read = info.responseStream.read(buffer, offset, readLength);
            if (read == -1) {
                if (info.bytesToRead > 0) {
                    // End of stream reached having not read sufficient data.
                    throw new EOFException();
                }
                return C.RESULT_END_OF_INPUT;
            }

            info.bytesRead += read;
            bytesTransferred(read);
            return read;
        } catch (IOException e) {
            throw new HttpDataSourceException(e, Assertions.checkNotNull(dataSpec), HttpDataSourceException.TYPE_READ);
        }
    }

    @Override
    public void close() {
        if (opened) {
            opened = false;
            transferEnded();
            closeConnectionQuietly();
        }
    }

    /**
     * Establishes a connection.
     */
    private Request makeRequest(DataSpec dataSpec)
            throws HttpDataSourceException {
        HttpUrl url = HttpUrl.parse(dataSpec.uri.toString());
        if (url == null) {
            throw new HttpDataSourceException("Malformed URL", dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        Request.Builder builder = new Request.Builder().url(url);

        Map<String, String> headers = new HashMap<>();
        headers.putAll(defaultRequestProperties.getSnapshot());
        headers.putAll(requestProperties.getSnapshot());
        headers.putAll(dataSpec.httpRequestHeaders);
        builder.headers(Headers.of(headers));

        if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
            builder.addHeader("Accept-Encoding", "identity");
        }

        return builder.build();
    }

    /**
     * Closes the current connection quietly, if there is one.
     */
    private void closeConnectionQuietly() {
        if (dataSpec == null) return;
        RequestInfo cached = cachedResponses.remove(dataSpec.uri);
        if (cached != null) {

            if (cached.referenceCount > 1) {
                cached.referenceCount -= 1;
                cachedResponses.put(dataSpec.uri, cached);
            } else {
                try {
                    cached.responseStream.close();
                    cached.response.close();
                } catch (Exception ignored) {}
            }
        }
    }

    @Nullable
    private RequestInfo getInfoForDataSpec() {
        if (dataSpec == null) return null;
        return cachedResponses.get(dataSpec.uri);
    }
}
