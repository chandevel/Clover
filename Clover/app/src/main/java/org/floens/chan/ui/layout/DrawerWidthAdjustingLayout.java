package org.floens.chan.ui.layout;

import android.content.Context;
import android.support.v4.widget.DrawerLayout;
import android.util.AttributeSet;
import android.view.View;

import org.floens.chan.R;

import static org.floens.chan.utils.AndroidUtils.dp;

public class DrawerWidthAdjustingLayout extends DrawerLayout {
    public DrawerWidthAdjustingLayout(Context context) {
        super(context);
    }

    public DrawerWidthAdjustingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawerWidthAdjustingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
//        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
//        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        View drawer = findViewById(R.id.drawer);

        int width = Math.min(widthSize - dp(56), dp(56) * 6);
        if (drawer.getLayoutParams().width != width) {
            drawer.getLayoutParams().width = width;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
