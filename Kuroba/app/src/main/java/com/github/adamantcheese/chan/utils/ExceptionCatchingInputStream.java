package com.github.adamantcheese.chan.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;

/**
 * An {@link java.io.InputStream} that catches {@link java.io.IOException}s during read and skip
 * calls and stores them so they can later be handled or thrown. This class is a workaround for a
 * framework issue where exceptions during reads while decoding bitmaps in {@link
 * android.graphics.BitmapFactory} can return partially decoded bitmaps.
 * <p>
 * <p>
 * This has been modified from the original Glide source to remove some non-required stuff and catch more exceptions.
 */
public class ExceptionCatchingInputStream
        extends InputStream {
    private final InputStream stream;
    private Exception exception;

    public ExceptionCatchingInputStream(@NonNull InputStream stream) {
        this.stream = stream;
    }

    @Override
    public int available() {
        try {
            return stream.available();
        } catch (Exception e) {
            exception = e;
            return 0;
        }
    }

    @Override
    public void close() {
        try {
            stream.close();
        } catch (Exception e) {
            exception = e;
        }
    }

    @Override
    public void mark(int readLimit) {
        stream.mark(readLimit);
    }

    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    @Override
    public int read(@NonNull byte[] buffer) {
        try {
            return stream.read(buffer);
        } catch (Exception e) {
            exception = e;
            return -1;
        }
    }

    @Override
    public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) {
        try {
            return stream.read(buffer, byteOffset, byteCount);
        } catch (Exception e) {
            exception = e;
            return -1;
        }
    }

    @Override
    public synchronized void reset() {
        try {
            stream.reset();
        } catch (Exception e) {
            exception = e;
        }
    }

    @Override
    public long skip(long byteCount) {
        try {
            return stream.skip(byteCount);
        } catch (Exception e) {
            exception = e;
            return 0;
        }
    }

    @Override
    public int read() {
        try {
            return stream.read();
        } catch (Exception e) {
            exception = e;
            return -1;
        }
    }

    @Nullable
    public Exception getException() {
        return exception;
    }
}
