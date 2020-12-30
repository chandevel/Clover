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
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.BitmapUtils;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;

public class PostImageThumbnailView
        extends FixedRatioThumbnailView
        implements View.OnLongClickListener {
    private PostImage postImage;
    private final Drawable playIcon;
    private final Rect bounds = new Rect();
    private int decodeSize;

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

        playIcon = context.getDrawable(R.drawable.ic_fluent_play_circle_24_regular);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PostImageThumbnailView);
        try {
            decodeSize = (int) a.getDimension(R.styleable.PostImageThumbnailView_decode_dimen, 0);
        } finally {
            a.recycle();
        }
    }

    public void setDecodeSize(int size) {
        decodeSize = size;
    }

    public void setPostImage(final PostImage postImage) {
        if (this.postImage == postImage) return;

        this.postImage = postImage;

        if (fullsizeDownload != null) {
            fullsizeDownload.cancel();
            fullsizeDownload = null;
        }

        if (postImage == null) {
            setUrl(null, 0, 0);
            return;
        }

        if (ChanSettings.shouldUseFullSizeImage(postImage)) {
            HttpUrl url = postImage.spoiler() ? postImage.getThumbnailUrl() : postImage.imageUrl;
            Bitmap cached = NetUtils.getCachedBitmap(url);
            if (cached != null) {
                setImageBitmap(cached, true);
            } else {
                setUrl(postImage.getThumbnailUrl(), decodeSize, decodeSize);
                fullsizeDownload =
                        Chan.instance(FileCacheV2.class).enqueueNormalDownloadFileRequest(url, new FileCacheListener() {
                            @Override
                            public void onSuccess(RawFile file, boolean immediate) {
                                BitmapUtils.decodeFilePreviewImage(new File(file.getFullPath()),
                                        decodeSize,
                                        decodeSize,
                                        bitmap -> {
                                            if (bitmap != BitmapRepository.error && bitmap != null
                                                    && PostImageThumbnailView.this.postImage == postImage) {
                                                NetUtils.storeExternalBitmap(url, bitmap);
                                                setImageBitmap(bitmap, false);
                                            }
                                        },
                                        false
                                );
                            }
                        });
            }
        } else {
            setUrl(postImage.getThumbnailUrl(), decodeSize, decodeSize);
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
        if (postImage == null || !ChanSettings.enableLongPressURLCopy.get()) {
            return false;
        }

        setClipboardContent("Image URL", postImage.imageUrl.toString());
        showToast(getContext(), R.string.image_url_copied_to_clipboard);

        return true;
    }
}
