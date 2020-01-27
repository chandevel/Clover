/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.controller.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.viewpager.widget.ViewPager;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.common.DefaultPostParser;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.ui.view.ViewPagerAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class ThemeSettingsController
        extends Controller
        implements View.OnClickListener {
    private Board dummyBoard;

    {
        dummyBoard = new Board();
        dummyBoard.name = "name";
        dummyBoard.code = "code";
    }

    private Loadable dummyLoadable;

    {
        dummyLoadable = Loadable.emptyLoadable();
        dummyLoadable.mode = Loadable.Mode.THREAD;
    }

    private PostCell.PostCellCallback dummyPostCallback = new PostCell.PostCellCallback() {
        @Override
        public Loadable getLoadable() {
            return dummyLoadable;
        }

        @Override
        public void onPostClicked(Post post) {
        }

        @Override
        public void onPopupPostDoubleClicked(Post post) {
        }

        @Override
        public void onThumbnailClicked(PostImage postImage, ThumbnailView thumbnail) {
        }

        @Override
        public void onShowPostReplies(Post post) {
        }

        @Override
        public Object onPopulatePostOptions(Post post, List<FloatingMenuItem> menu, List<FloatingMenuItem> extraMenu) {
            menu.add(new FloatingMenuItem(1, "Option"));
            return 0;
        }

        @Override
        public void onPostOptionClicked(Post post, Object id) {
        }

        @Override
        public void onPostLinkableClicked(Post post, PostLinkable linkable) {
        }

        @Override
        public void onPostNoClicked(Post post) {
        }

        @Override
        public void onPostSelectionQuoted(Post post, CharSequence quoted) {
        }
    };

    private PostParser.Callback parserCallback = new PostParser.Callback() {
        @Override
        public boolean isSaved(int postNo) {
            return false;
        }

        @Override
        public boolean isInternal(int postNo) {
            return true;
        }
    };

    private ViewPager pager;
    private FloatingActionButton done;
    private TextView textView;

    private List<Theme> themes;
    private List<ThemeHelper.PrimaryColor> selectedPrimaryColors = new ArrayList<>();
    private ThemeHelper.PrimaryColor selectedAccentColor;

    public ThemeSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_screen_theme);
        navigation.swipeable = false;
        view = inflate(context, R.layout.controller_theme);

        themes = instance(ThemeHelper.class).getThemes();

        pager = view.findViewById(R.id.pager);
        done = view.findViewById(R.id.add);
        done.setOnClickListener(this);

        textView = view.findViewById(R.id.text);

        SpannableString changeAccentColor = new SpannableString(getString(R.string.setting_theme_accent));
        changeAccentColor.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                showAccentColorPicker();
            }
        }, 0, changeAccentColor.length(), 0);

        textView.setText(TextUtils.concat(getString(R.string.setting_theme_explanation), "\n", changeAccentColor));
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        Adapter adapter = new Adapter();
        pager.setAdapter(adapter);

        ChanSettings.ThemeColor currentSettingsTheme = ChanSettings.getThemeAndColor();
        for (int i = 0; i < themes.size(); i++) {
            Theme theme = themes.get(i);
            ThemeHelper.PrimaryColor primaryColor = theme.primaryColor;

            if (theme.name.equals(currentSettingsTheme.theme)) {
                // Current theme
                pager.setCurrentItem(i, false);
            }
            selectedPrimaryColors.add(primaryColor);
        }
        selectedAccentColor = ThemeHelper.getTheme().accentColor;
        done.setBackgroundTintList(ColorStateList.valueOf(selectedAccentColor.color));
    }

    @Override
    public void onClick(View v) {
        if (v == done) {
            saveTheme();
        }
    }

    private void saveTheme() {
        int currentItem = pager.getCurrentItem();
        Theme selectedTheme = themes.get(currentItem);
        ThemeHelper.PrimaryColor selectedColor = selectedPrimaryColors.get(currentItem);
        instance(ThemeHelper.class).changeTheme(selectedTheme, selectedColor, selectedAccentColor);
        ((StartActivity) context).restartApp();
    }

    private void showAccentColorPicker() {
        List<FloatingMenuItem> items = new ArrayList<>();
        FloatingMenuItem selected = null;
        for (ThemeHelper.PrimaryColor color : instance(ThemeHelper.class).getColors()) {
            FloatingMenuItem floatingMenuItem =
                    new FloatingMenuItem(new ColorsAdapterItem(color, color.color), color.displayName);
            items.add(floatingMenuItem);
            if (color == selectedAccentColor) {
                selected = floatingMenuItem;
            }
        }

        FloatingMenu menu = getColorsMenu(items, selected, textView);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                ColorsAdapterItem colorItem = (ColorsAdapterItem) item.getId();
                selectedAccentColor = colorItem.color;
                done.setBackgroundTintList(ColorStateList.valueOf(selectedAccentColor.color));
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {

            }
        });
        menu.setPopupHeight(dp(300));
        menu.show();
    }

    private FloatingMenu getColorsMenu(List<FloatingMenuItem> items, FloatingMenuItem selected, View anchor) {
        FloatingMenu menu = new FloatingMenu(context);

        menu.setItems(items);
        menu.setAdapter(new ColorsAdapter(items));
        menu.setSelectedItem(selected);
        menu.setAnchor(anchor, Gravity.CENTER, 0, dp(5));
        return menu;
    }

    private class Adapter
            extends ViewPagerAdapter {
        public Adapter() {
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return super.getPageTitle(position);
        }

        @Override
        public View getView(final int position, ViewGroup parent) {
            final Theme theme = themes.get(position);

            Context themeContext = new ContextThemeWrapper(context, theme.resValue);

            Post.Builder builder = new Post.Builder().board(dummyBoard)
                    .id(123456789)
                    .opId(1)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(30)))
                    .subject("Lorem ipsum")
                    .comment("<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a><br>"
                            + "Lorem ipsum dolor sit amet, consectetur adipiscing elit.<br><br>"
                            + "<span class=\"deadlink\">&gt;&gt;987654321</span><br>" + "http://example.com/<br>"
                            + "Phasellus consequat semper sodales. Donec dolor lectus, aliquet nec mollis vel, rutrum vel enim.<br>"
                            + "<span class=\"quote\">&gt;Nam non hendrerit justo, venenatis bibendum arcu.</span>");
            CommentParser parser = new CommentParser();
            parser.addDefaultRules();
            Post post = new DefaultPostParser(parser).parse(theme, builder, parserCallback);

            LinearLayout linearLayout = new LinearLayout(themeContext);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setBackgroundColor(theme.backColor);

            final Toolbar toolbar = new Toolbar(themeContext);
            final View.OnClickListener colorClick = v -> {
                List<FloatingMenuItem> items = new ArrayList<>();
                FloatingMenuItem selected = null;
                for (ThemeHelper.PrimaryColor color : instance(ThemeHelper.class).getColors()) {
                    FloatingMenuItem floatingMenuItem =
                            new FloatingMenuItem(new ColorsAdapterItem(color, color.color500), color.displayName);
                    items.add(floatingMenuItem);
                    if (color == selectedPrimaryColors.get(position)) {
                        selected = floatingMenuItem;
                    }
                }

                FloatingMenu menu = getColorsMenu(items, selected, toolbar);
                menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                    @Override
                    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                        ColorsAdapterItem colorItem = (ColorsAdapterItem) item.getId();
                        selectedPrimaryColors.set(position, colorItem.color);
                        toolbar.setBackgroundColor(colorItem.color.color);
                    }

                    @Override
                    public void onFloatingMenuDismissed(FloatingMenu menu) {
                    }
                });
                menu.show();
            };
            toolbar.setCallback(new Toolbar.ToolbarCallback() {
                @Override
                public void onMenuOrBackClicked(boolean isArrow) {
                    colorClick.onClick(toolbar);
                }

                @Override
                public void onSearchVisibilityChanged(NavigationItem item, boolean visible) {
                }

                @Override
                public void onSearchEntered(NavigationItem item, String entered) {
                }
            });
            toolbar.setBackgroundColor(theme.primaryColor.color);
            final NavigationItem item = new NavigationItem();
            item.title = theme.displayName;
            item.hasBack = false;
            toolbar.setNavigationItem(false, true, item, theme);
            toolbar.setOnClickListener(colorClick);

            linearLayout.addView(toolbar,
                    new LinearLayout.LayoutParams(MATCH_PARENT, getDimen(R.dimen.toolbar_height))
            );

            PostCell postCell = (PostCell) inflate(context, R.layout.cell_post, null);
            postCell.setPost(dummyLoadable,
                    post,
                    dummyPostCallback,
                    false,
                    false,
                    false,
                    -1,
                    true,
                    ChanSettings.PostViewMode.LIST,
                    false,
                    theme
            );
            linearLayout.addView(postCell, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            return linearLayout;
        }

        @Override
        public int getCount() {
            return themes.size();
        }
    }

    private class ColorsAdapter
            extends BaseAdapter {
        private List<FloatingMenuItem> items;

        public ColorsAdapter(List<FloatingMenuItem> items) {
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            @SuppressLint("ViewHolder")
            TextView textView = (TextView) inflate(parent.getContext(), R.layout.toolbar_menu_item, parent, false);
            textView.setText(getItem(position));
            textView.setTypeface(ThemeHelper.getTheme().mainFont);

            ColorsAdapterItem color = (ColorsAdapterItem) items.get(position).getId();

            textView.setBackgroundColor(color.bg);
            boolean lightColor =
                    (Color.red(color.bg) * 0.299f) + (Color.green(color.bg) * 0.587f) + (Color.blue(color.bg) * 0.114f)
                            > 125f;
            textView.setTextColor(lightColor ? Color.BLACK : Color.WHITE);

            return textView;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public String getItem(int position) {
            return items.get(position).getText();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    private static class ColorsAdapterItem {
        public ThemeHelper.PrimaryColor color;
        public int bg;

        public ColorsAdapterItem(ThemeHelper.PrimaryColor color, int bg) {
            this.color = color;
            this.bg = bg;
        }
    }
}
