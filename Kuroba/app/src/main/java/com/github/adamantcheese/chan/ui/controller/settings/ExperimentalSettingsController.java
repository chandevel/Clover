package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.widget.ArrayAdapter;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder;
import com.github.adamantcheese.chan.features.gesture_editor.AttachSide;
import com.github.adamantcheese.chan.features.gesture_editor.ExclusionZone;
import com.github.adamantcheese.chan.ui.controller.AdjustAndroid10GestureZonesController;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;

import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getScreenOrientation;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.JavaUtils.in;

public class ExperimentalSettingsController
        extends SettingsController {

    //  !!!!!!!!!!!!!!!!!!!!!!!!!!
    //  When changing the following indexes don't forget to update arrayAdapter
    //  !!!!!!!!!!!!!!!!!!!!!!!!!!
    private static final int[] LEFT_ZONES_INDEXES = new int[]{0, 2};
    private static final int[] RIGHT_ZONES_INDEXES = new int[]{1, 3};
    private static final int[] PORTRAIT_ORIENTATION_INDEXES = new int[]{0, 1};
    private static final int[] LANDSCAPE_ORIENTATION_INDEXES = new int[]{2, 3};

    private LinkSettingView resetExclusionZonesSetting;
    private LinkSettingView gestureExclusionZonesSetting;

    @Inject
    Android10GesturesExclusionZonesHolder exclusionZonesHolder;

    private final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);

    public ExperimentalSettingsController(Context context) {
        super(context);

        //  !!!!!!!!!!!!!!!!!!!!!!!!!!
        //  When changing the following list don't forget to update indexes in LEFT_ZONES_INDEXES,
        //  RIGHT_ZONES_INDEXES
        //  !!!!!!!!!!!!!!!!!!!!!!!!!!
        arrayAdapter.add(getString(R.string.setting_exclusion_zones_left_zone_portrait));
        arrayAdapter.add(getString(R.string.setting_exclusion_zones_right_zone_portrait));
        arrayAdapter.add(getString(R.string.setting_exclusion_zones_left_zone_landscape));
        arrayAdapter.add(getString(R.string.setting_exclusion_zones_right_zone_landscape));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_experimental_settings_title);

        if (!isAndroid10()) {
            resetExclusionZonesSetting.setEnabled(false);
            gestureExclusionZonesSetting.setEnabled(false);
        }
    }

    @Override
    protected void populatePreferences() {
        SettingsGroup group = new SettingsGroup(getString(R.string.experimental_settings_group));

        setupZonesEditor(group);
        setupZonesResetButton(group);

        requiresRestart.add(group.add(new BooleanSettingView(
                this,
                ChanSettings.okHttpAllowHttp2,
                R.string.setting_allow_okhttp_http2,
                R.string.setting_allow_okhttp_http2_ipv6_description
        )));

        requiresRestart.add(group.add(new BooleanSettingView(
                this,
                ChanSettings.okHttpAllowIpv6,
                R.string.setting_allow_okhttp_ipv6,
                R.string.setting_allow_okhttp_http2_ipv6_description
        )));

        groups.add(group);
    }

    private void setupZonesResetButton(SettingsGroup group) {
        resetExclusionZonesSetting = new LinkSettingView(
                this,
                R.string.setting_exclusion_zones_reset_zones,
                R.string.setting_exclusion_zones_reset_zones_description,
                (v) -> {
                    exclusionZonesHolder.resetZones();
                    ((StartActivity) context).restartApp();
                }
        );

        requiresUiRefresh.add(group.add(resetExclusionZonesSetting));
    }

    private void setupZonesEditor(SettingsGroup group) {
        gestureExclusionZonesSetting = new LinkSettingView(
                this,
                R.string.setting_exclusion_zones_editor,
                R.string.setting_exclusion_zones_editor_description,
                (v) -> showZonesDialog()
        );

        requiresUiRefresh.add(group.add(gestureExclusionZonesSetting));
    }

    private void showZonesDialog() {
        if (!isAndroid10()) {
            return;
        }

        new AlertDialog.Builder(context).setTitle(R.string.setting_exclusion_zones_actions_dialog_title)
                .setAdapter(arrayAdapter, (dialog, selectedIndex) -> {
                    onOptionClicked(selectedIndex);
                    dialog.dismiss();
                })
                .create()
                .show();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void onOptionClicked(int selectedIndex) {
        AttachSide attachSide;
        int orientation;

        if (in(selectedIndex, PORTRAIT_ORIENTATION_INDEXES)) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
        } else if (in(selectedIndex, LANDSCAPE_ORIENTATION_INDEXES)) {
            orientation = Configuration.ORIENTATION_LANDSCAPE;
        } else {
            throw new IllegalStateException("Unhandled orientation index " + selectedIndex);
        }

        if (getScreenOrientation() != orientation) {
            showToast(context, R.string.setting_exclusion_zones_wrong_phone_orientation);
            return;
        }

        if (in(selectedIndex, LEFT_ZONES_INDEXES)) {
            attachSide = AttachSide.Left;
        } else if (in(selectedIndex, RIGHT_ZONES_INDEXES)) {
            attachSide = AttachSide.Right;
        } else {
            // this will need to be updated if any swipe up/down actions are added to the application
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
        new AlertDialog.Builder(context).setTitle(R.string.setting_exclusion_zones_edit_or_remove_zone_title)
                .setPositiveButton(R.string.edit, (dialog, which) -> {
                    ExclusionZone skipZone = exclusionZonesHolder.getZoneOrNull(orientation, attachSide);
                    if (skipZone == null) {
                        throw new IllegalStateException(
                                "skipZone is null! " + "(orientation = " + orientation + ", attachSide = " + attachSide
                                        + ")");
                    }

                    showZoneEditorController(attachSide, skipZone);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.remove, ((dialog, which) -> {
                    exclusionZonesHolder.removeZone(orientation, attachSide);
                    showToast(context, R.string.setting_exclusion_zones_zone_removed);
                    dialog.dismiss();
                }))
                .create()
                .show();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void showZoneEditorController(AttachSide attachSide, @Nullable ExclusionZone skipZone) {
        AdjustAndroid10GestureZonesController adjustGestureZonesController =
                new AdjustAndroid10GestureZonesController(context);

        adjustGestureZonesController.setAttachSide(attachSide);
        adjustGestureZonesController.setSkipZone(skipZone);

        navigationController.presentController(adjustGestureZonesController);
    }
}
