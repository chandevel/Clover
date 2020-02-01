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
package com.github.adamantcheese.chan.ui.helper;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.BackgroundUtils.runOnUiThread;

public class HintPopup {
    public static HintPopup show(Context context, View anchor, int text) {
        return show(context, anchor, getString(text));
    }

    public static HintPopup show(final Context context, final View anchor, final String text) {
        return show(context, anchor, text, 0, 0);
    }

    public static HintPopup show(
            final Context context, final View anchor, final String text, final int offsetX, final int offsetY
    ) {
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
    //centered enough, not exact
    private boolean centered = false;
    private boolean wiggle = false;

    public HintPopup(
            Context context,
            final View anchor,
            final String text,
            final int offsetX,
            final int offsetY,
            final boolean top
    ) {
        this.anchor = anchor;
        this.text = text;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.top = top;

        createView(context);
    }

    @SuppressLint("InflateParams")
    private void createView(Context context) {
        popupView = inflate(context, top ? R.layout.popup_hint_top : R.layout.popup_hint);
        popupView.setOnClickListener((view) -> dismiss());

        TextView textView = popupView.findViewById(R.id.text);
        textView.setText(text);

        popupWindow = new PopupWindow(popupView, WRAP_CONTENT, WRAP_CONTENT);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    public void show() {
        runOnUiThread(() -> {
            if (!dismissed) {
                popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                // TODO: cleanup
                int xoff;
                if (!centered) {
                    xoff = -popupView.getMeasuredWidth() + anchor.getWidth() + offsetX - dp(2);
                } else {
                    xoff = -popupView.getMeasuredWidth() + offsetX - dp(2);
                }
                int yoff = -dp(25) + offsetY + (top ? -anchor.getHeight() - dp(30) : 0);
                popupWindow.showAsDropDown(anchor, xoff, yoff);

                if (wiggle) {
                    TimeInterpolator wiggler = input -> (float) Math.sin(60 * input * 2.0 * Math.PI);

                    popupView.animate().translationY(dp(2)).setInterpolator(wiggler).setDuration(60000).start();
                }
            }
        }, 400);
    }

    public void alignCenter() {
        centered = true;
    }

    public void wiggle() {
        wiggle = true;
    }

    public void dismiss() {
        popupWindow.dismiss();
        dismissed = true;
    }
}
