package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final int MIN_QUALITY = 1;
    private static final int MAX_QUALITY = 100;
    private static final int MIN_REDUCE = 1;
    private static final int MAX_REDUCE = 10;
    private static final int PIXEL_DIFF = 5;
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String TEMP_FILE_NAME = "temp_file_name";
    private static final String TEMP_FILE_NAME_WITH_CACHE_DIR = "cache/" + TEMP_FILE_NAME;

    private static final byte[] PNG_HEADER = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] JPEG_HEADER = new byte[]{-1, -40};

    private static final Random random = new Random();

    public static File reencodeBitmapFile(
            @NonNull File inputBitmapFile,
            boolean fixExif,
            boolean removeMetadata,
            boolean changeImageChecksum,
            @Nullable ImageReencodingPresenter.Reencode reencode
    ) throws IOException {
        int quality = MAX_QUALITY;
        int reduce = MIN_REDUCE;
        ImageReencodingPresenter.ReencodeType reencodeType = ImageReencodingPresenter.ReencodeType.AS_IS;

        if (reencode != null) {
            quality = reencode.getReencodeQuality();
            reduce = reencode.getReduce();
            reencodeType = reencode.getReencodeType();
        }

        if (quality < MIN_QUALITY) {
            quality = MIN_QUALITY;
        }

        if (quality > MAX_QUALITY) {
            quality = MAX_QUALITY;
        }

        if (reduce > MAX_REDUCE) {
            reduce = MAX_REDUCE;
        }

        if (reduce < MIN_REDUCE) {
            reduce = MIN_REDUCE;
        }

        //all parameters are default - do nothing
        if (quality == MAX_QUALITY
                && reduce == MIN_REDUCE
                && reencodeType == ImageReencodingPresenter.ReencodeType.AS_IS
                && !fixExif
                && !removeMetadata
                && !changeImageChecksum) {
            return inputBitmapFile;
        }

        Bitmap bitmap = null;
        Bitmap.CompressFormat compressFormat = getImageFormat(inputBitmapFile);

        if (reencodeType == ImageReencodingPresenter.ReencodeType.AS_JPEG) {
            compressFormat = Bitmap.CompressFormat.JPEG;
        } else if (reencodeType == ImageReencodingPresenter.ReencodeType.AS_PNG) {
            compressFormat = Bitmap.CompressFormat.PNG;
        }

        try {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inMutable = true;

            bitmap = BitmapFactory.decodeFile(inputBitmapFile.getAbsolutePath(), opt);
            Matrix matrix = new Matrix();

            //slightly change one pixel of the image to change it's checksum
            if (changeImageChecksum) {
                changeBitmapChecksum(bitmap);
            }

            //scale the image down
            if (reduce != MIN_REDUCE) {
                float scale = 1f / reduce;
                matrix.setScale(scale, scale);
            }

            //fix exif
            if (compressFormat == Bitmap.CompressFormat.JPEG && fixExif) {
                ExifInterface exif = new ExifInterface(inputBitmapFile.getAbsolutePath());
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        matrix.postRotate(270);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        matrix.postRotate(180);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        matrix.postRotate(90);
                        break;
                    default:
                        matrix.postRotate(0);
                        break;
                }
            }

            Bitmap newBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );

            File tempFile = null;

            try {
                tempFile = getTempFilename();

                try (FileOutputStream output = new FileOutputStream(tempFile)) {
                    newBitmap.compress(compressFormat, quality, output);
                }

                return tempFile;
            } catch (Throwable error) {
                if (tempFile != null) {
                    if (!tempFile.delete()) {
                        Logger.w(TAG, "Could not delete temp image file: " + tempFile.getAbsolutePath());
                    }
                }

                throw error;
            } finally {
                if (newBitmap != null) {
                    newBitmap.recycle();
                }
            }
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private static File getTempFilename() throws IOException {
        File outputDir = getAppContext().getCacheDir();
        deleteOldTempFiles(outputDir.listFiles());

        return File.createTempFile(TEMP_FILE_NAME, TEMP_FILE_EXTENSION, outputDir);
    }

    private static void deleteOldTempFiles(File[] files) {
        if (files.length == 0) {
            return;
        }

        for (File file : files) {
            if (file.getAbsolutePath().contains(TEMP_FILE_NAME_WITH_CACHE_DIR)) {
                if (!file.delete()) {
                    Logger.w(TAG, "Could not delete old temp image file: " + file.getAbsolutePath());
                }
            }
        }
    }

    private static void changeBitmapChecksum(Bitmap bitmap) {
        int randomX = Math.abs(random.nextInt()) % bitmap.getWidth();
        int randomY = Math.abs(random.nextInt()) % bitmap.getHeight();

        // one pixel is enough to change the checksum of an image
        int pixel = bitmap.getPixel(randomX, randomY);

        // NOTE: apparently when re-encoding jpegs, changing a pixel by 1 is sometimes not enough
        // due to the jpeg's compression algorithm (it may even out this pixel with surrounding
        // pixels like it wasn't changed at all) so we have to increase the difference a little bit
        if (pixel - PIXEL_DIFF >= 0) {
            pixel -= PIXEL_DIFF;
        } else {
            pixel += PIXEL_DIFF;
        }

        bitmap.setPixel(randomX, randomY, pixel);
    }

    public static Bitmap.CompressFormat getImageFormat(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[16];
            raf.read(header);

            {
                boolean isPngHeader = true;
                int size = Math.min(PNG_HEADER.length, header.length);

                for (int i = 0; i < size; ++i) {
                    if (header[i] != PNG_HEADER[i]) {
                        isPngHeader = false;
                        break;
                    }
                }

                if (isPngHeader) {
                    return Bitmap.CompressFormat.PNG;
                }
            }

            {
                boolean isJpegHeader = true;
                int size = Math.min(JPEG_HEADER.length, header.length);

                for (int i = 0; i < size; ++i) {
                    if (header[i] != JPEG_HEADER[i]) {
                        isJpegHeader = false;
                        break;
                    }
                }

                if (isJpegHeader) {
                    return Bitmap.CompressFormat.JPEG;
                }
            }

            throw new IllegalArgumentException("File " + file.getName() + " is neither PNG nor JPEG");
        }
    }

    /**
     * Gets the dimensions of the specified image file
     *
     * @param file image
     * @return a pair of dimensions, in WIDTH then HEIGHT order
     * @throws IOException if anything went wrong
     */
    public static Pair<Integer, Integer> getImageDims(File file) throws IOException {
        if (file == null) throw new IOException();
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file));
        return new Pair<>(bitmap.getWidth(), bitmap.getHeight());
    }
}
