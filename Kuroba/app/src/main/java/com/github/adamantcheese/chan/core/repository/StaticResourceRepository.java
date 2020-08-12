package com.github.adamantcheese.chan.core.repository;

import android.os.Handler;
import android.os.Looper;

public class StaticResourceRepository {
    public static final Handler mainHandler = new Handler(Looper.getMainLooper());
}
