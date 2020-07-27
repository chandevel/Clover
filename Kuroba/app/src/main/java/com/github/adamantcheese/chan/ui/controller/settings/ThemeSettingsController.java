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
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.common.DefaultPostParser;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.ui.view.ViewPagerAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.ui.theme.ThemeHelper.createTheme;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class ThemeSettingsController
        extends Controller {

    private static final int TOGGLE_ID = 1;
    private Loadable dummyLoadable = Loadable.emptyLoadable();

    {
        dummyLoadable.mode = Loadable.Mode.THREAD;
        dummyLoadable.lastViewed = 234567890;
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
        public void onPostDoubleClicked(Post post) {
        }

        @Override
        public void onThumbnailClicked(PostImage postImage, ThumbnailView thumbnail) {
        }

        @Override
        public void onShowPostReplies(Post post) {
        }

        @Override
        public Object onPopulatePostOptions(
                Post post, List<FloatingMenuItem<Integer>> menu, List<FloatingMenuItem<Integer>> extraMenu
        ) {
            menu.add(new FloatingMenuItem<Integer>(1, "Option"));
            return 0;
        }

        @Override
        public void onPostOptionClicked(View anchor, Post post, Object id, boolean inPopup) {
        }

        @Override
        public void onPostLinkableClicked(Post post, PostLinkable linkable) {
        }

        @Override
        public void onPostNoClicked(Post post, boolean withText) {
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

    private CoordinatorLayout wrapper;
    private ViewPager pager;
    private FloatingActionButton done;

    private boolean currentDayNight;

    public ThemeSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationController.getToolbar().updateViewForItem(navigation);
        navigation.setTitle(R.string.settings_screen_theme);
        navigation.swipeable = false;
        NavigationItem.MenuBuilder builder =
                navigation.buildMenu().withItem(R.drawable.ic_help_outline_white_24dp, this::helpClicked);
        if (isAndroid10()) {
            builder.withItem(TOGGLE_ID,
                    ThemeHelper.isNightTheme ? R.drawable.ic_moon_white_24dp : R.drawable.ic_sun_white_24dp,
                    this::dayNightToggle
            );
        }
        builder.build();
        view = inflate(context, R.layout.controller_theme);

        Theme currentTheme = ThemeHelper.getTheme();
        // restore if the user pressed back
        currentDayNight = ThemeHelper.isNightTheme;

        wrapper = view.findViewById(R.id.wrapper);
        pager = view.findViewById(R.id.pager);
        done = view.findViewById(R.id.add);
        done.setOnClickListener(v -> saveTheme());

        pager.setAdapter(new Adapter());
        pager.setPageMargin(dp(6));
        for (int i = 0; i < ThemeHelper.getThemes().size(); i++) {
            Theme theme = ThemeHelper.getThemes().get(i);
            if (theme.name.equals(currentTheme.name)) {
                // Current theme
                pager.setCurrentItem(i, false);
                theme.primaryColor = currentTheme.primaryColor;
                theme.accentColor = currentTheme.accentColor;
                break;
            }
        }
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                Theme currentTheme = getViewedTheme();
                done.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(currentTheme.accentColor.accentStyleId,
                        R.attr.colorAccent
                )));
                wrapper.setBackgroundColor(getAttrColor(currentTheme.resValue, R.attr.backcolor));
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    @Override
    public boolean onBack() {
        ThemeHelper.resetThemes();
        ThemeHelper.isNightTheme = currentDayNight;
        return super.onBack();
    }

    private Theme getViewedTheme() {
        return ThemeHelper.getThemes().get(pager.getCurrentItem());
    }

    private void saveTheme() {
        if (ThemeHelper.isNightTheme) {
            ChanSettings.themeNight.setSync(getViewedTheme().toString());
        } else {
            ChanSettings.themeDay.setSync(getViewedTheme().toString());
        }
        ((StartActivity) context).restartApp();
    }

    private void helpClicked(ToolbarMenuItem item) {
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle("Help")
                .setMessage(R.string.setting_theme_explanation)
                .setPositiveButton("Close", null)
                .show();
        dialog.setCanceledOnTouchOutside(true);
    }

    private void dayNightToggle(ToolbarMenuItem item) {
        //reset theme choices
        ThemeHelper.resetThemes();

        //toggle toolbar item
        if (ThemeHelper.isNightTheme) {
            navigation.findItem(TOGGLE_ID).setImage(R.drawable.ic_sun_white_24dp);
            ThemeHelper.isNightTheme = false;
        } else {
            navigation.findItem(TOGGLE_ID).setImage(R.drawable.ic_moon_white_24dp);
            ThemeHelper.isNightTheme = true;
        }
        navigationController.getToolbar().updateViewForItem(navigation);

        //update views
        Theme currentTheme = ThemeHelper.getTheme();
        pager.setAdapter(new Adapter());
        for (int i = 0; i < ThemeHelper.getThemes().size(); i++) {
            Theme theme = ThemeHelper.getThemes().get(i);
            if (theme.name.equals(currentTheme.name)) {
                // Current theme
                pager.setCurrentItem(i, false);
                theme.primaryColor = currentTheme.primaryColor;
                theme.accentColor = currentTheme.accentColor;
                break;
            }
        }
        //update button color manually, in case onPageSelected isn't called
        done.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(ThemeHelper.getTheme().accentColor.accentStyleId,
                R.attr.colorAccent
        )));
        wrapper.setBackgroundColor(getAttrColor(ThemeHelper.getTheme().resValue, R.attr.backcolor));
    }

    private void showAccentColorPicker() {
        List<FloatingMenuItem<MaterialColorStyle>> items = new ArrayList<>();
        FloatingMenuItem<MaterialColorStyle> selected = null;
        for (MaterialColorStyle color : MaterialColorStyle.values()) {
            FloatingMenuItem<MaterialColorStyle> floatingMenuItem = new FloatingMenuItem<>(color, color.prettyName());
            items.add(floatingMenuItem);
            if (color == getViewedTheme().accentColor) {
                selected = floatingMenuItem;
            }
        }

        FloatingMenu<MaterialColorStyle> menu = getColorsMenu(items, selected, done, true);
        menu.setCallback(new FloatingMenu.ClickCallback<MaterialColorStyle>() {
            @Override
            public void onFloatingMenuItemClicked(
                    FloatingMenu<MaterialColorStyle> menu, FloatingMenuItem<MaterialColorStyle> item
            ) {
                Theme currentTheme = getViewedTheme();
                currentTheme.accentColor = item.getId();
                done.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(currentTheme.accentColor.accentStyleId,
                        R.attr.colorAccent
                )));
                //force update all the views to have the right accent color
                pager.setAdapter(new Adapter());
                for (int i = 0; i < ThemeHelper.getThemes().size(); i++) {
                    Theme theme = ThemeHelper.getThemes().get(i);
                    if (theme.name.equals(currentTheme.name)) {
                        // Current theme
                        pager.setCurrentItem(i, false);
                        break;
                    }
                }
            }
        });
        menu.setPopupHeight(dp(300));
        menu.show();
    }

    private FloatingMenu<MaterialColorStyle> getColorsMenu(
            List<FloatingMenuItem<MaterialColorStyle>> items,
            FloatingMenuItem<MaterialColorStyle> selected,
            View anchor,
            boolean useAccentColors
    ) {
        FloatingMenu<MaterialColorStyle> menu = new FloatingMenu<>(context, anchor, items);
        menu.setAnchorGravity(Gravity.CENTER, 0, 0);
        menu.setAdapter(new ColorsAdapter(items, useAccentColors));
        menu.setSelectedItem(selected);
        return menu;
    }

    private class Adapter
            extends ViewPagerAdapter {
        public Adapter() {
        }

        @Override
        public View getView(final int position, ViewGroup parent) {
            final Theme theme = ThemeHelper.getThemes().get(position);

            Context themeContext = new ContextThemeWrapper(context, createTheme(context, theme));

            CommentParser parser = new CommentParser().addDefaultRules();
            DefaultPostParser postParser = new DefaultPostParser(parser);

            //region POSTS
            Post.Builder builder1 = new Post.Builder().board(Board.getDummyBoard())
                    .id(123456789)
                    .opId(123456789)
                    .op(true)
                    .replies(1)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(60)))
                    .subject("Lorem ipsum")
                    .comment("<span class=\"deadlink\">&gt;&gt;987654321</span><br>" + "http://example.com/<br>"
                            + "This text is normally colored.<br>"
                            + "<span class=\"spoiler\">This text is spoilered.</span><br>"
                            + "<span class=\"quote\">&gt;This text is inline quoted (greentext).</span>")
                    .idColor(Color.WHITE);

            Post.Builder builder2 = new Post.Builder().board(Board.getDummyBoard())
                    .id(234567890)
                    .opId(123456789)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(30)))
                    .comment(
                            "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a> This link is marked.<br>"
                                    + "<a href=\"#p111111111\" class=\"quotelink\">&gt;&gt;111111111</a><br>"
                                    + "This is a spacer post for divider color display, and for displaying the new posts indicator.");

            Post.Builder builder3 = new Post.Builder().board(Board.getDummyBoard())
                    .id(345678901)
                    .opId(123456789)
                    .name("W.T. Snacks")
                    .tripcode("!TcT.PTG1.2")
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(15)))
                    .comment(
                            "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a> This link is marked.<br>"
                                    + "<a href=\"#p111111111\" class=\"quotelink\">&gt;&gt;111111111</a><br>"
                                    + "This post is highlighted.<br>"
                                    + "<span class=\"spoiler\">This text is spoilered in a highlighted post.</span><br>")
                    .images(Collections.singletonList(new PostImage.Builder().imageUrl(HttpUrl.get(
                            BuildConfig.RESOURCES_ENDPOINT + "new_icon_512.png"))
                            .thumbnailUrl(HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "new_icon_512.png"))
                            .filename("new_icon_512")
                            .extension("png")
                            .build()));
            //endregion

            List<Post> posts = new ArrayList<>();
            posts.add(postParser.parse(theme, builder1, parserCallback));
            posts.add(postParser.parse(theme, builder2, parserCallback));
            posts.add(postParser.parse(theme, builder3, parserCallback));
            posts.get(0).repliesFrom.add(posts.get(posts.size() - 1).no); // add reply to first post point to last post

            LinearLayout linearLayout = new LinearLayout(themeContext);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setBackgroundColor(getAttrColor(themeContext, R.attr.backcolor));

            RecyclerView postsView = new RecyclerView(themeContext);
            LinearLayoutManager layoutManager = new LinearLayoutManager(themeContext);
            postsView.setLayoutManager(layoutManager);
            PostAdapter adapter = new PostAdapter(postsView, new PostAdapter.PostAdapterCallback() {
                @Override
                public Loadable getLoadable() {
                    return dummyLoadable;
                }

                @Override
                public void onUnhidePostClick(Post post) {
                }
            }, dummyPostCallback, new ThreadStatusCell.Callback() {
                @Override
                public long getTimeUntilLoadMore() {
                    return 0;
                }

                @Override
                public boolean isWatching() {
                    return false;
                }

                @Nullable
                @Override
                public ChanThread getChanThread() {
                    return null;
                }

                @Override
                public void onListStatusClicked() {
                    showAccentColorPicker();
                }
            }, theme) {
                @Override
                public int getMarkedNo() {
                    return 123456789;
                }
            };
            adapter.setThread(dummyLoadable, posts, false);
            adapter.highlightPost(posts.get(posts.size() - 1)); // highlight last post
            adapter.setPostViewMode(ChanSettings.PostViewMode.LIST);
            adapter.showError(ThreadStatusCell.SPECIAL + getString(R.string.setting_theme_accent));
            postsView.setAdapter(adapter);

            final Toolbar toolbar = new Toolbar(themeContext);
            toolbar.setMenuDrawable(R.drawable.ic_format_paint_white_24dp);
            final View.OnClickListener colorClick = v -> {
                List<FloatingMenuItem<MaterialColorStyle>> items = new ArrayList<>();
                FloatingMenuItem<MaterialColorStyle> selected = null;
                for (MaterialColorStyle color : MaterialColorStyle.values()) {
                    FloatingMenuItem<MaterialColorStyle> floatingMenuItem =
                            new FloatingMenuItem<>(color, color.prettyName());
                    items.add(floatingMenuItem);
                    if (color == theme.primaryColor) {
                        selected = floatingMenuItem;
                    }
                }

                FloatingMenu<MaterialColorStyle> menu = getColorsMenu(items, selected, toolbar, false);
                menu.setCallback(new FloatingMenu.ClickCallback<MaterialColorStyle>() {
                    @Override
                    public void onFloatingMenuItemClicked(
                            FloatingMenu<MaterialColorStyle> menu, FloatingMenuItem<MaterialColorStyle> item
                    ) {
                        MaterialColorStyle color = item.getId();
                        theme.primaryColor = color;
                        toolbar.setBackgroundColor(getAttrColor(color.primaryColorStyleId, R.attr.colorPrimary));
                    }
                });
                menu.setPopupHeight(dp(300));
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
            final NavigationItem item = new NavigationItem();
            item.title = theme.name;
            item.hasBack = false;
            toolbar.setNavigationItem(false, true, item, theme);
            toolbar.setOnClickListener(colorClick);
            toolbar.setTag(theme.name);
            if (theme.name.equals(getViewedTheme().name)) {
                toolbar.setBackgroundColor(getAttrColor(ThemeHelper.getTheme().primaryColor.primaryColorStyleId,
                        R.attr.colorPrimary
                ));
            }

            linearLayout.addView(toolbar, new LayoutParams(MATCH_PARENT, getDimen(R.dimen.toolbar_height)));
            linearLayout.addView(postsView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            return linearLayout;
        }

        @Override
        public int getCount() {
            return ThemeHelper.getThemes().size();
        }
    }

    private static class ColorsAdapter
            extends BaseAdapter {
        private List<FloatingMenuItem<MaterialColorStyle>> colors;
        private boolean useAccentColors;

        public ColorsAdapter(List<FloatingMenuItem<MaterialColorStyle>> items, boolean useAccentColors) {
            this.colors = items;
            this.useAccentColors = useAccentColors;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            @SuppressLint("ViewHolder")
            TextView textView = (TextView) inflate(parent.getContext(), R.layout.toolbar_menu_item, parent, false);
            textView.setText(getItem(position));
            textView.setTypeface(ThemeHelper.getTheme().mainFont);

            MaterialColorStyle color = colors.get(position).getId();

            int colorForItem = useAccentColors
                    ? getAttrColor(color.accentStyleId, R.attr.colorAccent)
                    : getAttrColor(color.primaryColorStyleId, R.attr.colorPrimary);
            textView.setBackgroundColor(colorForItem);
            textView.setTextColor(getContrastColor(colorForItem));

            return textView;
        }

        @Override
        public int getCount() {
            return colors.size();
        }

        @Override
        public String getItem(int position) {
            return colors.get(position).getText();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
