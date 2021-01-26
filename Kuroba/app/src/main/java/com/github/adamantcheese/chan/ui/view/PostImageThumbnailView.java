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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

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
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.repository.DrawableRepository.playIcon;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;

public class PostImageThumbnailView
        extends FixedRatioThumbnailView {
    private PostImage postImage;
    private final Rect bounds = new Rect();

    private ViewTreeObserver.OnPreDrawListener drawListener;
    private CancelableDownload fullsizeDownload;

    public PostImageThumbnailView(Context context) {
        super(context);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * @param postImage    The image to set
     * @param maxDimension <0 for this view's width, 0 for exact bitmap dimension, >0 for scaled dimension
     */
    public void setPostImage(final PostImage postImage, int maxDimension) {
        if (this.postImage == postImage) return;

        AtomicInteger requestedDimension = new AtomicInteger(maxDimension);
        this.postImage = postImage;

        if (fullsizeDownload != null) { // clear out any pending calls
            fullsizeDownload.cancel();
            fullsizeDownload = null;
        }

        if (drawListener != null) { // clear out any pending on-draws
            if (getViewTreeObserver().isAlive()) {
                getViewTreeObserver().removeOnPreDrawListener(drawListener);
            }
            drawListener = null;
        }
        setUrl(null, 0);

        if (postImage == null) return;

        if (ChanSettings.shouldUseFullSizeImage(postImage)) {
            HttpUrl url = postImage.spoiler() ? postImage.getThumbnailUrl() : postImage.imageUrl;
            Bitmap cached = NetUtils.getCachedBitmap(url);
            if (cached != null) {
                setImageBitmap(cached, true);
            } else {
                FileCacheListener listener = new FileCacheListener() {
                    @Override
                    public void onSuccess(RawFile file, boolean immediate) {
                        BitmapUtils.decodeFilePreviewImage(
                                new File(file.getFullPath()),
                                requestedDimension.get(),
                                requestedDimension.get(),
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
                };

                if (requestedDimension.get() < 0) {
                    drawListener = waitForLayout(this, view -> {
                        requestedDimension.set(Math.max(view.getWidth(), view.getHeight()));
                        setUrl(postImage.getThumbnailUrl(), requestedDimension.get());
                        fullsizeDownload =
                                Chan.instance(FileCacheV2.class).enqueueNormalDownloadFileRequest(url, listener);
                        return true;
                    });
                } else {
                    setUrl(postImage.getThumbnailUrl(), requestedDimension.get());
                    fullsizeDownload = Chan.instance(FileCacheV2.class).enqueueNormalDownloadFileRequest(url, listener);
                }
            }
        } else {
            setUrl(postImage.getThumbnailUrl(), requestedDimension.get());
        }
    }

    public PostImage getPostImage() {
        return postImage;
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
}
