/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.layout;

import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.requestKeyboardFocus;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.*;
import android.util.AttributeSet;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import com.github.adamantcheese.chan.R;

public class SearchLayout
        extends LinearLayout {
    private EditText searchView;
    private ImageView clearButton;

    private final boolean alwaysShowClear;
    private final boolean useCatalogColors;
    private SearchLayoutCallback callback = entered -> {};

    public SearchLayout(Context context) {
        this(context, null);
    }

    public SearchLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.layout_search, this, true);
        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SearchLayout);
        try {
            alwaysShowClear = a.getBoolean(R.styleable.SearchLayout_alwaysShowClear, false);
            useCatalogColors = a.getBoolean(R.styleable.SearchLayout_catalogColors, false);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        searchView = findViewById(R.id.search_view);
        clearButton = findViewById(R.id.clear_button);
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setVisibility(s.length() == 0 && !alwaysShowClear ? GONE : VISIBLE);
                callback.onSearchEntered(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                    && event.getAction() == KeyEvent.ACTION_DOWN || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(searchView);
                String text = getText();
                if (text.isEmpty()) {
                    callback.onClearPressedWhenEmpty();
                } else {
                    callback.onSearchEntered(text);
                }
                return true;
            }
            return false;
        });
        searchView.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                view.postDelayed(() -> hideKeyboard(view), 100);
            } else {
                view.postDelayed(() -> {
                    searchView.setSelection(searchView.getText().length());
                    requestKeyboardFocus(view);
                }, 100);
            }
        });
        searchView.requestFocus();
        clearButton.setOnClickListener(v -> {
            if (TextUtils.isEmpty(searchView.getText())) {
                callback.onClearPressedWhenEmpty();
            } else {
                searchView.setText("");
            }
            requestKeyboardFocus(searchView);
        });

        clearButton.setVisibility(alwaysShowClear ? VISIBLE : GONE);

        if (useCatalogColors) {
            searchView.setTextColor(Color.WHITE);
            searchView.setHintTextColor(0x88ffffff);
            clearButton.setImageTintList(null);
        }
    }

    public void setCallback(SearchLayoutCallback callback) {
        if (callback == null) callback = entered -> {};
        this.callback = callback;
    }

    public void setText(String text) {
        searchView.setText(text);
    }

    public String getText() {
        return searchView.getText().toString();
    }

    public interface SearchLayoutCallback {
        void onSearchEntered(String entered);

        default void onClearPressedWhenEmpty() {}
    }
}
