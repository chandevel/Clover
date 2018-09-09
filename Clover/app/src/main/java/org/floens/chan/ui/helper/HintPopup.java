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
package org.floens.chan.ui.helper;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.utils.AndroidUtils;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getString;

public class HintPopup {
    public static HintPopup show(Context context, View anchor, int text) {
        return show(context, anchor, getString(text));
    }

    public static HintPopup show(final Context context, final View anchor, final String text) {
        return show(context, anchor, text, 0, 0);
    }

    public static HintPopup show(final Context context, final View anchor, final String text, final int offsetX, final int offsetY) {
        HintPopup hintPopup = new HintPopup(context, anchor, text, offsetX, offsetY, false);
        hintPopup.show();
        return hintPopup;
    }

    private PopupWindow popupWindow;
    private ViewGroup popupView;
    private final View anchor;
    private String text;
    private final int offsetX;
    private final int offsetY;
    private final boolean top;
    private boolean dismissed;
    private boolean rightAligned = true;
    private boolean wiggle = false;

    public HintPopup(Context context, final View anchor, final String text,
                     final int offsetX, final int offsetY, final boolean top) {
        this.anchor = anchor;
        this.text = text;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.top = top;

        createView(context);
    }

    @SuppressLint("InflateParams")
    private void createView(Context context) {
        popupView = (ViewGroup) LayoutInflater.from(context)
                .inflate(top ? R.layout.popup_hint_top : R.layout.popup_hint, null);

		TextView textView = popupView.findViewById(R.id.text);
        textView.setText(text);

        popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        popupView.setOnClickListener(v -> {
//                popupWindow.dismiss();
        });
    }

    public void show() {
        AndroidUtils.runOnUiThread(() -> {
            if (!dismissed) {
                popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                // TODO: cleanup
                int xoff;
                if (rightAligned) {
                    xoff = -popupView.getMeasuredWidth() + anchor.getWidth() + offsetX - dp(2);
                } else {
                    xoff = -popupView.getMeasuredWidth() + offsetX - dp(2);
                }
                int yoff = -dp(25) + offsetY + (top ? -anchor.getHeight() - dp(30) : 0);
                popupWindow.showAsDropDown(anchor, xoff, yoff);

                if (wiggle) {
                    TimeInterpolator wiggleInterpolator = input ->
                            (float) Math.sin(60 * input * 2.0 * Math.PI);

                    popupView.animate()
                            .translationY(dp(2))
                            .setInterpolator(wiggleInterpolator)
                            .setDuration(60000)
                            .start();
                }

                if (!rightAligned) {
                    View arrow = popupView.findViewById(R.id.arrow);
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) arrow.getLayoutParams();
					lp.gravity = Gravity.START;
                    arrow.setLayoutParams(lp);
                }
            }
        }, 400);

        // popupView.postDelayed(popupWindow::dismiss, 5000);
    }

    public void alignLeft() {
        rightAligned = false;
    }

    public void wiggle() {
        wiggle = true;
    }

    public void dismiss() {
        popupWindow.dismiss();
        dismissed = true;
    }
}
