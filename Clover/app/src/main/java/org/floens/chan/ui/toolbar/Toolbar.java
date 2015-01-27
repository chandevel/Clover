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
package org.floens.chan.ui.toolbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.ui.drawable.ArrowMenuDrawable;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrDrawable;

public class Toolbar extends LinearLayout implements View.OnClickListener {
    private ImageView arrowMenuView;
    private ArrowMenuDrawable arrowMenuDrawable;

    private FrameLayout navigationItemContainer;

    private ToolbarCallback callback;
    private NavigationItem navigationItem;

    public Toolbar(Context context) {
        super(context);
        init();
    }

    public Toolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Toolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setNavigationItem(final boolean animate, final boolean pushing, final NavigationItem item) {
        if (item.menu != null) {
            AndroidUtils.waitForMeasure(this, new AndroidUtils.OnMeasuredCallback() {
                @Override
                public void onMeasured(View view, int width, int height) {
                    setNavigationItemView(animate, pushing, item);
                }
            });
        } else {
            setNavigationItemView(animate, pushing, item);
        }
    }

    public void setCallback(ToolbarCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onClick(View v) {
        if (v == arrowMenuView) {
            if (callback != null) {
                callback.onMenuBackClicked(arrowMenuDrawable.getProgress() == 1f);
            }
        }
    }

    public void setArrowMenuProgress(float progress) {
        arrowMenuDrawable.setProgress(progress);
    }

    private void init() {
        setOrientation(HORIZONTAL);

        FrameLayout leftButtonContainer = new FrameLayout(getContext());
        addView(leftButtonContainer, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

        arrowMenuView = new ImageView(getContext());
        arrowMenuView.setOnClickListener(this);
        arrowMenuView.setFocusable(true);
        arrowMenuView.setScaleType(ImageView.ScaleType.CENTER);
        arrowMenuDrawable = new ArrowMenuDrawable();
        arrowMenuView.setImageDrawable(arrowMenuDrawable);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //noinspection deprecation
            arrowMenuView.setBackgroundDrawable(getAttrDrawable(android.R.attr.selectableItemBackgroundBorderless));
        } else {
            //noinspection deprecation
            arrowMenuView.setBackgroundResource(R.drawable.gray_background_selector);
        }

        leftButtonContainer.addView(arrowMenuView, new FrameLayout.LayoutParams(dp(56), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));

        navigationItemContainer = new FrameLayout(getContext());
        addView(navigationItemContainer, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    private void setNavigationItemView(boolean animate, boolean pushing, NavigationItem toItem) {
        toItem.view = createNavigationItemView(toItem);

        navigationItemContainer.addView(toItem.view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        final NavigationItem fromItem = navigationItem;

        final int duration = 300;
        final int offset = dp(16);

        if (animate) {
            toItem.view.setAlpha(0f);

            List<Animator> animations = new ArrayList<>(5);

            if (fromItem != null && fromItem.hasBack != toItem.hasBack) {
                ValueAnimator arrowAnimation = ValueAnimator.ofFloat(fromItem.hasBack ? 1f : 0f, toItem.hasBack ? 1f : 0f);
                arrowAnimation.setDuration(duration);
                arrowAnimation.setInterpolator(new DecelerateInterpolator(2f));
                arrowAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        setArrowMenuProgress((float) animation.getAnimatedValue());
                    }
                });
                animations.add(arrowAnimation);
            } else {
                setArrowMenuProgress(toItem.hasBack ? 1f : 0f);
            }

            Animator toYAnimation = ObjectAnimator.ofFloat(toItem.view, View.TRANSLATION_Y, pushing ? offset : -offset, 0f);
            toYAnimation.setDuration(duration);
            toYAnimation.setInterpolator(new DecelerateInterpolator(2f));
            toYAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (fromItem != null) {
                        removeNavigationItem(fromItem);
                    }
                }
            });
            animations.add(toYAnimation);

            Animator toAlphaAnimation = ObjectAnimator.ofFloat(toItem.view, View.ALPHA, 0f, 1f);
            toAlphaAnimation.setDuration(duration);
            toAlphaAnimation.setInterpolator(new DecelerateInterpolator(2f));
            animations.add(toAlphaAnimation);

            if (fromItem != null) {
                Animator fromYAnimation = ObjectAnimator.ofFloat(fromItem.view, View.TRANSLATION_Y, 0f, pushing ? -offset : offset);
                fromYAnimation.setDuration(duration);
                fromYAnimation.setInterpolator(new DecelerateInterpolator(2f));
                animations.add(fromYAnimation);

                Animator fromAlphaAnimation = ObjectAnimator.ofFloat(fromItem.view, View.ALPHA, 1f, 0f);
                fromAlphaAnimation.setDuration(duration);
                fromAlphaAnimation.setInterpolator(new DecelerateInterpolator(2f));
                animations.add(fromAlphaAnimation);
            }

            AnimatorSet set = new AnimatorSet();
            set.setStartDelay(pushing ? 0 : 100);
            set.playTogether(animations);
            set.start();
        } else {
            // No animation
            if (fromItem != null) {
                removeNavigationItem(fromItem);
            }
            setArrowMenuProgress(toItem.hasBack ? 1f : 0f);
        }

        navigationItem = toItem;
    }

    private void removeNavigationItem(NavigationItem item) {
        item.view.removeAllViews();
        navigationItemContainer.removeView(item.view);
        item.view = null;
    }

    private LinearLayout createNavigationItemView(NavigationItem item) {
        LinearLayout wrapper = new LinearLayout(getContext());

        TextView titleView = new TextView(getContext());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
//        titleView.setTextColor(Color.argb((int)(0.87 * 255.0), 0, 0, 0));
        titleView.setTextColor(Color.argb(255, 255, 255, 255));
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(dp(16), 0, 0, 0);
        titleView.setTypeface(AndroidUtils.ROBOTO_MEDIUM);
        titleView.setText(item.title);
        wrapper.addView(titleView, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        if (item.menu != null) {
            wrapper.addView(item.menu, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        }

        return wrapper;
    }

    public interface ToolbarCallback {
        public void onMenuBackClicked(boolean isArrow);
    }
}
