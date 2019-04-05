package org.floens.chan.ui.controller;

import android.content.Context;
import android.support.constraint.ConstraintLayout;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;

public class ImageReencodeOptionsController extends Controller {

    private ConstraintLayout viewHolder;

    public ImageReencodeOptionsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.layout_image_reencoding);

        viewHolder = view.findViewById(R.id.reencode_image_view_holder);
    }
}
