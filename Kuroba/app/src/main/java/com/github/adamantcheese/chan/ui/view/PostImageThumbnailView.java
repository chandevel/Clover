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
package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.cache.downloader.DownloadRequestExtraInfo;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.BitmapUtils;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.NetUtilsClasses;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class PostImageThumbnailView
        extends FixedRatioThumbnailView
        implements View.OnLongClickListener {
    private PostImage postImage;
    private Drawable playIcon;
    private Rect bounds = new Rect();
    private float decodeSize;

    private CancelableDownload fullsizeDownload;

    public PostImageThumbnailView(Context context) {
        this(context, null);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.setOnLongClickListener(this);

        playIcon = context.getDrawable(R.drawable.ic_play_circle_outline_white_24dp);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PostImageThumbnailView);
        try {
            decodeSize = a.getDimension(R.styleable.PostImageThumbnailView_decode_dimen, -1);
        } finally {
            a.recycle();
        }
    }

    public void setPostImage(PostImage postImage) {
        if (this.postImage != postImage) {
            this.postImage = postImage;
            setUrl(null);
            if (fullsizeDownload != null) {
                fullsizeDownload.cancel();
                fullsizeDownload = null;
            }

            if (postImage == null) {
                return;
            }

            int width = decodeSize == -1 ? getWidth() : (int) decodeSize;
            int height = decodeSize == -1 ? getHeight() : (int) decodeSize;

            if (ChanSettings.shouldUseFullSizeImage(postImage)) {
                HttpUrl url = postImage.spoiler() ? postImage.getThumbnailUrl() : postImage.imageUrl;
                Bitmap cached = NetUtils.getCachedBitmap(url);
                if (cached != null && cached.getWidth() == width && cached.getHeight() == height) {
                    onBitmapSuccess(cached, true);
                } else {
                    fullsizeDownload = Chan.instance(FileCacheV2.class).enqueueChunkedDownloadFileRequest(url,
                            new DownloadRequestExtraInfo(postImage.size, postImage.fileHash),
                            new FileCacheListener() {
                                @Override
                                public void onSuccess(RawFile file, boolean immediate) {
                                    BackgroundUtils.runWithDefaultExecutor(() -> BitmapUtils.decodeFile(new File(file.getFullPath()),
                                            width * 2,
                                            height * 2
                                    ), result -> {
                                        if (result != null) {
                                            NetUtils.storeExternalBitmap(url, result);
                                            onBitmapSuccess(result, immediate);
                                        } else {
                                            onBitmapFailure(new NullPointerException("Failed to decode bitmap."));
                                        }
                                    });
                                }

                                @Override
                                public void onFail(Exception exception) {
                                    onBitmapFailure(exception);
                                }

                                @Override
                                public void onNotFound() {
                                    onBitmapFailure(new NetUtilsClasses.HttpCodeException(404));
                                }
                            }
                    );
                }
            } else {
                setUrl(postImage.getThumbnailUrl(), width, height);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (postImage != null && (postImage.type == PostImage.Type.MOVIE || postImage.type == PostImage.Type.IFRAME)
                && !error) {
            int x = (int) (getWidth() / 2.0 - playIcon.getIntrinsicWidth() * 0.5);
            int y = (int) (getHeight() / 2.0 - playIcon.getIntrinsicHeight() * 0.5);

            bounds.set(x, y, x + playIcon.getIntrinsicWidth(), y + playIcon.getIntrinsicHeight());
            playIcon.setBounds(bounds);
            playIcon.draw(canvas);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (postImage == null || postImage.imageUrl == null || !ChanSettings.enableLongPressURLCopy.get()) {
            return false;
        }

        setClipboardContent("Image URL", postImage.imageUrl.toString());
        showToast(getContext(), R.string.image_url_copied_to_clipboard);

        return true;
    }
}
