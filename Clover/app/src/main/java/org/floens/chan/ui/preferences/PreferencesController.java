package org.floens.chan.ui.preferences;

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

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class PreferencesController extends Controller implements AndroidUtils.OnMeasuredCallback {
    protected LinearLayout content;
    protected List<PreferenceGroup> groups = new ArrayList<>();

    public PreferencesController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
    public void onMeasured(View view, int width, int height) {
        setMargins();
    }

    public void onPreferenceChange(PreferenceItem item) {
        if (item instanceof ListPreference) {
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

    protected void buildPreferences() {
        LayoutInflater inf = LayoutInflater.from(context);
        boolean firstGroup = true;
        for (PreferenceGroup group : groups) {
            LinearLayout groupLayout = (LinearLayout) inf.inflate(R.layout.preference_group, content, false);
            ((TextView) groupLayout.findViewById(R.id.header)).setText(group.name);

            if (firstGroup) {
                firstGroup = false;
                ((LinearLayout.LayoutParams) groupLayout.getLayoutParams()).topMargin = 0;
            }

            content.addView(groupLayout);

            for (int i = 0; i < group.preferenceItems.size(); i++) {
                PreferenceItem preferenceItem = group.preferenceItems.get(i);

                ViewGroup preferenceView = null;
                String topValue = preferenceItem.getTopDescription();
                String bottomValue = preferenceItem.getBottomDescription();

                if ((preferenceItem instanceof ListPreference) || (preferenceItem instanceof LinkPreference)) {
                    preferenceView = (ViewGroup) inf.inflate(R.layout.preference_link, groupLayout, false);
                } else if (preferenceItem instanceof BooleanPreference) {
                    preferenceView = (ViewGroup) inf.inflate(R.layout.preference_boolean, groupLayout, false);
                }

                setDescriptionText(preferenceView, topValue, bottomValue);

                groupLayout.addView(preferenceView);

                preferenceItem.setView(preferenceView);

                if (i < group.preferenceItems.size() - 1) {
                    inf.inflate(R.layout.preference_divider, groupLayout, true);
                }
            }
        }
    }

    private void setDescriptionText(View view, String topText, String bottomText) {
        ((TextView) view.findViewById(R.id.top)).setText(topText);

        TextView bottom = ((TextView) view.findViewById(R.id.bottom));
        if (bottom != null) {
            if (bottomText == null) {
                ViewGroup parent = (ViewGroup) bottom.getParent();
                parent.removeView(bottom);
            } else {
                bottom.setText(bottomText);
            }
        }
    }
}
