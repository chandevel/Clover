package com.github.adamantcheese.chan.core.net;

import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

/**
 * Used for getting the progress of a body when receiving it from the network.
 */
public class ProgressResponseBody
        extends ResponseBody {

    private final HttpUrl sourceUrl;
    private final ResponseBody responseBody;
    @Nullable
    private final ProgressListener progressListener;
    private BufferedSource bufferedSource;

    ProgressResponseBody(Response response, @Nullable ProgressListener progressListener) {
        this.sourceUrl = response.request().url();
        this.responseBody = response.body();
        this.progressListener = progressListener;
    }

    @Override
    public MediaType contentType() {
        return responseBody.contentType();
    }

    @Override
    public long contentLength() {
        return responseBody.contentLength();
    }

    @Override
    public BufferedSource source() {
        if (bufferedSource == null) {
            bufferedSource = Okio.buffer(source(responseBody.source()));
        }
        return bufferedSource;
    }

    private Source source(Source source) {
        return new ForwardingSource(source) {
            long totalBytesRead = 0L;

            @Override
            public long read(Buffer sink, long byteCount)
                    throws IOException {
                long bytesRead = super.read(sink, byteCount);
                boolean firstRead = totalBytesRead == 0L;
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                if (progressListener != null) {
                    progressListener.onDownloadProgress(sourceUrl,
                            totalBytesRead,
                            responseBody.contentLength(),
                            firstRead,
                            bytesRead == -1
                    );
                }
                return bytesRead;
            }
        };
    }

    public interface ProgressListener {
        void onDownloadProgress(HttpUrl source, long bytesRead, long contentLength, boolean start, boolean done);
    }
}
