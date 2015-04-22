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
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.ui.drawable.ArrowMenuDrawable;
import org.floens.chan.ui.drawable.DropdownArrowDrawable;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrDrawable;

public class Toolbar extends LinearLayout implements View.OnClickListener, LoadView.Listener {
    private ImageView arrowMenuView;
    private ArrowMenuDrawable arrowMenuDrawable;

    private LoadView navigationItemContainer;

    private ToolbarCallback callback;
    private NavigationItem navigationItem;
    private boolean search = false;

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

    public void updateNavigation() {
        closeSearchInternal(true);
        setNavigationItem(false, false, navigationItem);
    }

    public boolean showSearch() {
        if (!search) {
            search = true;

            LinearLayout searchViewWrapper = new LinearLayout(getContext());
            final EditText searchView = new EditText(getContext());
            searchView.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
            searchView.setHint(callback.getSearchHint());
            searchView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            searchView.setHintTextColor(0x88ffffff);
            searchView.setTextColor(0xffffffff);
            searchView.setSingleLine(true);
            searchView.setBackgroundResource(0);
            searchView.setPadding(0, 0, 0, 0);
            final ImageView clearButton = new ImageView(getContext());
            searchView.addTextChangedListener(new TextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    callback.onSearchEntered(s.toString());
                    clearButton.setAlpha(s.length() == 0 ? 0.6f : 1.0f);
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
            searchView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        AndroidUtils.hideKeyboard(searchView);
                        callback.onSearchEntered(searchView.getText().toString());
                        return true;
                    }
                    return false;
                }
            });
            LinearLayout.LayoutParams searchViewParams = new LinearLayout.LayoutParams(0, dp(36), 1);
            searchViewParams.gravity = Gravity.CENTER_VERTICAL;
            searchViewWrapper.addView(searchView, searchViewParams);

            clearButton.setImageResource(R.drawable.ic_close_white_24dp);
            clearButton.setAlpha(0.6f);
            clearButton.setScaleType(ImageView.ScaleType.CENTER);
            clearButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchView.setText("");
                    AndroidUtils.requestKeyboardFocus(searchView);
                }
            });
            searchViewWrapper.addView(clearButton, dp(48), LayoutParams.MATCH_PARENT);
            searchViewWrapper.setPadding(dp(16), 0, 0, 0);

            searchView.post(new Runnable() {
                @Override
                public void run() {
                    searchView.requestFocus();
                    AndroidUtils.requestKeyboardFocus(searchView);
                }
            });

            navigationItemContainer.setView(searchViewWrapper, true);
            animateArrow(true, 0);
            callback.onSearchVisibilityChanged(true);
            return true;
        } else {
            return false;
        }
    }

    public boolean closeSearch() {
        return closeSearchInternal(false);
    }

    public void setNavigationItem(final boolean animate, final boolean pushing, final NavigationItem item) {
        closeSearchInternal(true);
        if (item.menu != null) {
            AndroidUtils.waitForMeasure(this, new AndroidUtils.OnMeasuredCallback() {
                @Override
                public boolean onMeasured(View view) {
                    setNavigationItemInternal(animate, pushing, false, item);
                    return true;
                }
            });
        } else {
            setNavigationItemInternal(animate, pushing, false, item);
        }
    }

    public void setCallback(ToolbarCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onClick(View v) {
        if (v == arrowMenuView) {
            if (callback != null) {
                callback.onMenuOrBackClicked(arrowMenuDrawable.getProgress() == 1f);
            }
        }
    }

    public void setArrowMenuProgress(float progress) {
        arrowMenuDrawable.setProgress(progress);
    }

    public void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public void onLoadViewRemoved(View view) {
        // TODO: this is kinda a hack
        if (view instanceof ViewGroup) {
            ((ViewGroup) view).removeAllViews();
        }
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
            arrowMenuView.setBackground(getAttrDrawable(getContext(), android.R.attr.selectableItemBackgroundBorderless));
        } else {
            arrowMenuView.setBackgroundResource(R.drawable.gray_background_selector);
        }

        leftButtonContainer.addView(arrowMenuView, new FrameLayout.LayoutParams(dp(56), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));

        navigationItemContainer = new LoadView(getContext());
        navigationItemContainer.setListener(this);
        addView(navigationItemContainer, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    private boolean closeSearchInternal(boolean fromSetNavigation) {
        if (search) {
            search = false;
            setNavigationItemInternal(true, false, true, navigationItem);
            AndroidUtils.hideKeyboard(navigationItemContainer);
            callback.onSearchVisibilityChanged(false);
            if (!fromSetNavigation) {
                animateArrow(navigationItem.hasBack, 0);
            }
            return true;
        } else {
            return false;
        }
    }

    private void setNavigationItemInternal(boolean animate, boolean pushing, boolean fromSearch, NavigationItem toItem) {
        final NavigationItem fromItem = navigationItem;

        if (!animate) {
            if (fromItem != null) {
                removeNavigationItem(fromItem);
            }
            setArrowMenuProgress(toItem.hasBack ? 1f : 0f);
        }

        toItem.view = createNavigationItemView(toItem);

        // use the LoadView animation when from a search
        if (fromSearch) {
            navigationItemContainer.setView(toItem.view, true);
        } else {
            navigationItemContainer.addView(toItem.view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        }

        final int duration = 300;
        final int offset = dp(16);
        final int delay = pushing ? 0 : 100;

        // Use the LoadView animation when from a search
        if (animate && !fromSearch) {
            toItem.view.setAlpha(0f);

            List<Animator> animations = new ArrayList<>(5);

            if (fromItem != null && fromItem.hasBack != toItem.hasBack) {
                animateArrow(toItem.hasBack, delay);
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
            set.setStartDelay(delay);
            set.playTogether(animations);
            set.start();
        }

        navigationItem = toItem;
    }

    private void removeNavigationItem(NavigationItem item) {
        item.view.removeAllViews();
        navigationItemContainer.removeView(item.view);
        item.view = null;
    }

    private LinearLayout createNavigationItemView(final NavigationItem item) {
        LinearLayout wrapper = (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.toolbar_menu, null);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);

        final TextView titleView = (TextView) wrapper.findViewById(R.id.title);
        titleView.setTypeface(AndroidUtils.ROBOTO_MEDIUM);
        titleView.setText(item.title);
        // black: titleView.setTextColor(Color.argb((int)(0.87 * 255.0), 0, 0, 0));

        if (item.middleMenu != null) {
            item.middleMenu.setAnchor(titleView, Gravity.LEFT, dp(5), dp(5));

            Drawable drawable = new DropdownArrowDrawable();
            titleView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);

            titleView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    item.middleMenu.show();
                }
            });
        }

        if (item.menu != null) {
            wrapper.addView(item.menu, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        }

        if (item.rightView != null) {
            item.rightView.setPadding(0, 0, dp(16), 0);
            wrapper.addView(item.rightView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        }

        AndroidUtils.waitForMeasure(titleView, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                if (item.middleMenu != null) {
                    item.middleMenu.setPopupWidth(Math.max(dp(200), titleView.getWidth()));
                }
                return false;
            }
        });

        return wrapper;
    }

    private void animateArrow(boolean toArrow, long delay) {
        float to = toArrow ? 1f : 0f;
        if (to != arrowMenuDrawable.getProgress()) {
            ValueAnimator arrowAnimation = ValueAnimator.ofFloat(arrowMenuDrawable.getProgress(), to);
            arrowAnimation.setDuration(300);
            arrowAnimation.setInterpolator(new DecelerateInterpolator(2f));
            arrowAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setArrowMenuProgress((float) animation.getAnimatedValue());
                }
            });
            arrowAnimation.setStartDelay(delay);
            arrowAnimation.start();
        }
    }

    public interface ToolbarCallback {
        void onMenuOrBackClicked(boolean isArrow);

        void onSearchVisibilityChanged(boolean visible);

        String getSearchHint();

        void onSearchEntered(String entered);
    }
}
