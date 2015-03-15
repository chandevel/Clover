package org.floens.chan.ui.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.AnimationUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class SettingsController extends Controller implements AndroidUtils.OnMeasuredCallback {
    protected LinearLayout content;
    protected List<SettingsGroup> groups = new ArrayList<>();

    private boolean built = false;

    public SettingsController(Context context) {
        super(context);
    }

    @Override
    public void onShow() {
        super.onShow();

        AndroidUtils.waitForLayout(view, this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AndroidUtils.waitForLayout(view, this);
    }

    @Override
    public boolean onMeasured(View view) {
        setMargins();
        return true;
    }

    public void onPreferenceChange(SettingView item) {
        if ((item instanceof ListSettingView) || (item instanceof StringSettingView) || (item instanceof LinkSettingView)) {
            setDescriptionText(item.view, item.getTopDescription(), item.getBottomDescription());
        }
    }

    private void setMargins() {
        boolean tablet = view.getWidth() > dp(500); // TODO is tablet

        int margin = 0;
        if (tablet) {
            margin = (int) (view.getWidth() * 0.1);
        }

        int itemMargin = 0;
        if (tablet) {
            itemMargin = dp(16);
        }

        List<View> groups = AndroidUtils.findViewsById(content, R.id.group);
        for (View group : groups) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) group.getLayoutParams();
            params.leftMargin = margin;
            params.rightMargin = margin;
            group.setLayoutParams(params);
        }

        List<View> items = AndroidUtils.findViewsById(content, R.id.preference_item);
        for (View item : items) {
            item.setPadding(itemMargin, item.getPaddingTop(), itemMargin, item.getPaddingBottom());
        }
    }

    protected void setSettingViewVisibility(SettingView settingView, boolean visible, boolean animated) {
        if (animated) {
            AnimationUtils.animateHeight(settingView.view, visible);
        } else {
            settingView.view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        if (settingView.divider != null) {
            settingView.divider.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    protected void buildPreferences() {
        LayoutInflater inf = LayoutInflater.from(context);
        boolean firstGroup = true;
        for (SettingsGroup group : groups) {
            LinearLayout groupLayout = (LinearLayout) inf.inflate(R.layout.setting_group, content, false);
            ((TextView) groupLayout.findViewById(R.id.header)).setText(group.name);

            if (firstGroup) {
                firstGroup = false;
                ((LinearLayout.LayoutParams) groupLayout.getLayoutParams()).topMargin = 0;
            }

            content.addView(groupLayout);

            for (int i = 0; i < group.settingViews.size(); i++) {
                SettingView settingView = group.settingViews.get(i);

                ViewGroup preferenceView = null;
                String topValue = settingView.getTopDescription();
                String bottomValue = settingView.getBottomDescription();

                if ((settingView instanceof ListSettingView) || (settingView instanceof LinkSettingView) || (settingView instanceof StringSettingView)) {
                    preferenceView = (ViewGroup) inf.inflate(R.layout.setting_link, groupLayout, false);
                } else if (settingView instanceof BooleanSettingView) {
                    preferenceView = (ViewGroup) inf.inflate(R.layout.setting_boolean, groupLayout, false);
                }

                setDescriptionText(preferenceView, topValue, bottomValue);

                groupLayout.addView(preferenceView);

                settingView.setView(preferenceView);

                if (i < group.settingViews.size() - 1) {
                    settingView.divider = inf.inflate(R.layout.setting_divider, groupLayout, false);
                    groupLayout.addView(settingView.divider);
                }
            }
        }

        built = true;
    }

    private void setDescriptionText(View view, String topText, String bottomText) {
        ((TextView) view.findViewById(R.id.top)).setText(topText);

        final TextView bottom = ((TextView) view.findViewById(R.id.bottom));
        if (bottom != null) {
            if (built) {
                if (bottomText != null) {
                    bottom.setText(bottomText);
                }

                AnimationUtils.animateHeight(bottom, bottomText != null);
            } else {
                bottom.setText(bottomText);
                bottom.setVisibility(bottomText == null ? View.GONE : View.VISIBLE);
            }
        }
    }
}
