package com.github.adamantcheese.chan.ui.widget;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;

public class DefaultAlertDialog {
    // This exists because for some reason, alertDialogTheme in styles.xml doesn't seem to properly work
    // However this constructor DOES, so it's used throughout the application
    public static AlertDialog.Builder getDefaultAlertBuilder(Context context) {
        return new AlertDialog.Builder(context, R.style.AlertDialogTheme);
    }
}
