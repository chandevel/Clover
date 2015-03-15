package org.floens.chan.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.Button;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;

public class ImageViewController extends Controller implements View.OnClickListener {
    private Button button;

    public ImageViewController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_view_image);

        button = (Button) view.findViewById(R.id.button);
        button.setOnClickListener(this);
    }

    @Override
    public boolean onBack() {
        stopPresenting();
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == button) {
            stopPresenting();
        }
    }
}
