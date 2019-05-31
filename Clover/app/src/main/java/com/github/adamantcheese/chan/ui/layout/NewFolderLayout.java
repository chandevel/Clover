package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.github.adamantcheese.chan.R;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;

public class NewFolderLayout extends LinearLayout {
    private EditText folderName;

    public NewFolderLayout(Context context) {
        this(context, null);
    }

    public NewFolderLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NewFolderLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        folderName = findViewById(R.id.new_folder);
        folderName.setText(getApplicationLabel());
    }

    public String getFolderName() {
        return folderName.getText().toString();
    }
}
