package org.floens.chan.ui.view;

import org.floens.chan.utils.SimpleAnimatorListener;

import android.animation.Animator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * Container for a view with an ProgressBar. Toggles between the view and a ProgressBar.
 */
public class LoadView extends FrameLayout {
    public int fadeDuration = 100;
    
    private View currentView;
    
    public LoadView(Context context) {
        super(context);
        init();
    }
    
    public LoadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public LoadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setView(null);
    }
    
    /**
     * Set the content of this container. It will fade the old one out with the new one.
     * Set view to null to show the progressbar.
     * @param view the view or null for a progressbar.
     */
    public void setView(View view) {
        if (view == null) {
            LinearLayout layout = new LinearLayout(getContext());
            layout.setGravity(Gravity.CENTER);
            
            ProgressBar pb = new ProgressBar(getContext());
            layout.addView(pb);
            view = layout;
        }
        
        while (getChildCount() > 2) {
            removeViewAt(0);
        }
        
        if (currentView != null) {
            final View tempView = currentView;
            currentView.animate().setDuration(fadeDuration).alpha(0).setListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeView(tempView);
                }
            });
        }
        
        addView(view);
        view.setAlpha(0f);
        view.animate().setDuration(fadeDuration).alpha(1f);
        
        currentView = view;
    }
}





