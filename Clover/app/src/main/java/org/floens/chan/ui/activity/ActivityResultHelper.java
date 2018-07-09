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
package org.floens.chan.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.SparseArray;

import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Abstraction of the startActivityForResult and onActivityResult calls. It autogenerates
 * a result code and adds a callback interface. Activities must implement the
 * ActivityResultStarter interface and the application must implement the
 * ApplicationActivitiesProvider interface.
 */
public class ActivityResultHelper {
    private static final String TAG = "ActivityResultHelper";

    private Context applicationContext;

    private int resultCounter = 100;

    @Inject
    public ActivityResultHelper(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void getResultFromIntent(Intent intent, ActivityResultCallback callback) {
        resultCounter++;

        ActivityResultStarter starter = findStarter();
        if (starter == null) {
            Logger.e(TAG, "Could not find an active activity to use.");
            callback.onActivityResult(Activity.RESULT_CANCELED, null);
            return;
        }

        starter.startActivityForResultWithCallback(intent, resultCounter, callback);
    }

    private ActivityResultStarter findStarter() {
        List<Activity> activities = ((ApplicationActivitiesProvider) applicationContext).getActivities();
        List<ActivityResultStarter> starters = new ArrayList<>(1);
        for (Activity activity : activities) {
            if (activity instanceof ActivityResultStarter) {
                starters.add((ActivityResultStarter) activity);
            }
        }

        if (starters.isEmpty()) {
            return null;
        }

        if (starters.size() > 1) {
            // Give priority to the resumed activities.
            for (ActivityResultStarter starter : starters) {
                if (starter.isActivityResumed()) {
                    return starter;
                }
            }
        }

        return starters.get(0);
    }

    public interface ActivityResultStarter {
        boolean isActivityResumed();

        void startActivityForResultWithCallback(Intent intent, int requestCode,
                                                ActivityResultCallback callback);
    }

    public interface ApplicationActivitiesProvider {
        /**
         * Get all created activities. You can use Application.ActivityLifecycleCallbacks
         * to track the activities that are between the onCreate and onDestroy stage.
         *
         * @return a list of created activities.
         */
        List<Activity> getActivities();
    }

    public interface ActivityResultCallback {
        void onActivityResult(int resultCode, Intent result);
    }

    /**
     * Helper class for Activities that implement the ActivityResultStarter interface.
     */
    public static class ActivityStarterHelper {
        private SparseArray<ActivityResultCallback> activityResultCallbacks
                = new SparseArray<>();
        private boolean isResumed = false;

        public void onResume() {
            isResumed = true;
        }

        public void onPause() {
            isResumed = false;
        }

        public boolean isActivityResumed() {
            return isResumed;
        }

        public void startActivityForResult(Activity activity, Intent intent,
                                           int requestCode, ActivityResultCallback callback) {
            if (activityResultCallbacks.indexOfKey(requestCode) >= 0) {
                throw new IllegalArgumentException("requestCode " + requestCode + " already used");
            }
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivityForResult(intent, requestCode);
                activityResultCallbacks.put(requestCode, callback);
            } else {
                Logger.e(TAG, "Can't start activity for result, intent does not resolve.");
                callback.onActivityResult(Activity.RESULT_CANCELED, null);
            }
        }

        public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
            if (activityResultCallbacks.indexOfKey(requestCode) >= 0) {
                ActivityResultHelper.ActivityResultCallback callback =
                        activityResultCallbacks.get(requestCode);
                activityResultCallbacks.delete(requestCode);

                callback.onActivityResult(resultCode, data);
                return true;
            }

            return false;
        }
    }
}
