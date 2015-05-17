package org.floens.chan.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

public class SelectionListeningEditText extends EditText {
    private SelectionChangedListener listener;

    public SelectionListeningEditText(Context context) {
        super(context);
    }

    public SelectionListeningEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SelectionListeningEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSelectionChangedListener(SelectionChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);

        if (listener != null) {
            listener.onSelectionChanged(selStart, selEnd);
        }
    }

    public interface SelectionChangedListener {
        void onSelectionChanged(int selStart, int selEnd);
    }
}
