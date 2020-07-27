package com.github.adamantcheese.chan;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

public interface DefaultActivityLifecycleCallbacks
        extends Application.ActivityLifecycleCallbacks {

    default void onActivityStarted(@NonNull Activity activity) {}

    default void onActivityStopped(@NonNull Activity activity) {}

    default void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {}

    default void onActivityResumed(@NonNull Activity activity) {}

    default void onActivityPaused(@NonNull Activity activity) {}

    default void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    default void onActivityDestroyed(@NonNull Activity activity) {}
}
