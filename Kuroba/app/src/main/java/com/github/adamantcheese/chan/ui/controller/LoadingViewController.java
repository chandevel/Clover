package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;

public class LoadingViewController extends BaseFloatingController {
    private TextView textView;
    private boolean indeterminate;

    public LoadingViewController(Context context, boolean indeterminate) {
        super(context);

        this.indeterminate = indeterminate;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!indeterminate) {
            textView = view.findViewById(R.id.progress_percent);
        }
    }

    @Override
    public boolean onBack() {
        return true;
    }

    public void updateProgress(int percent) {
        if (indeterminate) {
            throw new IllegalStateException("Cannot be used with indeterminate flag");
        }

        if (textView.getVisibility() != View.VISIBLE && percent > 0) {
            textView.setVisibility(View.VISIBLE);
        }

        textView.setText(String.valueOf(percent));
    }

    @Override
    protected int getLayoutId() {
        return R.layout.controller_loading_view;
    }

}
