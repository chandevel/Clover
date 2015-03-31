package org.floens.chan.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.ChanApplication;

public class ThumbnailView extends ImageView implements ImageLoader.ImageListener {
    private String url;

    public ThumbnailView(Context context) {
        super(context);
    }

    public ThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setUrl(String url) {
        this.url = url;

        ImageLoader.ImageContainer container = ChanApplication.getVolleyImageLoader().get(url, this);
    }

    @Override
    public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
    }

    @Override
    public void onErrorResponse(VolleyError error) {

    }
}
