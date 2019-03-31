/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.presenter;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.utils.BackgroundUtils;
import org.floens.chan.utils.BitmapUtils;
import org.floens.chan.utils.ImageDecoder;
import org.floens.chan.utils.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.utils.AndroidUtils.dp;

public class ImageReencodingPresenter {
    private final static String TAG = "ImageReencodingPresenter";

    @Inject
    ReplyManager replyManager;

    private Executor executor = Executors.newSingleThreadExecutor();
    private ImageReencodingPresenterCallback callback;
    private Loadable loadable;
    private ImageOptions imageOptions;
    private BackgroundUtils.Cancelable cancelable;

    public ImageReencodingPresenter(ImageReencodingPresenterCallback callback, Loadable loadable) {
        inject(this);

        this.loadable = loadable;
        this.callback = callback;
        this.imageOptions = new ImageOptions();
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

        //TODO: move out constants to resources
        ImageDecoder.decodeFileOnBackgroundThread(reply.file, dp(340), dp(180), (file, bitmap) -> {
            if (bitmap == null) {
                callback.showCouldNotDecodeBitmapError();
                return;
            }

            callback.showImagePreview(bitmap);
        });
    }

    public void reencode(boolean isChecked) {
        if (isChecked) {
            //TODO: implement an input for Reencode data
            imageOptions.setReencode(new Reencode(ReencodeType.AS_IS, 70, 30));
        } else {
            imageOptions.setReencode(null);
        }
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

        if (!imageOptions.getRemoveFilename()
                && !imageOptions.getRemoveFilename()
                && !imageOptions.getChangeImageChecksum()
                && imageOptions.getReencode() == null) {
            callback.onImageOptionsApplied();
            return;
        }

        if (imageOptions.getRemoveFilename()
                && !imageOptions.getRemoveFilename()
                && !imageOptions.getChangeImageChecksum()
                && imageOptions.getReencode() == null) {
            //TODO: change this to unix time?
            reply.fileName = "reencoded_image";
            replyManager.putReply(loadable, reply);
            callback.onImageOptionsApplied();
            return;
        }

        BackgroundUtils.Cancelable localCancelable = BackgroundUtils.runWithExecutor(executor, () -> {
            callback.disableOrEnableButtons(false);

            if (imageOptions.getRemoveFilename()) {
                //TODO: change this to unix time?
                reply.fileName = "reencoded_image";
            }

            try {
                reply.file = BitmapUtils.reencodeBitmapFile(
                        reply.file,
                        imageOptions.removeMetadata,
                        imageOptions.getChangeImageChecksum(),
                        imageOptions.getReencode()
                );
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to re-encode bitmap file", error);
                callback.disableOrEnableButtons(true);
                callback.showFailedToReencodeImage(error);
                return;
            }

            callback.disableOrEnableButtons(true);
            callback.onImageOptionsApplied();

            synchronized (this) {
                replyManager.putReply(loadable, reply);
                cancelable = null;
            }
        });

        synchronized (this) {
            cancelable = localCancelable;
        }
    }

    public static class ImageOptions {
        private boolean removeMetadata;
        private boolean removeFilename;
        private boolean changeImageChecksum;

        @Nullable
        private Reencode reencode;

        public ImageOptions() {
            this.removeMetadata = false;
            this.removeFilename = false;
            this.changeImageChecksum = false;
            this.reencode = null;
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
        public Reencode getReencode() {
            return reencode;
        }

        public void setReencode(@Nullable Reencode reencode) {
            this.reencode = reencode;
        }

        public boolean getChangeImageChecksum() {
            return changeImageChecksum;
        }

        public void setChangeImageChecksum(boolean changeImageChecksum) {
            this.changeImageChecksum = changeImageChecksum;
        }
    }

    public static class Reencode {
        private ReencodeType reencodeType;
        private int reencodeQuality;
        private int resizeScale;

        public Reencode(ReencodeType reencodeType, int reencodeQuality, int resizeScale) {
            this.reencodeType = reencodeType;
            this.reencodeQuality = reencodeQuality;
            this.resizeScale = resizeScale;
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

        public int getResizeScale() {
            return resizeScale;
        }

        public void setResizeScale(int resizeScale) {
            this.resizeScale = resizeScale;
        }
    }

    public enum ReencodeType {
        AS_IS,
        AS_PNG,
        AS_JPEG
    }

    public interface ImageReencodingPresenterCallback {
        void showCouldNotDecodeBitmapError();

        void showImagePreview(Bitmap bitmap);

        void disableOrEnableButtons(boolean enabled);

        void onImageOptionsApplied();

        void showFailedToReencodeImage(Throwable error);
    }
}
