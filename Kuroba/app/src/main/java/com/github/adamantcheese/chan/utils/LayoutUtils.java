package com.github.adamantcheese.chan.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.FrameLayout;

public class LayoutUtils {
    public static View inflate(Context context, int resId, ViewGroup root) {
        return LayoutInflater.from(context).inflate(resId, root);
    }

    public static View inflate(Context context, int resId, ViewGroup root, boolean attachToRoot) {
        return LayoutInflater.from(context).inflate(resId, root, attachToRoot);
    }

    public static ViewGroup inflate(Context context, int resId) {
        return (ViewGroup) LayoutInflater.from(context).inflate(resId, null);
    }
}
