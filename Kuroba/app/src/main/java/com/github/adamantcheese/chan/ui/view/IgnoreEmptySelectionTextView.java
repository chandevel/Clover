package com.github.adamantcheese.chan.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import static android.os.Build.VERSION;
import static android.os.Build.VERSION_CODES;

public class IgnoreEmptySelectionTextView
        extends AppCompatTextView {
    public IgnoreEmptySelectionTextView(Context context) {
        super(context);
    }

    public IgnoreEmptySelectionTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public IgnoreEmptySelectionTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean performLongClick() {
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            if (getSelectionStart() >= 0 && getSelectionEnd() >= getSelectionStart()) {
                String testSubsequence = TextUtils.substring(getText(), getSelectionStart(), getSelectionEnd());
                if (testSubsequence.length() > 0 && testSubsequence.trim().isEmpty()) {
                    // "Empty" selections of zero rendered width will crash on Android P and above on a drag action,
                    // which is handled by the long click listener of the textview; we override this method to handle
                    // this case first, so that no crashes occur
                    return true;
                }
            }
        }
        return super.performLongClick();
    }
}
