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
package com.github.adamantcheese.chan.ui.view;

import android.content.ClipData;
import android.content.Context;
import android.text.Spanned;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getClipboardManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;

public class SelectionListeningEditText
        extends AppCompatEditText {
    private SelectionChangedListener listener;
    private boolean plainTextPaste = false;

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
    public boolean isSuggestionsEnabled() {
        // this is due to an issue where suggestions are not run synchronously, so the suggestion system tries to generate
        // a set of suggestions, but if you delete text while this occurs, an index out of bounds exception will be thrown
        // as obviously you are now out of the range it was calculating suggestions for
        // this is solved on Android 10+, but not below; suggestions are disabled for these Android versions
        // https://issuetracker.google.com/issues/140891676
        // autocorrect still functions, but is only on the keyboard, not through a popup window (which is where the crash happens)
        if (isAndroid10()) return super.isSuggestionsEnabled();
        return false;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);

        if (listener != null) {
            listener.onSelectionChanged();
        }
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (getText() == null) return false;
        int start = getSelectionStart();
        int end = getSelectionEnd();
        int min = isFocused() ? Math.max(0, Math.min(start, end)) : 0;
        int max = isFocused() ? Math.max(0, Math.max(start, end)) : getText().length();
        if (id == android.R.id.paste && plainTextPaste) {
            //this code is basically a duplicate of the plain text paste functionality for later API versions
            ClipData clip = getClipboardManager().getPrimaryClip();
            if (clip != null) {
                boolean didFirst = false;
                for (int i = 0; i < clip.getItemCount(); i++) {
                    // Get an item as text and remove all spans by toString().
                    final CharSequence text = clip.getItemAt(i).coerceToText(getContext());
                    final CharSequence paste = (text instanceof Spanned) ? text.toString() : text;
                    if (paste != null) {
                        if (!didFirst) {
                            setSelection(max);
                            getText().replace(min, max, paste);
                            didFirst = true;
                        } else {
                            getText().insert(getSelectionEnd(), "\n");
                            getText().insert(getSelectionEnd(), paste);
                        }
                    }
                }
            }
            return true;
        }
        return super.onTextContextMenuItem(id);
    }

    public void setPlainTextPaste(boolean plainTextPaste) {
        this.plainTextPaste = plainTextPaste;
    }

    public interface SelectionChangedListener {
        void onSelectionChanged();
    }
}
