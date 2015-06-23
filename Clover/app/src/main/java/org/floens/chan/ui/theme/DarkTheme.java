package org.floens.chan.ui.theme;

import org.floens.chan.R;

public class DarkTheme extends Theme {
    public DarkTheme(String displayName, String name, int resValue, ThemeHelper.PrimaryColor primaryColor) {
        super(displayName, name, resValue, primaryColor);
        isLightTheme = false;
    }

    public void resolveDrawables() {
        super.resolveDrawables();
        settingsDrawable = new ThemeDrawable(R.drawable.ic_settings_white_24dp, 1f);
        imageDrawable = new ThemeDrawable(R.drawable.ic_image_white_24dp, 1f);
        sendDrawable = new ThemeDrawable(R.drawable.ic_send_white_24dp, 1f);
        clearDrawable = new ThemeDrawable(R.drawable.ic_clear_white_24dp, 1f);
        backDrawable = new ThemeDrawable(R.drawable.ic_arrow_back_white_24dp, 1f);
        doneDrawable = new ThemeDrawable(R.drawable.ic_done_white_24dp, 1f);
        historyDrawable = new ThemeDrawable(R.drawable.ic_history_white_24dp, 1f);
        listAddDrawable = new ThemeDrawable(R.drawable.ic_playlist_add_white_24dp, 1f);
    }
}
