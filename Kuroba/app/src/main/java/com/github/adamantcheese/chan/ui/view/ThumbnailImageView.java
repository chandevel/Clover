package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.github.adamantcheese.chan.core.model.PostImage;

import static com.github.adamantcheese.chan.core.repository.DrawableRepository.playIcon;

public class ThumbnailImageView
        extends AppCompatImageView {

    private PostImage.Type type = PostImage.Type.STATIC;
    private final Rect bounds = new Rect();

    public ThumbnailImageView(@NonNull Context context) {
        super(context);
    }

    public ThumbnailImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ThumbnailImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setType(PostImage.Type type) {
        this.type = type;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (type == PostImage.Type.MOVIE || type == PostImage.Type.IFRAME) {
            int iconScale = 2;
            double scalar = (Math.pow(2.0, iconScale) - 1) / Math.pow(2.0, iconScale);
            int x = (int) (getWidth() * 0.5 - playIcon.getIntrinsicWidth() * scalar);
            int y = (int) (getHeight() * 0.5 - playIcon.getIntrinsicHeight() * scalar);

            bounds.set(x,
                    y,
                    x + playIcon.getIntrinsicWidth() * iconScale,
                    y + playIcon.getIntrinsicHeight() * iconScale
            );
            playIcon.setBounds(bounds);
            playIcon.draw(canvas);
        }
    }
}
