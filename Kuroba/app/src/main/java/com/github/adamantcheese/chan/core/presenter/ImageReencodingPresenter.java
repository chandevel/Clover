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
package com.github.adamantcheese.chan.core.presenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Point;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.exifinterface.media.ExifInterface;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.utils.BitmapUtils;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ImageReencodingPresenter {
    private final Context context;
    private final ImageReencodingPresenterCallback callback;
    private final Reply draft;

    public ImageReencodingPresenter(Context context, ImageReencodingPresenterCallback callback, Loadable loadable) {
        this.context = context;
        this.draft = loadable.draft;
        this.callback = callback;
    }

    public void loadImagePreview() {
        Point displaySize = getDisplaySize();
        BitmapUtils.decodeFilePreviewImage(draft.file,
                //decode to the device width/height, whatever is smaller
                dp(Math.min(displaySize.x, displaySize.y)), 0, bitmap -> {
                    if (bitmap == null) {
                        showToast(context, R.string.could_not_decode_image_bitmap);
                        callback.showImagePreview(BitmapRepository.error);
                        return;
                    }

                    callback.showImagePreview(bitmap);
                }, true
        );
    }

    public boolean hasExif() {
        try {
            ExifInterface exif = new ExifInterface(draft.file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Nullable
    public CompressFormat getCurrentFileFormat() {
        return BitmapUtils.getImageFormat(draft.file);
    }

    public Pair<Integer, Integer> getImageDims() {
        return BitmapUtils.getImageDims(draft.file);
    }

    public void applyImageOptions(ImageOptions options) {
        ChanSettings.lastImageOptions.set(AppModule.gson.toJson(options));

        callback.disableOrEnableButtons(false);
        try {
            CompressFormat reencodeFormat =
                    callback.getReencodeFormat() == null ? getCurrentFileFormat() : callback.getReencodeFormat();
            draft.file = BitmapUtils.reencodeBitmapFile(draft.file, options, reencodeFormat);
        } catch (Throwable error) {
            showToast(context, getString(R.string.could_not_apply_image_options, error.getMessage()));
            return;
        } finally {
            callback.disableOrEnableButtons(true);
        }

        callback.onImageOptionsApplied();
    }

    public static class ImageOptions {
        private static final int MIN_QUALITY = 1;
        private static final int MAX_QUALITY = 100;
        private static final int MIN_REDUCE = 0;
        private static final int MAX_REDUCE = 100;

        public boolean fixExif;
        public boolean changeImageChecksum;
        public int reencodeQuality;
        public int reducePercent;

        public ImageOptions(boolean fixExif, boolean changeImageChecksum, int reencodeQuality, int reducePercent) {
            this.fixExif = fixExif;
            this.changeImageChecksum = changeImageChecksum;
            this.reencodeQuality = reencodeQuality;
            this.reducePercent = reducePercent;
            if (areOptionsInvalid()) { //reset these if not valid
                this.reencodeQuality = MAX_QUALITY;
                this.reducePercent = MIN_REDUCE;
            }
        }

        public boolean areOptionsInvalid() {
            return reencodeQuality < MIN_QUALITY || reencodeQuality > MAX_QUALITY || reducePercent < MIN_REDUCE
                    || reducePercent > MAX_REDUCE;
        }
    }

    public interface ImageReencodingPresenterCallback {
        void showImagePreview(Bitmap bitmap);

        void disableOrEnableButtons(boolean enabled);

        void onImageOptionsApplied();

        CompressFormat getReencodeFormat();
    }
}
