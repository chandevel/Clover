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

import java.io.File;
import java.io.FileInputStream;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;

public class ImageDecoder {

    public static void decodeFileOnBackgroundThread(
            final File file, int maxWidth, int maxHeight, final ImageDecoderCallback callback
    ) {
        BackgroundUtils.runOnBackgroundThread(() -> {
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
    }

    public interface ImageDecoderCallback {
        void onImageBitmap(Bitmap bitmap);
    }

    public static Bitmap decodeFile(File file, int maxWidth, int maxHeight) {
        if (file == null || !file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {
            return BitmapUtils.decode(fis, maxWidth, maxHeight);
        } catch (Throwable e) {
            Logger.e("ImageDecoder", "", e);
            return null;
        }
    }
}
