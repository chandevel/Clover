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
package com.github.adamantcheese.chan.ui.text.spans;

import android.text.style.AbsoluteSizeSpan;

import java.util.Objects;

/**
 * A version of AbsoluteSizeSpan that has proper equals and hashCode implementations. Used to fix the hashcode result from SpannableStringBuilder.
 */
public class AbsoluteSizeSpanHashed
        extends AbsoluteSizeSpan {
    public AbsoluteSizeSpanHashed(int size) {
        super(size);
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
        return Objects.hash(getSize(), getDip());
    }
}
