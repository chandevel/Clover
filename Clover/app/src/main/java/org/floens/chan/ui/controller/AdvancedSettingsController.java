package org.floens.chan.ui.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.fragment.FolderPickFragment;
import org.floens.chan.ui.preferences.BooleanSettingView;
import org.floens.chan.ui.preferences.LinkSettingView;
import org.floens.chan.ui.preferences.SettingsController;
import org.floens.chan.ui.preferences.SettingsGroup;

import java.io.File;

public class AdvancedSettingsController extends SettingsController {
    private static final String TAG = "AdvancedSettingsController";

    private LinkSettingView saveLocation;

    public AdvancedSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.settings_screen_advanced);

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        buildPreferences();
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(string(R.string.settings_group_advanced));

        saveLocation = (LinkSettingView) settings.add(new LinkSettingView(this, string(R.string.setting_save_folder), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File dir = ChanSettings.getImageSaveDirectory();
                if (!dir.mkdirs() && !dir.isDirectory()) {
                    new AlertDialog.Builder(context).setMessage(R.string.setting_save_folder_error_create_folder).show();
                } else {
                    FolderPickFragment frag = FolderPickFragment.newInstance(new FolderPickFragment.FolderPickListener() {
                        @Override
                        public void folderPicked(File path) {
                            ChanSettings.saveLocation.set(path.getAbsolutePath());
                            setSaveLocationDescription();
                        }
                    }, dir);
                    ((Activity) context).getFragmentManager().beginTransaction().add(frag, null).commit();
                }
            }
        }));
        setSaveLocationDescription();

        settings.add(new BooleanSettingView(this, ChanSettings.saveOriginalFilename, string(R.string.setting_save_original_filename), null));
        settings.add(new BooleanSettingView(this, ChanSettings.shareUrl, string(R.string.setting_share_url), string(R.string.setting_share_url_description)));
        settings.add(new BooleanSettingView(this, ChanSettings.networkHttps, string(R.string.setting_network_https), string(R.string.setting_network_https_description)));
        settings.add(new BooleanSettingView(this, ChanSettings.forcePhoneLayout, string(R.string.setting_force_phone_layout), null));
        settings.add(new BooleanSettingView(this, ChanSettings.anonymize, string(R.string.preference_anonymize), null));
        settings.add(new BooleanSettingView(this, ChanSettings.anonymizeIds, string(R.string.preference_anonymize_ids), null));
        settings.add(new BooleanSettingView(this, ChanSettings.repliesButtonsBottom, string(R.string.setting_buttons_bottom), null));

        groups.add(settings);
    }

    private void setSaveLocationDescription() {
        saveLocation.setDescription(ChanSettings.saveLocation.get());
    }
}
