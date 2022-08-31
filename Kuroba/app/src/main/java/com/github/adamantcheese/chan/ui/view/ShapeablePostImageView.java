package com.github.adamantcheese.chan.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.google.android.material.imageview.ShapeableImageView;

public class ShapeablePostImageView
        extends ShapeableImageView {

    private final Drawable playIcon;
    private boolean useRipple;
    private final Drawable foregroundRipple;

    private PostImage.Type type = PostImage.Type.STATIC;

    public ShapeablePostImageView(@NonNull Context context) {
        this(context, null);
    }

    public ShapeablePostImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public ShapeablePostImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        playIcon = context.getDrawable(R.drawable.ic_fluent_play_circle_24_regular);

        // sadly the android:foreground attribute is only in API 23+, so we have to do ripple drawables ourselves here
        TypedValue rippleAttrForThemeValue = new TypedValue();
        getContext().getTheme().resolveAttribute(R.attr.colorControlHighlight, rippleAttrForThemeValue, true);
        foregroundRipple = new RippleDrawable(ColorStateList.valueOf(rippleAttrForThemeValue.data),
                null,
                new ColorDrawable(Color.WHITE)
        );

        setScaleType(ScaleType.CENTER_CROP); // by default use this

        if (isInEditMode()) {
            setImageResource(R.drawable.ic_stat_notify);
        }
    }

    public void setType(PostImage image) {
        type = image == null ? PostImage.Type.STATIC : image.type;
        invalidate();
    }

    @Override
    public void setClickable(boolean clickable) {
        if (clickable != isClickable()) {
            super.setClickable(clickable);

            if (clickable) {
                useRipple = true;
                foregroundRipple.setCallback(this);
                if (foregroundRipple.isStateful()) {
                    foregroundRipple.setState(getDrawableState());
                }
            } else {
                unscheduleDrawable(foregroundRipple);
                useRipple = false;
                foregroundRipple.setCallback(null);
            }
            requestLayout();
            invalidate();
        } else {
            super.setClickable(clickable);
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || (useRipple && who == foregroundRipple);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();

        if (useRipple) {
            foregroundRipple.jumpToCurrentState();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (useRipple && foregroundRipple.isStateful()) {
            foregroundRipple.setState(getDrawableState());
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (useRipple) {
            foregroundRipple.setHotspot(x, y);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (type == PostImage.Type.MOVIE || type == PostImage.Type.IFRAME) {
            float proportion = 3.5f;
            boolean wide = getHeight() < getWidth();
            float iconOffset = (wide ? getWidth() : getHeight()) * 0.5f
                    - (wide ? playIcon.getIntrinsicWidth() : playIcon.getIntrinsicHeight()) * 0.5f;
            float iconScale = proportion * iconOffset / (wide ? getWidth() : getHeight());
            float x = getWidth() * 0.5f - playIcon.getIntrinsicWidth() * iconScale * 0.5f;
            float y = getHeight() * 0.5f - playIcon.getIntrinsicHeight() * iconScale * 0.5f;

            int prevAlpha = playIcon.getAlpha();
            playIcon.setAlpha((int) getAlpha() * 255);
            playIcon.setBounds((int) x,
                    (int) y,
                    (int) (x + playIcon.getIntrinsicWidth() * iconScale),
                    (int) (y + playIcon.getIntrinsicHeight() * iconScale)
            );
            playIcon.draw(canvas);
            playIcon.setAlpha(prevAlpha);
        }

        if (useRipple) {
            int prevAlpha = foregroundRipple.getAlpha();
            foregroundRipple.setAlpha((int) getAlpha() * 255);
            foregroundRipple.setBounds(0, 0, getRight(), getBottom());
            foregroundRipple.draw(canvas);
            foregroundRipple.setAlpha(prevAlpha);
        }
    }
}
