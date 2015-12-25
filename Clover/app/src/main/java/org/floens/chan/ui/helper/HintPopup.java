package org.floens.chan.ui.helper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.floens.chan.R;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getString;

public class HintPopup {
    public static PopupWindow show(Context context, View anchor, int text) {
        return show(context, anchor, getString(text));
    }

    public static PopupWindow show(final Context context, final View anchor, final String text) {
        final LinearLayout popupView = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.popup_hint, null);

        TextView textView = (TextView) popupView.findViewById(R.id.text);
        textView.setText(text);

        final PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        popupView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
            }
        });

        popupView.postDelayed(new Runnable() {
            @Override
            public void run() {
                popupWindow.showAsDropDown(anchor, 0, -dp(25));
            }
        }, 100);

        popupView.postDelayed(new Runnable() {
            @Override
            public void run() {
                popupWindow.dismiss();
            }
        }, 7500);

        return popupWindow;
    }
}
