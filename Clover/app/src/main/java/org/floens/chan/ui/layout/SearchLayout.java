/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.layout;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.utils.AndroidUtils;

import static org.floens.chan.utils.AndroidUtils.dp;

public class SearchLayout extends LinearLayout {
    private EditText searchView;
    private ImageView clearButton;

    public SearchLayout(Context context) {
        super(context);
    }

    public SearchLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCallback(final SearchLayoutCallback callback) {
        searchView = new EditText(getContext());
        searchView.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
        searchView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchView.setHintTextColor(0x88ffffff);
        searchView.setTextColor(0xffffffff);
        searchView.setSingleLine(true);
        searchView.setBackgroundResource(0);
        searchView.setPadding(0, 0, 0, 0);
        clearButton = new ImageView(getContext());
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setAlpha(s.length() == 0 ? 0.6f : 1.0f);
                callback.onSearchEntered(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    AndroidUtils.hideKeyboard(searchView);
                    callback.onSearchEntered(searchView.getText().toString());
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams searchViewParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        searchViewParams.gravity = Gravity.CENTER_VERTICAL;
        addView(searchView, searchViewParams);

        clearButton.setAlpha(0.6f);
        clearButton.setImageResource(R.drawable.ic_clear_white_24dp);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                searchView.setText("");
                AndroidUtils.requestKeyboardFocus(searchView);
            }
        });
        addView(clearButton, dp(48), LayoutParams.MATCH_PARENT);
    }

    public void setHintColor(int color) {
        searchView.setHintTextColor(color);
    }

    public void setTextColor(int color) {
        searchView.setTextColor(color);
    }

    public void setClearButtonImage(int image) {
        clearButton.setImageResource(image);
    }

    public void setText(String text) {
        searchView.setText(text);
    }

    public String getText() {
        return searchView.getText().toString();
    }

    public void setHint(String hint) {
        searchView.setHint(hint);
    }

    public void openKeyboard() {
        searchView.post(new Runnable() {
            @Override
            public void run() {
                AndroidUtils.requestViewAndKeyboardFocus(searchView);
            }
        });
    }

    public interface SearchLayoutCallback {
        void onSearchEntered(String entered);
    }
}
