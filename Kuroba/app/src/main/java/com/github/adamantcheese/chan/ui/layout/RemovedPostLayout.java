package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.*;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.net.ImageLoadable;
import com.github.adamantcheese.chan.ui.view.ShapeablePostImageView;

import okhttp3.Call;
import okhttp3.HttpUrl;

public class RemovedPostLayout
        extends LinearLayout
        implements ImageLoadable {
    public TextView postNo;
    public TextView postComment;
    public CheckBox checkbox;
    public ShapeablePostImageView postImage;
    private Call thumbnailCall;
    private HttpUrl lastHttpUrl;

    public RemovedPostLayout(Context context) {
        super(context);
    }

    public RemovedPostLayout(
            Context context, @Nullable @org.jetbrains.annotations.Nullable AttributeSet attrs
    ) {
        super(context, attrs);
    }

    public RemovedPostLayout(
            Context context, @Nullable @org.jetbrains.annotations.Nullable AttributeSet attrs, int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        postNo = findViewById(R.id.removed_post_no);
        postComment = findViewById(R.id.removed_post_comment);
        checkbox = findViewById(R.id.removed_post_checkbox);
        postImage = findViewById(R.id.post_image);
    }

    @Override
    public HttpUrl getLastHttpUrl() {
        return lastHttpUrl;
    }

    @Override
    public void setLastHttpUrl(HttpUrl url) {
        lastHttpUrl = url;
    }

    @Override
    public Call getImageCall() {
        return thumbnailCall;
    }

    @Override
    public void setImageCall(Call call) {
        thumbnailCall = call;
    }
}
