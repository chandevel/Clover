package com.github.adamantcheese.chan.utils;

import android.widget.SeekBar;

public interface DefaultOnSeekBarChangeListener
        extends SeekBar.OnSeekBarChangeListener {
    @Override
    default void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    default void onStopTrackingTouch(SeekBar seekBar) {}
}
