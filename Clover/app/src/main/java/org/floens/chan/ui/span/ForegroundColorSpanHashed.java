package org.floens.chan.ui.span;

import android.os.Parcel;
import android.text.style.ForegroundColorSpan;

/**
 * A version of ForegroundColorSpan that has proper equals and hashCode implementations. Used to fix the hashcode result from SpannableStringBuilder.
 */
public class ForegroundColorSpanHashed extends ForegroundColorSpan {
    public ForegroundColorSpanHashed(int color) {
        super(color);
    }

    public ForegroundColorSpanHashed(Parcel src) {
        super(src);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ForegroundColorSpanHashed that = (ForegroundColorSpanHashed) o;

        return getForegroundColor() == that.getForegroundColor();

    }

    @Override
    public int hashCode() {
        return getForegroundColor();
    }
}
