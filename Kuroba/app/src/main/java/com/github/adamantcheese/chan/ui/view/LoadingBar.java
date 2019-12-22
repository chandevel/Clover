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
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class LoadingBar
        extends View {
    private static final float MINIMUM_PROGRESS = 0.1f;

    private int chunksCount = -1;
    private List<Float> chunkLoadingProgress;
    private List<Float> chunkTargetLoadingProgress;
    private Paint paint;

    public LoadingBar(Context context) {
        super(context);
        init();
    }

    public LoadingBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LoadingBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setChunksCount(int chunksCount) {
        BackgroundUtils.ensureMainThread();

        this.chunksCount = chunksCount;
        this.chunkLoadingProgress = new ArrayList<>(chunksCount);
        this.chunkTargetLoadingProgress = new ArrayList<>(chunksCount);

        for (int i = 0; i < chunksCount; ++i) {
            chunkLoadingProgress.add(0f);
            chunkTargetLoadingProgress.add(0f);
        }
    }

    public void setProgress(List<Float> updatedProgress) {
        BackgroundUtils.ensureMainThread();

        if (chunksCount != updatedProgress.size()) {
            throw new IllegalArgumentException("Bad updatedProgress list size: "
                    + updatedProgress.size() + ", should be " + chunksCount);
        }

        for (int i = 0; i < updatedProgress.size(); i++) {
            float updatedChunkProgress = updatedProgress.get(i);
            float targetChunkProgress = chunkTargetLoadingProgress.get(i);

            float clampedProgress = Math.min(Math.max(updatedChunkProgress, 0f), 1f);
            this.chunkTargetLoadingProgress.set(
                    i,
                    MINIMUM_PROGRESS + clampedProgress * (1f - MINIMUM_PROGRESS)
            );

            chunkLoadingProgress.set(i, targetChunkProgress);
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (chunkLoadingProgress.size() != chunkTargetLoadingProgress.size()) {
            throw new IllegalStateException("chunkLoadingProgress.size != chunkTargetLoadingProgress.size!");
        }

        float width = getWidth() / chunksCount;
        float offset = 0f;

        for (int i = 0; i < chunkLoadingProgress.size(); i++) {
            float progress = chunkLoadingProgress.get(i);
            if (progress > 0f) {
                canvas.drawRect(offset, 0f, offset + (width * progress), getHeight(), paint);
            }

            offset += width;
        }
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(getAttrColor(getContext(), R.attr.colorAccent));
    }
}
