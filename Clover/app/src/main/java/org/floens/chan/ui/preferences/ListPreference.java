package org.floens.chan.ui.preferences;

import android.view.Gravity;
import android.view.View;

import org.floens.chan.core.preferences.Preference;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ListPreference extends PreferenceItem implements FloatingMenu.FloatingMenuCallback, View.OnClickListener {
    public final Item[] items;

    private Preference<String> preference;

    private int selected;

    public ListPreference(PreferencesController preferencesController, Preference<String> preference, String name, Item[] items) {
        super(preferencesController, name);
        this.preference = preference;
        this.items = items;

        selectItem();
    }

    public String getBottomDescription() {
        return items[selected].name;
    }

    public Preference<String> getPreference() {
        return preference;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(this);
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
        preference.set(selectedKey);
        selectItem();
        preferencesController.onPreferenceChange(this);
    }

    private void selectItem() {
        String selectedKey = preference.get();
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
