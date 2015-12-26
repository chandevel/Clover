package org.floens.chan.ui.span;

import android.os.Parcel;
import android.text.style.AbsoluteSizeSpan;

/**
 * A version of AbsoluteSizeSpan that has proper equals and hashCode implementations. Used to fix the hashcode result from SpannableStringBuilder.
 */
public class AbsoluteSizeSpanHashed extends AbsoluteSizeSpan {
    public AbsoluteSizeSpanHashed(int size) {
        super(size);
    }

    public AbsoluteSizeSpanHashed(int size, boolean dip) {
        super(size, dip);
    }

    public AbsoluteSizeSpanHashed(Parcel src) {
        super(src);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbsoluteSizeSpanHashed that = (AbsoluteSizeSpanHashed) o;

        if (getSize() != that.getSize()) return false;
        return getDip() == that.getDip();
    }

    @Override
    public int hashCode() {
        int result = getSize();
        result = 31 * result + (getDip() ? 1 : 0);
        return result;
    }
}
