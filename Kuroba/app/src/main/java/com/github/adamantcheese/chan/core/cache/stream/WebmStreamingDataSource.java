package com.github.adamantcheese.chan.core.cache.stream;

import android.net.Uri;
import android.util.Range;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Original implementation by https://github.com/ekisu
 */
public class WebmStreamingDataSource
        extends BaseDataSource {
    private final String TAG = "WebmStreamingDataSource";

    class PartialFileCache {
        class RegionStats {
            final List<Range<Long>> cachedRanges;
            final List<Range<Long>> missingRanges;

            RegionStats(List<Range<Long>> cachedRanges, List<Range<Long>> missingRanges) {
                this.cachedRanges = cachedRanges;
                this.missingRanges = missingRanges;
            }

            private Range<Long> findRange(List<Range<Long>> ranges, long position) {
                for (Range<Long> r : ranges) {
                    if (r.contains(position)) return r;
                }

                return null;
            }

            Range<Long> findCachedRange(long position) {
                return findRange(cachedRanges, position);
            }

            Range<Long> findMissingRange(long position) {
                return findRange(missingRanges, position);
            }
        }

        private final String TAG = "PartialFileCache";

        private List<Range<Long>> cachedRanges = new ArrayList<>();
        private byte[] cachedRangesData;
        private long pos = 0;
        private long fileLength;
        private boolean firedCacheComplete = false;
        private List<Runnable> listeners = new ArrayList<>();

        PartialFileCache(long fileLength) {
            this.fileLength = fileLength;
            cachedRangesData = new byte[(int) this.fileLength];
        }

        boolean isCached(long position, long length) {
            for (Range<Long> r : cachedRanges) {
                if (r.contains(Range.create(position, position + length - 1))) {
                    return true;
                }
            }

            return false;
        }

        boolean isCached(long length) {
            return isCached(pos, length);
        }

        List<Range<Long>> determineMissingRanges() {
            List<Range<Long>> missing = new ArrayList<>();

            long rangeStart = 0;
            for (Range<Long> r : cachedRanges) {
                if (rangeStart < r.getLower()) {
                    missing.add(Range.create(rangeStart, r.getLower() - 1));
                }
                rangeStart = r.getUpper() + 1;
            }

            if (rangeStart < fileLength) {
                missing.add(Range.create(rangeStart, fileLength - 1));
            }

            return missing;
        }

        RegionStats getRegionStats(Range<Long> region) {
            List<Range<Long>> cached = new ArrayList<>();
            List<Range<Long>> missing = new ArrayList<>();

            for (Range<Long> r : cachedRanges) {
                try {
                    cached.add(region.intersect(r));
                } catch (IllegalArgumentException e) {} // Disjoint ranges.
            }

            for (Range<Long> r : determineMissingRanges()) {
                try {
                    missing.add(region.intersect(r));
                } catch (IllegalArgumentException e) {} // Disjoint ranges.
            }

            return new RegionStats(cached, missing);
        }

        private boolean rangesAreContiguous(Range<Long> left, Range<Long> right) {
            // Ranges like [0, 1] and [2, 3] are contiguous too.
            return left.getUpper() + 1 >= right.getLower();
        }

        private void joinRange(int i, int j) {
            Range<Long> left = cachedRanges.get(i);
            Range<Long> right = cachedRanges.get(j);

            Range<Long> newRange = left.extend(right);

            cachedRanges.set(i, newRange);
            cachedRanges.remove(j);
        }

        private void joinRanges() {
            for (int i = 0; i < cachedRanges.size(); i++) {
                while (i < cachedRanges.size() - 1 && rangesAreContiguous(cachedRanges.get(i),
                        cachedRanges.get(i + 1)
                )) {
                    joinRange(i, i + 1);
                }
            }
        }

        private int findRangeInsertPosition(long filePosition) {
            int rangePosition = 0;
            while (rangePosition < cachedRanges.size() && filePosition > cachedRanges.get(rangePosition).getLower()) {
                rangePosition++;
            }

            return rangePosition;
        }

        void write(byte[] data, long offset, long length) {
            int newRangePosition = findRangeInsertPosition(pos);
            Range<Long> newRange = new Range<>(pos, pos + length - 1);
            long destOffset = newRange.getLower();

            System.arraycopy(data, (int) offset, cachedRangesData, (int) destOffset, (int) length);

            cachedRanges.add(newRangePosition, newRange);
            joinRanges();

            pos += length;

            if (isCacheComplete() && !firedCacheComplete) {
                fireCacheComplete();
            }
        }

        void read(byte[] buffer, long offset, long length) {
            if (!isCached(length)) {
                throw new IllegalArgumentException("tried to read uncached data!");
            }

            System.arraycopy(cachedRangesData, (int) pos, buffer, (int) offset, (int) length);

            pos += length;
        }

        void seek(long pos) {
            this.pos = pos;
        }

        void addListener(Runnable listener) {
            listeners.add(listener);

            if (firedCacheComplete) {
                listener.run();
            }
        }

        void fireCacheComplete() {
            for (Runnable listener : listeners) {
                listener.run();
            }

            firedCacheComplete = true;
        }

        public void clearListeners() {
            listeners.clear();
        }

        boolean isCacheComplete() {
            return isCached(0, fileLength);
        }

        byte[] getCacheBytes() {
            return cachedRangesData;
        }
    }

    private FileManager fileManager;
    private HttpDataSource dataSource;
    private PartialFileCache partialFileCache;
    private byte[] dataToFillCache = null;
    private int dataToFillCacheLength = 0;
    private PartialFileCache.RegionStats activeRegionStats;
    private Range<Long> httpActiveRange;
    private List<Callback> listeners = new ArrayList<>();

    private RawFile file;
    @Nullable
    private Uri uri;

    private long pos;
    private long end;

    private long fileLength = C.LENGTH_UNSET;

    private boolean prepared = false;
    private boolean opened = false;

    public WebmStreamingDataSource(@Nullable Uri uri, RawFile file, FileManager fileManager) {
        super(/* isNetwork= */ true);
        Logger.i(TAG, "WebmStreamingDataSource");

        this.dataSource = new DefaultHttpDataSourceFactory(NetModule.USER_AGENT).createDataSource();

        this.fileManager = fileManager;
        this.file = file;
        this.uri = uri;
    }

    private void detectLength()
            throws HttpDataSource.HttpDataSourceException {
        this.fileLength = dataSource.open(new DataSpec(uri, 0, C.LENGTH_UNSET, null));

        Logger.i(TAG, "detectLength: " + this.fileLength);
    }

    private void prepare()
            throws HttpDataSource.HttpDataSourceException {
        detectLength();
        this.partialFileCache = new PartialFileCache(this.fileLength);
        partialFileCache.addListener(this::cacheComplete);

        if (dataToFillCache != null) {
            partialFileCache.write(dataToFillCache, 0, dataToFillCacheLength);
            partialFileCache.seek(0);

            dataToFillCache = null;
        }

        prepared = true;
    }

    public void fillCache(long length, InputStream inputStream)
            throws IOException {
        dataToFillCache = new byte[(int) length];
        dataToFillCacheLength = inputStream.read(dataToFillCache);
        // If it's null, this means we're not prepared yet (i.e. we don't know the real size
        // of the video, which is required by partialFileCache). Just leave it here and wait
        // until we're prepared.
        if (partialFileCache != null) {
            partialFileCache.write(dataToFillCache, 0, dataToFillCacheLength);
            partialFileCache.seek(0);
            dataToFillCache = null;
        }
    }

    @Override
    public long open(DataSpec dataSpec)
            throws IOException {
        if (!prepared) {
            prepare();
        }

        // We keep the cache for a single file, so it would be bothersome that it was used with
        // another file.
        if (!dataSpec.uri.equals(uri)) {
            throw new IOException("dataSpec.uri is different than uri passed to constructor!");
        }

        transferInitializing(dataSpec);

        Logger.i(TAG, "opening, position: " + dataSpec.position + " length: " + dataSpec.length);
        partialFileCache.seek(dataSpec.position);

        long bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? fileLength - dataSpec.position : dataSpec.length;

        pos = dataSpec.position;
        end = pos + bytesRemaining - 1;

        activeRegionStats = partialFileCache.getRegionStats(new Range<>(pos, end));
        httpActiveRange = null;

        Logger.i(TAG, "bytes remaining: " + bytesRemaining);
        if (bytesRemaining < 0) {
            throw new EOFException();
        }

        transferStarted(dataSpec);

        return bytesRemaining;
    }

    private void activateHttpRange(Range<Long> range)
            throws HttpDataSource.HttpDataSourceException {
        if (httpActiveRange == null || !httpActiveRange.equals(range)) {
            // As this is reading sequentially, and our ranges are limited to the region
            // our DataSpec was supposed to read, it's okay to assume we will read the entirety
            // of our missing ranges, and we won't need to seek inside them.

            DataSpec dataSpec = new DataSpec(uri, range.getLower(), range.getUpper() - range.getLower() + 1, null);

            dataSource.open(dataSpec);
            httpActiveRange = range;
        }
    }

    private long bytesRemaining() {
        return end - pos + 1;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength)
            throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining() == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        int readBytes = 0;
        int maxReadableBytes = (int) Math.min(bytesRemaining(), readLength);

        Range<Long> cachedRange = activeRegionStats.findCachedRange(pos);
        if (cachedRange != null) {
            int remainingBytesInRange = (int) (cachedRange.getUpper() - pos + 1);
            readBytes = Math.min(remainingBytesInRange, maxReadableBytes);
            partialFileCache.read(buffer, offset, readBytes);
        } else {
            Range<Long> missingRange = activeRegionStats.findMissingRange(pos);
            activateHttpRange(missingRange);

            readBytes = dataSource.read(buffer, offset, readLength);
            partialFileCache.write(buffer, offset, readBytes);
        }

        if (readBytes > 0) {
            bytesTransferred(readBytes);
            pos += readBytes;
        }

        return readBytes;
    }

    public void cacheComplete() {
        if (!fileManager.exists(file)) {
            throw new IllegalStateException("File does not exist!");
        }

        File innerFile = new File(file.getFullPath());

        try (FileOutputStream fos = new FileOutputStream(innerFile)) {
            fos.write(partialFileCache.getCacheBytes());
        } catch (Exception e) {
            Logger.e(TAG, "cacheComplete: caught exception", e);
            return;
        }

        BackgroundUtils.runOnUiThread(() -> {
            for (Callback c : listeners) {
                c.dataSourceAddedFile(innerFile);
            }
        });

        listeners.clear();
        partialFileCache.clearListeners();
    }

    public void addListener(Callback c) {
        listeners.add(c);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close()
            throws IOException {
        Logger.i(TAG, "close");
        try {
            if (dataSource != null) {
                dataSource.close();
            }
        } finally {
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }

    interface Callback {
        void dataSourceAddedFile(File file);
    }
}
