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
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.layout.FixedRatioLinearLayout;
import com.github.adamantcheese.chan.utils.BitmapUtils;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;

import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class PostImageThumbnailView
        extends FixedRatioThumbnailView
        implements View.OnLongClickListener {
    private PostImage postImage;
    private Drawable playIcon;
    private Rect bounds = new Rect();

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
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (fullsizeDownload != null) {
            fullsizeDownload.cancel();
            fullsizeDownload = null;
        }
    }

    public void setPostImage(Loadable loadable, PostImage postImage, int width, int height) {
        if (this.postImage != postImage) {
            this.postImage = postImage;

            if (postImage != null) {
                if (!loadable.isLocal()) {
                    if (ChanSettings.autoLoadThreadImages.get() && (postImage.type == PostImage.Type.STATIC
                            || postImage.type == PostImage.Type.GIF)) {
                        fullsizeDownload = Chan.instance(FileCacheV2.class).enqueueChunkedDownloadFileRequest(loadable,
                                postImage,
                                new DownloadRequestExtraInfo(postImage.size, postImage.fileHash),
                                new FileCacheListener() {
                                    @Override
                                    public void onSuccess(RawFile file, boolean immediate) {
                                        Bitmap fullsize = BitmapUtils.decodeFile(new File(file.getFullPath()),
                                                width * 2,
                                                height * 2
                                        );
                                        if (fullsize != null) {
                                            onBitmapSuccess(fullsize, immediate);
                                        } else {
                                            onBitmapFailure(null, new NullPointerException("Failed to decode bitmap."));
                                        }
                                    }

                                    @Override
                                    public void onFail(Exception exception) {
                                        onBitmapFailure(null, exception);
                                    }

                                    @Override
                                    public void onNotFound() {
                                        onBitmapFailure(null, new NetUtils.HttpCodeException(404));
                                    }
                                }
                        );
                    } else {
                        setUrl(postImage.getThumbnailUrl(), width, height);
                    }
                } else {
                    String fileName;

                    if (postImage.spoiler()) {
                        String extension =
                                StringUtils.extractFileNameExtension(postImage.spoilerThumbnailUrl.toString());

                        fileName = ThreadSaveManager.formatSpoilerImageName(extension);
                    } else {
                        String extension = StringUtils.extractFileNameExtension(postImage.thumbnailUrl.toString());

                        fileName = ThreadSaveManager.formatThumbnailImageName(postImage.serverFilename, extension);
                    }

                    setUrlFromDisk(loadable, fileName, postImage.spoiler(), width, height);
                }
            } else {
                setUrl(null);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (postImage != null && postImage.type == PostImage.Type.MOVIE && !error) {
            int iconScale = 1;
            double scalar = (Math.pow(2.0, iconScale) - 1) / Math.pow(2.0, iconScale);
            int x = (int) (getWidth() / 2.0 - playIcon.getIntrinsicWidth() * scalar);
            int y = (int) (getHeight() / 2.0 - playIcon.getIntrinsicHeight() * scalar);

            bounds.set(x,
                    y,
                    x + playIcon.getIntrinsicWidth() * iconScale,
                    y + playIcon.getIntrinsicHeight() * iconScale
            );
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
