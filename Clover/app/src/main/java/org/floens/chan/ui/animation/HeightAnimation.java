package org.floens.chan.ui.animation;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class HeightAnimation extends Animation {
    final int startHeight;
    final int targetHeight;
    View view;

    public HeightAnimation(View view, int startHeight, int targetHeight, int duration) {
        this.view = view;
        this.startHeight = startHeight;
        this.targetHeight = targetHeight;
        setDuration(duration);
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        view.getLayoutParams().height = (int) (startHeight + (targetHeight - startHeight) * interpolatedTime);
        view.requestLayout();
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }
}
