package com.github.adamantcheese.chan.ui.controller;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;

public class LoadingViewController
        extends BaseFloatingController {
    private TextView textView;
    private ProgressBar progressBar;
    private final boolean indeterminate;

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

    @Override
    public boolean onBack() {
        return true; // consume back presses
    }

    /**
     * Hide a progress bar and instead of percentage any text may be shown
     * (cannot be used with indeterminate)
     */
    public void updateWithText(String text) {
        if (indeterminate) {
            return;
        }

        textView.setVisibility(VISIBLE);
        progressBar.setVisibility(GONE);
        textView.setText(text);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.controller_loading_view;
    }
}
