package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.exifinterface.media.ExifInterface;

import com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

import static android.graphics.Bitmap.CompressFormat.JPEG;
import static android.graphics.Bitmap.CompressFormat.PNG;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final int PIXEL_DIFF = 5;
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String TEMP_FILE_NAME = "temp_file_name";
    private static final String TEMP_FILE_NAME_WITH_CACHE_DIR = "cache/" + TEMP_FILE_NAME;

    private static final byte[] PNG_HEADER = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] JPEG_HEADER = new byte[]{-1, -40};

    private static final Random random = new Random();
    private static BitmapFactory.Options options = new BitmapFactory.Options();

    static {
        options.inMutable = true;
    }

    public static File reencodeBitmapFile(
            @NonNull File inputBitmapFile,
            @NonNull ImageReencodingPresenter.ImageOptions imageOptions,
            @Nullable CompressFormat newFormat
    )
            throws IOException {
        if (imageOptions.areOptionsInvalid()) throw new IllegalArgumentException("Image options not formatted correctly.");
        Bitmap bitmap = BitmapFactory.decodeFile(inputBitmapFile.getAbsolutePath(), options);
        Matrix matrix = new Matrix();

        //slightly change one pixel of the image to change it's checksum
        if (imageOptions.changeImageChecksum) {
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

        //scale the image down
        if (imageOptions.reducePercent > 0) {
            float scale = (100f - (float) imageOptions.reducePercent) / 100f;
            matrix.setScale(scale, scale);
        }

        //fix exif
        if (imageOptions.fixExif) {
            try {
                ExifInterface exif = new ExifInterface(inputBitmapFile.getAbsolutePath());
                int orientation =
                        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
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
            } catch (Exception ignored) {}
        }

        Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();

        File tempFile = null;

        try {
            tempFile = getTempFilename();

            try (FileOutputStream output = new FileOutputStream(tempFile)) {
                newBitmap.compress(newFormat, imageOptions.reencodeQuality, output);
            } catch (Exception e) {
                Logger.d("BitmapUtils", "test");
            }

            return tempFile;
        } catch (Throwable error) {
            File[] list = new File[1];
            list[0] = tempFile;
            deleteOldTempFiles(list);

            throw error;
        } finally {
            if (newBitmap != null) {
                newBitmap.recycle();
            }
        }
    }

    private static File getTempFilename()
            throws IOException {
        File outputDir = getAppContext().getCacheDir();
        deleteOldTempFiles(outputDir.listFiles());

        return File.createTempFile(TEMP_FILE_NAME, TEMP_FILE_EXTENSION, outputDir);
    }

    private static void deleteOldTempFiles(File[] files) {
        if (files == null || files.length == 0) {
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

    public static boolean isFileSupportedForReencoding(File file) {
        return getImageFormat(file) != null;
    }

    public static CompressFormat getImageFormat(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[16];
            raf.read(header);

            if (JavaUtils.arrayPrefixedWith(header, PNG_HEADER)) {
                return PNG;
            }

            if (JavaUtils.arrayPrefixedWith(header, JPEG_HEADER)) {
                return JPEG;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the dimensions of the specified image file
     *
     * @param file image
     * @return a pair of dimensions, in WIDTH then HEIGHT order; -1, -1 if not determinable
     */
    @NonNull
    public static Pair<Integer, Integer> getImageDims(File file) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(file));
            return new Pair<>(bitmap.getWidth(), bitmap.getHeight());
        } catch (Exception e) {
            return new Pair<>(-1, -1);
        }
    }
}
