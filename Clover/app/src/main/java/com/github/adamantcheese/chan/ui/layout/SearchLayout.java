/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

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
        searchView.setHint(getString(R.string.search_hint));
        searchView.setHintTextColor(getAttrColor(getContext(), R.attr.text_color_hint));
        searchView.setTextColor(getAttrColor(getContext(), R.attr.text_color_primary));
        searchView.setSingleLine(true);
        searchView.setBackgroundResource(0);
        searchView.setPadding(0, 0, 0, 0);
        clearButton = new ImageView(getContext());
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setAlpha(s.length() == 0 ? 0.0f : 1.0f);
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
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                AndroidUtils.hideKeyboard(searchView);
                callback.onSearchEntered(getText());
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams searchViewParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        searchViewParams.gravity = Gravity.CENTER_VERTICAL;
        addView(searchView, searchViewParams);

        clearButton.setAlpha(0f);
        clearButton.setImageResource(R.drawable.ic_clear_black_24dp);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setOnClickListener(v -> {
            searchView.setText("");
            AndroidUtils.requestKeyboardFocus(searchView);
        });
        addView(clearButton, dp(48), LayoutParams.MATCH_PARENT);
    }

    public void setText(String text) {
        searchView.setText(text);
    }

    public String getText() {
        return searchView.getText().toString();
    }

    public void setCatalogSearchColors() {
        searchView.setTextColor(0xffffffff);
        searchView.setHintTextColor(0x88ffffff);
        clearButton.setImageResource(R.drawable.ic_clear_white_24dp);
    }

    public void openKeyboard() {
        searchView.post(() -> AndroidUtils.requestViewAndKeyboardFocus(searchView));
    }

    public interface SearchLayoutCallback {
        void onSearchEntered(String entered);
    }
}
