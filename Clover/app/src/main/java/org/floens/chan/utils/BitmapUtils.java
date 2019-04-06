package org.floens.chan.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.floens.chan.core.presenter.ImageReencodingPresenter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.floens.chan.utils.AndroidUtils.getAppContext;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final int MIN_QUALITY = 1;
    private static final int MAX_QUALITY = 100;
    private static final int MIN_REDUCE = 1;
    private static final int MAX_REDUCE = 10;
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String TEMP_FILE_NAME = "temp_file_name";
    private static final String TEMP_FILE_NAME_WITH_CACHE_DIR = "cache/" + TEMP_FILE_NAME;

    private static final Random random = new Random();

    public static File reencodeBitmapFile(
            @NonNull File inputBitmapFile,
            boolean removeMetadata,
            boolean changeImageChecksum,
            @Nullable ImageReencodingPresenter.Reencode reencode
    ) throws IOException {
        if (reencode == null) {
            return inputBitmapFile;
        }

        int quality = reencode.getReencodeQuality();
        int reduce = reencode.getReduce();
        ImageReencodingPresenter.ReencodeType reencodeType = reencode.getReencodeType();

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
                && !removeMetadata
                && !changeImageChecksum) {
            return inputBitmapFile;
        }

        Bitmap bitmap = null;
        Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.PNG;

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

            //slightly change one pixel oof the image to change it's checksum
            if (changeImageChecksum) {
                changeBitmapChecksum(bitmap);
            }

            //scale image down
            if (reduce != MIN_REDUCE) {
                float scale = (float) ((MAX_REDUCE + 1) - reduce) / MAX_REDUCE;
                matrix.setScale(scale, scale);
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

        int pixel = bitmap.getPixel(randomX, randomY);

        //one pixel is enough to change the checksum of an image
        if (pixel - 1 >= 0) {
            --pixel;
        } else {
            ++pixel;
        }

        bitmap.setPixel(randomX, randomY, pixel);
    }
}
