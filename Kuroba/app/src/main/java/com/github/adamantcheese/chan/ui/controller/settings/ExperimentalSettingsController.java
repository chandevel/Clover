package com.github.adamantcheese.chan.ui.controller.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.widget.ArrayAdapter;

import androidx.annotation.RequiresApi;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder;
import com.github.adamantcheese.chan.features.gesture_editor.AttachSide;
import com.github.adamantcheese.chan.features.gesture_editor.ExclusionZone;
import com.github.adamantcheese.chan.ui.controller.AdjustAndroid10GestureZonesController;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getScreenOrientation;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ExperimentalSettingsController
        extends SettingsController {

    //  !!!!!!!!!!!!!!!!!!!!!!!!!!
    //  When changing the following indexes don't forget to update arrayAdapter
    //  !!!!!!!!!!!!!!!!!!!!!!!!!!
    private static final int[] LEFT_ZONES_INDEXES = new int[]{0, 2};
    private static final int[] RIGHT_ZONES_INDEXES = new int[]{1, 3};
    private static final int[] PORTRAIT_ORIENTATION_INDEXES = new int[]{0, 1};
    private static final int[] LANDSCAPE_ORIENTATION_INDEXES = new int[]{2, 3};

    @Inject
    Android10GesturesExclusionZonesHolder exclusionZonesHolder;

    private final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
            context,
            android.R.layout.simple_list_item_1
    );

    public ExperimentalSettingsController(Context context) {
        super(context);

        //  !!!!!!!!!!!!!!!!!!!!!!!!!!
        //  When changing the following list don't forget to update indexes in LEFT_ZONES_INDEXES,
        //  RIGHT_ZONES_INDEXES
        //  !!!!!!!!!!!!!!!!!!!!!!!!!!
        arrayAdapter.add("Left zone (Portrait orientation)");
        arrayAdapter.add("Right zone (Portrait orientation)");
        arrayAdapter.add("Left zone (Landscape orientation)");
        arrayAdapter.add("Right zone (Landscape orientation)");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_experimental_settings_title);

        setupLayout();
        populatePreferences();
        buildPreferences();

        inject(this);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);
    }

    private void populatePreferences() {
        SettingsGroup group = new SettingsGroup(getString(R.string.experimental_settings_group));

        group.add(new BooleanSettingView(
                this,
                ChanSettings.videoStream,
                R.string.setting_video_stream,
                R.string.setting_video_stream_description
        ));

        setupConcurrentFileDownloadingChunksSetting(group);

        setUpZonesEditor(group);
        setUpZonesResetButton(group);

        groups.add(group);
    }

    private void setupConcurrentFileDownloadingChunksSetting(SettingsGroup group) {
        List<ListSettingView.Item> items = new ArrayList<>();

        for (ChanSettings.ConcurrentFileDownloadingChunks setting : ChanSettings.ConcurrentFileDownloadingChunks.values()) {
            items.add(new ListSettingView.Item<>(setting.getKey(), setting));
        }

        requiresRestart.add(group.add(new ListSettingView<ChanSettings.ConcurrentFileDownloadingChunks>(
                this,
                ChanSettings.concurrentDownloadChunkCount,
                getString(R.string.settings_concurrent_file_downloading_name),
                items
        ) {
            @Override
            public String getBottomDescription() {
                return getString(R.string.settings_concurrent_file_downloading_description) + "\n\n" + items.get(
                        selected).name;
            }
        }));
    }

    private void setUpZonesResetButton(SettingsGroup group) {
        LinkSettingView resetExclusionZonesSetting = new LinkSettingView(
                this,
                // TODO(gestures): strings!
                "Reset exclusion zones",
                "All exclusion zones will be deleted (The app will have to be restarted)",
                (v) -> {
                    exclusionZonesHolder.resetZones();
                    ((StartActivity) context).restartApp();
                }
        );

        if (!isAndroid10()) {
            resetExclusionZonesSetting.setEnabled(false);
        }
        requiresUiRefresh.add(group.add(resetExclusionZonesSetting));
    }

    private void setUpZonesEditor(SettingsGroup group) {
        LinkSettingView gestureExclusionZonesSetting = new LinkSettingView(
                this,
                // TODO(gestures): strings!
                "Android 10 gesture exclusion zones",
                "Adjust zones where new Android 10 gestures will be disabled. " +
                        "You will have to set 4 zones (Left/Right for Portrait/Landscape orientations). " +
                        "You will have to rotate your phone/tablet to set zones for different orientations",
                (v) -> showZonesDialog()
        );

        if (!isAndroid10()) {
            gestureExclusionZonesSetting.setEnabled(false);
        }
        requiresUiRefresh.add(group.add(gestureExclusionZonesSetting));
    }

    private void showZonesDialog() {
        if (!isAndroid10()) {
            showToast("Can only be used on Android 10 and above");
            return;
        }

        new AlertDialog.Builder(context)
                .setTitle("Android 10 exclusion zones actions")
                .setAdapter(arrayAdapter, (dialog, selectedIndex) -> {
                    onOptionClicked(selectedIndex);
                    dialog.dismiss();
                })
                .create()
                .show();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void onOptionClicked(int selectedIndex) {
        AttachSide attachSide = null;
        int orientation = -1;

        if (in(selectedIndex, PORTRAIT_ORIENTATION_INDEXES)) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
        } else if (in(selectedIndex, LANDSCAPE_ORIENTATION_INDEXES)) {
            orientation = Configuration.ORIENTATION_LANDSCAPE;
        } else {
            throw new IllegalStateException("Unhandled orientation index " + selectedIndex);
        }

        if (getScreenOrientation() != orientation) {
            showToast("Wrong phone orientation, you need to rotate your " +
                    "phone 90 degrees to any side before using this option");
            return;
        }

        if (in(selectedIndex, LEFT_ZONES_INDEXES)) {
            attachSide = AttachSide.Left;
        } else if (in(selectedIndex, RIGHT_ZONES_INDEXES)) {
            attachSide = AttachSide.Right;
        } else {
            throw new IllegalStateException("Unhandled AttachSide index " + selectedIndex);
        }

        if (exclusionZonesHolder.getZoneOrNull(orientation, attachSide) != null) {
            showEditOrRemoveZoneDialog(orientation, attachSide);
            return;
        }

        showZoneEditorController(attachSide, null);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void showEditOrRemoveZoneDialog(int orientation, AttachSide attachSide) {
        new AlertDialog.Builder(context)
                .setTitle("Edit or remove zone?")
                .setPositiveButton("Edit", (dialog, which) -> {
                    ExclusionZone skipZone = exclusionZonesHolder.getZoneOrNull(orientation, attachSide);
                    if (skipZone == null) {
                        throw new IllegalStateException("skipZone is null! " +
                                "(orientation = " + orientation + ", attachSide = " + attachSide + ")");
                    }

                    showZoneEditorController(attachSide, skipZone);
                    dialog.dismiss();
                })
                .setNegativeButton("Remove", ((dialog, which) -> {
                    exclusionZonesHolder.removeZone(orientation, attachSide);
                    showToast("Zone removed");
                    dialog.dismiss();
                }))
                .create()
                .show();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void showZoneEditorController(AttachSide attachSide, @Nullable ExclusionZone skipZone) {
        AdjustAndroid10GestureZonesController adjustGestureZonesController
                = new AdjustAndroid10GestureZonesController(context);

        adjustGestureZonesController.setAttachSide(attachSide);
        adjustGestureZonesController.setSkipZone(skipZone);

        navigationController.presentController(adjustGestureZonesController);
    }

    private boolean in(int value, int[] array) {
        for (int i : array) {
            if (value == i) {
                return true;
            }
        }

        return false;
    }
}
