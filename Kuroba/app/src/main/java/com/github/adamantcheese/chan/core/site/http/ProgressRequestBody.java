/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.site.http;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public class ProgressRequestBody
        extends RequestBody {
    protected RequestBody delegate;
    protected ProgressRequestListener listener;
    protected ProgressSink progressSink;

    private static final int maxPercent = 100;
    private static final int percentStep = 2;

    public ProgressRequestBody(RequestBody delegate, ProgressRequestListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public MediaType contentType() {
        return delegate.contentType();
    }

    @Override
    public long contentLength()
            throws IOException {
        return delegate.contentLength();
    }

    @Override
    public void writeTo(BufferedSink sink)
            throws IOException {
        BufferedSink bufferedSink;

        progressSink = new ProgressSink(sink);
        bufferedSink = Okio.buffer(progressSink);

        delegate.writeTo(bufferedSink);
        bufferedSink.flush();
    }

    protected final class ProgressSink
            extends ForwardingSink {
        private long bytesWritten = 0;
        private int lastPercent = 0;

        public ProgressSink(Sink delegate) {
            super(delegate);
        }

        @Override
        public void write(Buffer source, long byteCount)
                throws IOException {
            super.write(source, byteCount);

            if (bytesWritten == 0) {
                // so we can know that the uploading has just started
                listener.onRequestProgress(0);
            }

            bytesWritten += byteCount;
            if (contentLength() > 0) {
                int percent = (int) (maxPercent * bytesWritten / contentLength());

                if (percent - lastPercent >= percentStep) {
                    lastPercent = percent;
                    listener.onRequestProgress(percent);
                }
            }
        }
    }

    public interface ProgressRequestListener {
        void onRequestProgress(int percent);
    }
}
