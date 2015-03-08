package org.floens.chan.ui.preferences;

import android.view.Gravity;
import android.view.View;

import org.floens.chan.R;
import org.floens.chan.core.settings.Setting;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ListSettingView extends SettingView implements FloatingMenu.FloatingMenuCallback, View.OnClickListener {
    public final Item[] items;

    private Setting<String> setting;

    private int selected;

    public ListSettingView(SettingsController settingsController, Setting<String> setting, String name, Item[] items) {
        super(settingsController, name);
        this.setting = setting;
        this.items = items;

        selectItem();
    }

    public String getBottomDescription() {
        return items[selected].name;
    }

    public Setting<String> getSetting() {
        return setting;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        view.setEnabled(enabled);
        view.findViewById(R.id.top).setEnabled(enabled);
        View bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setEnabled(enabled);
        }
    }

    @Override
    public void onClick(View v) {
        List<FloatingMenuItem> menuItems = new ArrayList<>(2);

        for (Item item : items) {
            menuItems.add(new FloatingMenuItem(item.key, item.name));
        }

        FloatingMenu menu = new FloatingMenu(v.getContext());
        menu.setAnchor(v, Gravity.LEFT, dp(5), dp(5));
        menu.setPopupWidth(FloatingMenu.POPUP_WIDTH_ANCHOR);
        menu.setCallback(this);
        menu.setItems(menuItems);
        menu.show();
    }

    @Override
    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
        String selectedKey = (String) item.getId();
        setting.set(selectedKey);
        selectItem();
        settingsController.onPreferenceChange(this);
    }

    private void selectItem() {
        String selectedKey = setting.get();
        for (int i = 0; i < items.length; i++) {
            if (items[i].key.equals(selectedKey)) {
                selected = i;
                break;
            }
        }
    }

    public static class Item<T> {
        public final String name;
        public final T key;

        public Item(String name, T key) {
            this.name = name;
            this.key = key;
        }
    }
}
