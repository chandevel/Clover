package com.github.adamantcheese.chan.ui.text;

import android.text.style.RelativeSizeSpan;

import androidx.annotation.Nullable;

import java.util.Objects;

public class RelativeSizeSpanHashed
        extends RelativeSizeSpan {
    public RelativeSizeSpanHashed(float proportion) {
        super(proportion);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        RelativeSizeSpanHashed that = (RelativeSizeSpanHashed) obj;

        return getSizeChange() == that.getSizeChange();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSizeChange());
    }
}
