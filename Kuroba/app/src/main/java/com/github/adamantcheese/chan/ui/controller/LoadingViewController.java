package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class LoadingViewController
        extends BaseFloatingController {
    private TextView textView;
    private ProgressBar progressBar;
    private boolean indeterminate;

    public LoadingViewController(Context context, boolean indeterminate) {
        super(context);

        this.indeterminate = indeterminate;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        textView = view.findViewById(R.id.text);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    // Disable the back button for this controller
    @Override
    public boolean onBack() {
        return true;
    }

    /**
     * Shows a progress bar with percentage in the center (cannot be used with indeterminate)
     */
    public void updateProgress(int percent) {
        BackgroundUtils.ensureMainThread();

        if (indeterminate) {
            throw new IllegalStateException("Cannot be used with indeterminate flag");
        }

        if (textView.getVisibility() != VISIBLE && percent > 0) {
            textView.setVisibility(VISIBLE);
        }

        if (progressBar.getVisibility() != VISIBLE) {
            progressBar.setVisibility(VISIBLE);
        }

        textView.setText(String.valueOf(percent));
    }

    /**
     * Hide a progress bar and instead of percentage any text may be shown
     * (cannot be used with indeterminate)
     */
    public void updateWithText(String text) {
        BackgroundUtils.ensureMainThread();

        if (indeterminate) {
            throw new IllegalStateException("Cannot be used with indeterminate flag");
        }

        if (textView.getVisibility() != VISIBLE) {
            textView.setVisibility(VISIBLE);
        }

        if (progressBar.getVisibility() == VISIBLE) {
            progressBar.setVisibility(GONE);
        }

        textView.setText(text);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.controller_loading_view;
    }
}
