package org.floens.chan.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.floens.chan.utils.AndroidUtils.getAppContext;

public class ImageOrientationUtil {
    private static Bitmap rotateImage(File file) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        Matrix matrix = new Matrix();
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
                //no rotation
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static String getTempFilename() throws IOException {
        File outputDir = getAppContext().getCacheDir();
        File outputFile = File.createTempFile("image", ".jpg", outputDir);
        return outputFile.getAbsolutePath();
    }

    public static File getFixedFile(File file) throws IOException {
        Bitmap fixed = rotateImage(file);
        String tempFileName = getTempFilename();
        FileOutputStream output = new FileOutputStream(tempFileName);
        fixed.compress(Bitmap.CompressFormat.JPEG, 90, output);
        output.close();
        return new File(tempFileName);
    }
}