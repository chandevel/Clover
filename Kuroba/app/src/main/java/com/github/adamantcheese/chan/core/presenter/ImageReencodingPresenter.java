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

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ReplyManager;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.BitmapUtils;
import com.github.adamantcheese.chan.utils.ImageDecoder;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.google.gson.Gson;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter.ReencodeType.AS_IS;
import static com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter.ReencodeType.AS_JPEG;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ImageReencodingPresenter {
    private final static String TAG = "ImageReencodingPresenter";

    @Inject
    ReplyManager replyManager;

    private Executor executor = Executors.newSingleThreadExecutor();
    private ImageReencodingPresenterCallback callback;
    private Loadable loadable;
    private ImageOptions imageOptions;
    private BackgroundUtils.Cancelable cancelable;

    public ImageReencodingPresenter(
            ImageReencodingPresenterCallback callback, Loadable loadable, ImageOptions lastOptions
    ) {
        inject(this);

        this.loadable = loadable;
        this.callback = callback;
        if (lastOptions != null) {
            imageOptions = lastOptions;
        } else {
            imageOptions = new ImageOptions();
        }
    }

    public void onDestroy() {
        synchronized (this) {
            if (cancelable != null) {
                cancelable.cancel();
                cancelable = null;
            }
        }
    }

    public void loadImagePreview() {
        Reply reply = replyManager.getReply(loadable);
        Point displaySize = getDisplaySize();
        ImageDecoder.decodeFileOnBackgroundThread(reply.file,
                //decode to the device width/height, whatever is smaller
                dp(displaySize.x > displaySize.y ? displaySize.y : displaySize.x), 0, bitmap -> {
                    if (bitmap == null) {
                        showToast(R.string.could_not_decode_image_bitmap);
                        return;
                    }

                    callback.showImagePreview(bitmap);
                }
        );
    }

    public boolean hasAttachedFile() {
        return replyManager.getReply(loadable).file != null;
    }

    @Nullable
    public Bitmap.CompressFormat getImageFormat() {
        try {
            Reply reply = replyManager.getReply(loadable);
            return BitmapUtils.getImageFormat(reply.file);
        } catch (Exception e) {
            Logger.e(TAG, "Error while trying to get image format", e);
            return null;
        }
    }

    @Nullable
    public Pair<Integer, Integer> getImageDims() {
        try {
            Reply reply = replyManager.getReply(loadable);
            return BitmapUtils.getImageDims(reply.file);
        } catch (Exception e) {
            Logger.e(TAG, "Error while trying to get image dimensions", e);
            return null;
        }
    }

    public void setReencode(@Nullable ReencodeSettings reencodeSettings) {
        if (reencodeSettings != null) {
            imageOptions.setReencodeSettings(reencodeSettings);
        } else {
            imageOptions.setReencodeSettings(null);
        }
    }

    public void fixExif(boolean isChecked) {
        imageOptions.setFixExif(isChecked);
    }

    public void removeMetadata(boolean isChecked) {
        imageOptions.setRemoveMetadata(isChecked);
    }

    public void removeFilename(boolean isChecked) {
        imageOptions.setRemoveFilename(isChecked);
    }

    public void changeImageChecksum(boolean isChecked) {
        imageOptions.setChangeImageChecksum(isChecked);
    }

    public void applyImageOptions() {
        Reply reply;

        synchronized (this) {
            if (cancelable != null) {
                return;
            }

            reply = replyManager.getReply(loadable);
        }

        ChanSettings.lastImageOptions.set(instance(Gson.class).toJson(imageOptions));
        Logger.d(TAG, "imageOptions: [" + imageOptions.toString() + "]");

        //all options are default - do nothing
        if (!imageOptions.getRemoveFilename() && !imageOptions.getFixExif() && !imageOptions.getRemoveMetadata()
                && !imageOptions.getChangeImageChecksum() && imageOptions.getReencodeSettings() == null) {
            callback.onImageOptionsApplied(reply, false);
            return;
        }

        //only the "remove filename" option is selected
        if (imageOptions.getRemoveFilename() && !imageOptions.getFixExif() && !imageOptions.getRemoveMetadata()
                && !imageOptions.getChangeImageChecksum() && imageOptions.getReencodeSettings() == null) {
            reply.fileName = getNewImageName(reply.fileName, AS_IS);
            callback.onImageOptionsApplied(reply, true);
            return;
        }

        //one of the options that affects the image is selected (reencode/remove metadata/change checksum)
        BackgroundUtils.Cancelable localCancelable = BackgroundUtils.runWithExecutor(executor, () -> {
            try {
                callback.disableOrEnableButtons(false);

                if (imageOptions.getRemoveFilename()) {
                    reply.fileName = getNewImageName(reply.fileName,
                            imageOptions.reencodeSettings != null ? imageOptions.reencodeSettings.reencodeType : AS_IS
                    );
                }

                reply.file = BitmapUtils.reencodeBitmapFile(reply.file,
                        imageOptions.getFixExif(),
                        imageOptions.getRemoveMetadata(),
                        imageOptions.getChangeImageChecksum(),
                        imageOptions.getReencodeSettings()
                );
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to re-encode bitmap file", error);
                callback.disableOrEnableButtons(true);
                showToast(getString(R.string.could_not_apply_image_options, error.getMessage()));
                cancelable = null;
                return;
            } finally {
                callback.disableOrEnableButtons(true);
            }

            callback.onImageOptionsApplied(reply, imageOptions.getRemoveFilename());

            synchronized (this) {
                cancelable = null;
            }
        });

        synchronized (this) {
            cancelable = localCancelable;
        }
    }

    private String getNewImageName(String currentFileName, ReencodeType newType) {
        String currentExt = StringUtils.extractFileNameExtension(currentFileName);
        if (currentExt == null) {
            currentExt = "";
        } else {
            currentExt = "." + currentExt;
        }
        switch (newType) {
            case AS_PNG:
                return System.currentTimeMillis() + ".png";
            case AS_JPEG:
                return System.currentTimeMillis() + ".jpg";
            case AS_IS:
            default:
                return System.currentTimeMillis() + currentExt;
        }
    }

    public static class ImageOptions {
        private boolean fixExif;
        private boolean removeMetadata;
        private boolean removeFilename;
        private boolean changeImageChecksum;

        @Nullable
        private ReencodeSettings reencodeSettings;

        public ImageOptions() {
            this.fixExif = false;
            this.removeMetadata = false;
            this.removeFilename = false;
            this.changeImageChecksum = false;
            this.reencodeSettings = null;
        }

        public boolean getFixExif() {
            return fixExif;
        }

        public void setFixExif(boolean fixExif) {
            this.fixExif = fixExif;
        }

        public boolean getRemoveMetadata() {
            return removeMetadata;
        }

        public void setRemoveMetadata(boolean removeMetadata) {
            this.removeMetadata = removeMetadata;
        }

        public boolean getRemoveFilename() {
            return removeFilename;
        }

        public void setRemoveFilename(boolean removeFilename) {
            this.removeFilename = removeFilename;
        }

        @Nullable
        public ReencodeSettings getReencodeSettings() {
            return reencodeSettings;
        }

        public void setReencodeSettings(@Nullable ReencodeSettings reencodeSettings) {
            this.reencodeSettings = reencodeSettings;
        }

        public boolean getChangeImageChecksum() {
            return changeImageChecksum;
        }

        public void setChangeImageChecksum(boolean changeImageChecksum) {
            this.changeImageChecksum = changeImageChecksum;
        }

        @Override
        public String toString() {
            String reencodeStr = reencodeSettings != null ? reencodeSettings.toString() : "null";

            return "fixExif = " + fixExif + ", removeMetadata = " + removeMetadata + ", removeFilename = "
                    + removeFilename + ", changeImageChecksum = " + changeImageChecksum + ", " + reencodeStr;
        }
    }

    public static class ReencodeSettings {
        private ReencodeType reencodeType;
        private int reencodeQuality;
        private int reducePercent;

        public ReencodeSettings(ReencodeType reencodeType, int reencodeQuality, int reducePercent) {
            this.reencodeType = reencodeType;
            this.reencodeQuality = reencodeQuality;
            this.reducePercent = reducePercent;
        }

        public ReencodeType getReencodeType() {
            return reencodeType;
        }

        public void setReencodeType(ReencodeType reencodeType) {
            this.reencodeType = reencodeType;
        }

        public int getReencodeQuality() {
            return reencodeQuality;
        }

        public void setReencodeQuality(int reencodeQuality) {
            this.reencodeQuality = reencodeQuality;
        }

        public int getReducePercent() {
            return reducePercent;
        }

        public void setReducePercent(int reducePercent) {
            this.reducePercent = reducePercent;
        }

        public boolean isDefault() {
            return reencodeType == AS_IS && reencodeQuality == 100 && reducePercent == 0;
        }

        @Override
        public String toString() {
            return "reencodeType = " + reencodeType + ", reencodeQuality = " + reencodeQuality + ", reducePercent = "
                    + reducePercent;
        }

        public String prettyPrint(Bitmap.CompressFormat currentFormat) {
            String type = "Unknown";
            if (currentFormat == null) {
                Logger.e(TAG, "currentFormat == null");
                return type;
            }

            switch (reencodeType) {
                case AS_IS:
                    type = "As-is";
                    break;
                case AS_PNG:
                    type = "PNG";
                    break;
                case AS_JPEG:
                    type = "JPEG";
                    break;
            }
            boolean isJpeg =
                    reencodeType == AS_JPEG || (reencodeType == AS_IS && currentFormat == Bitmap.CompressFormat.JPEG);
            String quality = isJpeg ? reencodeQuality + ", " : "";
            return "(" + type + ", " + quality + (100 - reducePercent) + "%)";
        }
    }

    public enum ReencodeType {
        AS_IS,
        AS_JPEG,
        AS_PNG;

        public static ReencodeType fromInt(int value) {
            if (value == AS_IS.ordinal()) {
                return AS_IS;
            } else if (value == AS_PNG.ordinal()) {
                return AS_PNG;
            } else if (value == AS_JPEG.ordinal()) {
                return AS_JPEG;
            }

            throw new RuntimeException("Cannot get ReencodeType from int value: " + value);
        }
    }

    public interface ImageReencodingPresenterCallback {
        void showImagePreview(Bitmap bitmap);

        void disableOrEnableButtons(boolean enabled);

        void onImageOptionsApplied(Reply reply, boolean filenameRemoved);
    }
}
