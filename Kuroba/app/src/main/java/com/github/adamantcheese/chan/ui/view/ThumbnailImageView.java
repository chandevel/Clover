package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostImage;

public class ThumbnailImageView
        extends AppCompatImageView {

    private PostImage.Type type = PostImage.Type.STATIC;
    private Drawable playIcon;
    private Rect bounds = new Rect();

    public ThumbnailImageView(Context context) {
        this(context, null);
    }

    public ThumbnailImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThumbnailImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        playIcon = context.getDrawable(R.drawable.ic_play_circle_outline_white_24dp);
    }

    public void setType(PostImage.Type type) {
        this.type = type;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (type == PostImage.Type.MOVIE) {
            int iconScale = 2;
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
}
