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

        int quality = 100;
        int scale = 100;
        ImageReencodingPresenter.ReencodeType reencodeType = ImageReencodingPresenter.ReencodeType.AS_IS;

        if (reencode.getReencodeQuality() != 100) {
            quality = reencode.getReencodeQuality();
        }

        if (reencode.getResizeScale() != 100) {
            scale = reencode.getResizeScale();
        }

        if (reencode.getReencodeType() != ImageReencodingPresenter.ReencodeType.AS_IS) {
            reencodeType = reencode.getReencodeType();
        }

        if (quality > 100) {
            throw new RuntimeException("quality > 100 (" + quality + ")");
        }

        if (scale > 100) {
            throw new RuntimeException("scale > 100 (" + scale + ")");
        }

        if (quality == 100
                && scale == 100
                && reencodeType == ImageReencodingPresenter.ReencodeType.AS_IS
                && !removeMetadata
                && !changeImageChecksum) {
            return inputBitmapFile;
        }

        Bitmap bitmap = null;
        Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.JPEG;

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

            if (changeImageChecksum) {
                changeBitmapChecksum(bitmap);
            }

            if (scale != 100) {
                matrix.setScale(scale / 100.f, scale / 100.0f);
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
                        Logger.w(TAG, "Could not delete temp file " + tempFile.getAbsolutePath());
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

        return File.createTempFile("temp_image_file", ".tmp", outputDir);
    }

    private static void deleteOldTempFiles(File[] files) {
        if (files.length == 0) {
            return;
        }

        for (File file : files) {
            if (file.getAbsolutePath().contains("cache/temp_image_file")) {
                file.delete();
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
