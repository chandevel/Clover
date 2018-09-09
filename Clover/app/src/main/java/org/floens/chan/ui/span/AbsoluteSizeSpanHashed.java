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

		return getSize() == that.getSize() && getDip() == that.getDip();
    }

    @Override
    public int hashCode() {
        int result = getSize();
        result = 31 * result + (getDip() ? 1 : 0);
        return result;
    }
}
