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
package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;

import com.github.adamantcheese.chan.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;

/**
 * Simple ImageDecoder. Taken from Volley ImageRequest.
 */
public class ImageDecoder {

    public static void decodeFileOnBackgroundThread(
            final File file, int maxWidth, int maxHeight, final ImageDecoderCallback callback
    ) {
        Thread thread = new Thread(() -> {
            final Bitmap bitmap = decodeFile(file, maxWidth, maxHeight);
            Bitmap videoBitmap = null;
            try {
                MediaMetadataRetriever video = new MediaMetadataRetriever();
                video.setDataSource(file.getAbsolutePath());
                Bitmap frameBitmap = video.getFrameAtTime();
                Bitmap audioIconBitmap = BitmapFactory.decodeResource(getRes(), R.drawable.ic_volume_up_white_24dp);
                Bitmap audioBitmap = Bitmap.createScaledBitmap(audioIconBitmap,
                        audioIconBitmap.getWidth() * 3,
                        audioIconBitmap.getHeight() * 3,
                        true
                );
                boolean hasAudio = "yes".equals(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
                if (hasAudio && frameBitmap != null) {
                    videoBitmap = Bitmap.createBitmap(frameBitmap.getWidth(),
                            frameBitmap.getHeight(),
                            frameBitmap.getConfig()
                    );
                    Canvas temp = new Canvas(videoBitmap);
                    temp.drawBitmap(frameBitmap, new Matrix(), null);
                    temp.drawBitmap(audioBitmap,
                            frameBitmap.getWidth() - audioBitmap.getWidth(),
                            frameBitmap.getHeight() - audioBitmap.getHeight(),
                            null
                    );
                } else {
                    videoBitmap = frameBitmap;
                }
            } catch (Exception ignored) {
            }

            final Bitmap finalVideoBitmap = videoBitmap;
            BackgroundUtils.runOnMainThread(() -> callback.onImageBitmap(bitmap != null ? bitmap : finalVideoBitmap));
        });
        thread.start();
    }

    public interface ImageDecoderCallback {
        void onImageBitmap(Bitmap bitmap);
    }

    public static Bitmap decodeFile(File file, int maxWidth, int maxHeight) {
        if (file == null || !file.exists()) return null;

        FileInputStream fis;

        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Bitmap bitmap = null;

        try {
            IOUtils.copy(fis, baos);
            bitmap = decode(baos.toByteArray(), maxWidth, maxHeight);
        } catch (IOException | OutOfMemoryError e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fis);
            IOUtils.closeQuietly(baos);
        }

        return bitmap;
    }

    public static Bitmap decode(byte[] data, int maxWidth, int maxHeight) {
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        Bitmap bitmap;

        // If we have to resize this image, first get the natural bounds.
        decodeOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);
        int actualWidth = decodeOptions.outWidth;
        int actualHeight = decodeOptions.outHeight;

        // Then compute the dimensions we would ideally like to decode to.
        int desiredWidth = getResizedDimension(maxWidth, maxHeight, actualWidth, actualHeight);
        int desiredHeight = getResizedDimension(maxHeight, maxWidth, actualHeight, actualWidth);

        // Decode to the nearest power of two scaling factor.
        decodeOptions.inJustDecodeBounds = false;

        // decodeOptions.inPreferQualityOverSpeed = PREFER_QUALITY_OVER_SPEED;
        decodeOptions.inSampleSize = findBestSampleSize(actualWidth, actualHeight, desiredWidth, desiredHeight);
        Bitmap tempBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);

        // If necessary, scale down to the maximal acceptable size.
        if (tempBitmap != null && (tempBitmap.getWidth() > desiredWidth || tempBitmap.getHeight() > desiredHeight)) {
            bitmap = Bitmap.createScaledBitmap(tempBitmap, desiredWidth, desiredHeight, true);
            tempBitmap.recycle();
        } else {
            bitmap = tempBitmap;
        }

        return bitmap;
    }

    private static int getResizedDimension(int maxPrimary, int maxSecondary, int actualPrimary, int actualSecondary) {
        // If no dominant value at all, just return the actual.
        if (maxPrimary == 0 && maxSecondary == 0) {
            return actualPrimary;
        }

        // If primary is unspecified, scale primary to match secondary's scaling ratio.
        if (maxPrimary == 0) {
            double ratio = (double) maxSecondary / (double) actualSecondary;
            return (int) (actualPrimary * ratio);
        }

        if (maxSecondary == 0) {
            return maxPrimary;
        }

        double ratio = (double) actualSecondary / (double) actualPrimary;
        int resized = maxPrimary;
        if (resized * ratio > maxSecondary) {
            resized = (int) (maxSecondary / ratio);
        }
        return resized;
    }

    private static int findBestSampleSize(int actualWidth, int actualHeight, int desiredWidth, int desiredHeight) {
        double wr = (double) actualWidth / desiredWidth;
        double hr = (double) actualHeight / desiredHeight;
        double ratio = Math.min(wr, hr);
        float n = 1.0f;
        while ((n * 2) <= ratio) {
            n *= 2;
        }

        return (int) n;
    }
}
