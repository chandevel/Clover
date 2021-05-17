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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.model.PostImage.Type.IFRAME;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.MOVIE;
import static com.github.adamantcheese.chan.core.repository.DrawableRepository.playIcon;

public class PostImageThumbnailView
        extends FixedRatioThumbnailView {
    boolean drawPlayIcon = false;
    private final Rect bounds = new Rect();

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
        drawPlayIcon = postImage != null && errorText == null && (postImage.type == MOVIE || postImage.type == IFRAME);

        if (postImage == null) {
            setUrl(null, 0);
            return;
        }

        if (ChanSettings.shouldUseFullSizeImage(postImage)) {
            HttpUrl url = postImage.spoiler() ? postImage.getThumbnailUrl() : postImage.imageUrl;
            setUrl(url, maxDimension);
        } else {
            setUrl(postImage.getThumbnailUrl(), maxDimension);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (drawPlayIcon) {
            int x = (int) (getWidth() * 0.5 - playIcon.getIntrinsicWidth() * 0.5);
            int y = (int) (getHeight() * 0.5 - playIcon.getIntrinsicHeight() * 0.5);

            bounds.set(x, y, x + playIcon.getIntrinsicWidth(), y + playIcon.getIntrinsicHeight());
            Rect curBounds = playIcon.getBounds();
            playIcon.setBounds(bounds);
            playIcon.draw(canvas);
            playIcon.setBounds(curBounds);
        }
    }
}
