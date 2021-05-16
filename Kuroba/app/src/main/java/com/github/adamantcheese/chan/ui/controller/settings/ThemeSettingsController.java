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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.common.DefaultPostParser;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

import static androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL;
import static com.github.adamantcheese.chan.ui.theme.ThemeHelper.createTheme;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class ThemeSettingsController
        extends Controller {

    private final Loadable dummyLoadable = Loadable.emptyLoadable();

    {
        dummyLoadable.mode = Loadable.Mode.THREAD;
        dummyLoadable.lastViewed = 234567890;
    }

    private final PostCell.PostCellCallback dummyPostCallback = new PostCell.PostCellCallback() {
        @Override
        public Loadable getLoadable() {
            return dummyLoadable;
        }

        @Override
        public String getSearchQuery() {
            return "search highlighting";
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
                Post post, List<FloatingMenuItem<PostOptions>> menu, List<FloatingMenuItem<PostOptions>> extraMenu
        ) {
            menu.add(new FloatingMenuItem<>(PostOptions.POST_OPTION_INFO, "Option"));
            return 0;
        }

        @Override
        public void onPostOptionClicked(View anchor, Post post, PostOptions id, boolean inPopup) {
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

    private final PostParser.Callback parserCallback = new PostParser.Callback() {
        @Override
        public boolean isSaved(int postNo) {
            return false;
        }

        @Override
        public boolean isInternal(int postNo) {
            return true;
        }

        @Override
        public boolean isRemoved(int postNo) {
            return false;
        }
    };

    private CoordinatorLayout wrapper;
    private ViewPager2 pager;
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
                navigation.buildMenu().withItem(R.drawable.ic_fluent_question_circle_24_regular, this::helpClicked);
        if (isAndroid10()) {
            builder.withItem(ThemeHelper.isNightTheme
                    ? R.drawable.ic_fluent_weather_moon_24_filled
                    : R.drawable.ic_fluent_weather_sunny_24_filled, this::dayNightToggle);
        }
        builder.build();
        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_theme, null);

        Theme currentTheme = ThemeHelper.getTheme();
        // restore if the user pressed back
        currentDayNight = ThemeHelper.isNightTheme;

        wrapper = view.findViewById(R.id.wrapper);
        pager = view.findViewById(R.id.pager);
        done = view.findViewById(R.id.add);
        done.setOnClickListener(v -> saveTheme());

        // pager setup
        pager.setOffscreenPageLimit(1);
        // display on the sides
        pager.setPageTransformer((page, position) -> {
            float offset = position * -(2 * dp(6) + dp(6));
            if (pager.getOrientation() == ORIENTATION_HORIZONTAL) {
                if (ViewCompat.getLayoutDirection(pager) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                    page.setTranslationX(-offset);
                } else {
                    page.setTranslationX(offset);
                }
            } else {
                page.setTranslationY(offset);
            }
        });
        // update done and background color when a new theme is selected
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                Theme currentTheme = getViewedTheme();
                done.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(currentTheme.accentColor.accentStyleId,
                        R.attr.colorAccent
                )));
                wrapper.setBackgroundColor(getAttrColor(currentTheme.resValue, R.attr.backcolor));
            }
        });

        updateAdapter(currentTheme);
    }

    @Override
    public boolean onBack() {
        ThemeHelper.resetThemes();
        ThemeHelper.isNightTheme = currentDayNight;
        return super.onBack();
    }

    private Theme getViewedTheme() {
        return ThemeHelper.themes.get(pager.getCurrentItem());
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
        final AlertDialog dialog = getDefaultAlertBuilder(context).setTitle("Help")
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
            item.setImage(R.drawable.ic_fluent_weather_sunny_24_filled);
            ThemeHelper.isNightTheme = false;
        } else {
            item.setImage(R.drawable.ic_fluent_weather_moon_24_filled);
            ThemeHelper.isNightTheme = true;
        }
        navigationController.getToolbar().updateViewForItem(navigation);

        updateAdapter(ThemeHelper.getTheme());
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
                updateAdapter(currentTheme);
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

    private void updateAdapter(Theme currentTheme) {
        int i;
        for (i = 0; i < ThemeHelper.themes.size(); i++) {
            Theme theme = ThemeHelper.themes.get(i);
            if (theme.name.equals(currentTheme.name)) {
                theme.primaryColor = currentTheme.primaryColor;
                theme.accentColor = currentTheme.accentColor;
                break;
            }
        }
        pager.setAdapter(new ThemePostsAdapter());
        pager.setCurrentItem(i, false);
    }

    final List<Filter> filters = Collections.singletonList(new Filter(true,
            FilterType.SUBJECT.flag | FilterType.COMMENT.flag,
            "spacer",
            true,
            "",
            FilterEngine.FilterAction.COLOR.id,
            Color.RED & 0x50FFFFFF,
            false,
            0,
            false,
            false
    ));
    final PostParser postParser = new DefaultPostParser(new CommentParser().addDefaultRules());

    private class ThemePostsAdapter
            extends RecyclerView.Adapter<ThemePostsAdapter.ThemePreviewHolder> {
        public ThemePostsAdapter() {
        }

        // NOTE
        // This adapter is a bit weird because we need to change contexts a lot
        // So anytime we do something that changes the theme, we need to refresh the whole adapter and set the current item
        // updateAdapter takes care of that
        @NonNull
        @Override
        public ThemePostsAdapter.ThemePreviewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Theme theme = ThemeHelper.themes.get(viewType);
            Context themeContext = new ContextThemeWrapper(context, createTheme(context, theme));
            return new ThemePreviewHolder(theme,
                    LayoutInflater.from(themeContext).inflate(R.layout.layout_theme_preview, parent, false)
            );
        }

        @Override
        public int getItemCount() {
            return ThemeHelper.themes.size();
        }

        @Override
        public int getItemViewType(int position) {
            // in order to index into ThemeHelper's themes in onCreateViewHolder, we just return the position here
            return position;
        }

        @Override
        public void onViewRecycled(
                @NonNull ThemePreviewHolder holder
        ) {
            holder.recyclerView.setAdapter(null);
        }

        @Override
        public void onBindViewHolder(@NonNull ThemePostsAdapter.ThemePreviewHolder holder, int position) {
            //region POST BUILDERS
            Post.Builder builder1 = new Post.Builder().board(Board.getDummyBoard())
                    .no(123456789)
                    .opId(123456789)
                    .op(true)
                    .replies(1)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(60)))
                    .subject("Lorem ipsum")
                    .comment("<span class=\"deadlink\">&gt;&gt;987654321</span><br>" + "http://example.com/<br>"
                            + "This text is normally colored. <span class=\"spoiler\">This text is spoilered.</span><br>"
                            + "<span class=\"quote\">&gt;This text is inline quoted (greentext).</span>")
                    .idColor(Color.WHITE);

            Post.Builder builder2 = new Post.Builder().board(Board.getDummyBoard())
                    .no(234567890)
                    .opId(123456789)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(30)))
                    .comment(
                            "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a> This link is marked.<br>"
                                    + "<a href=\"#p111111111\" class=\"quotelink\">&gt;&gt;111111111</a> This is a spacer "
                                    + "post for seeing the divider color; below is a youtube link for title/duration testing:<br>"
                                    + "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

            Post.Builder builder3 = new Post.Builder().board(Board.getDummyBoard())
                    .no(345678901)
                    .opId(123456789)
                    .name("W.T. Snacks")
                    .tripcode("!TcT.PTG1.2")
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(15)))
                    .comment(
                            "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a> This link is marked.<br>"
                                    + "<a href=\"#p111111111\" class=\"quotelink\">&gt;&gt;111111111</a><br>"
                                    + "This post is highlighted.<br>"
                                    + "<span class=\"spoiler\">This text is spoilered in a highlighted post.</span><br>"
                                    + "This text has search highlighting applied.")
                    .images(Collections.singletonList(new PostImage.Builder().imageUrl(HttpUrl.get(
                            BuildConfig.RESOURCES_ENDPOINT + "new_icon_512.png"))
                            .thumbnailUrl(HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "new_icon_512.png"))
                            .filename("new_icon_512")
                            .extension("png")
                            .build()));
            //endregion

            List<Post> posts = new ArrayList<>();
            posts.add(postParser.parse(holder.theme, builder1, filters, parserCallback));
            posts.add(postParser.parse(holder.theme, builder2, filters, parserCallback));
            posts.add(postParser.parse(holder.theme, builder3, filters, parserCallback));
            posts.get(0).repliesFrom.add(posts.get(posts.size() - 1).no); // add reply to first post point to last post
            ChanThread thread = new ChanThread(dummyLoadable, posts);

            for (Post p : thread.getPosts()) {
                for (PostLinkable linkable : p.getLinkables()) {
                    linkable.setMarkedNo(123456789);
                }
            }

            PostAdapter adapter = new PostAdapter(holder.recyclerView, post -> {
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
                    return thread;
                }

                @Override
                public void onListStatusClicked() {}
            }, holder.theme) {
                @Override
                public boolean showStatusView() {
                    return false;
                }
            };
            adapter.setThread(thread, new PostsFilter(PostsFilter.Order.BUMP, null));
            adapter.highlightPostNo(posts.get(posts.size() - 1).no); // highlight last post
            holder.recyclerView.setAdapter(adapter);
            holder.accentText.setOnClickListener((v) -> showAccentColorPicker());

            final View.OnClickListener colorClick = v -> {
                List<FloatingMenuItem<MaterialColorStyle>> items = new ArrayList<>();
                FloatingMenuItem<MaterialColorStyle> selected = null;
                for (MaterialColorStyle color : MaterialColorStyle.values()) {
                    FloatingMenuItem<MaterialColorStyle> floatingMenuItem =
                            new FloatingMenuItem<>(color, color.prettyName());
                    items.add(floatingMenuItem);
                    if (color == holder.theme.primaryColor) {
                        selected = floatingMenuItem;
                    }
                }

                FloatingMenu<MaterialColorStyle> menu = getColorsMenu(items, selected, holder.toolbar, false);
                menu.setCallback(new FloatingMenu.ClickCallback<MaterialColorStyle>() {
                    @Override
                    public void onFloatingMenuItemClicked(
                            FloatingMenu<MaterialColorStyle> menu, FloatingMenuItem<MaterialColorStyle> item
                    ) {
                        MaterialColorStyle color = item.getId();
                        holder.theme.primaryColor = color;
                        holder.toolbar.setBackgroundColor(getAttrColor(color.primaryColorStyleId, R.attr.colorPrimary));
                    }
                });
                menu.setPopupHeight(dp(300));
                menu.show();
            };
            holder.toolbar.setCallback(new Toolbar.ToolbarCallback() {
                @Override
                public void onMenuOrBackClicked(boolean isArrow) {
                    colorClick.onClick(holder.toolbar);
                }

                @Override
                public void onSearchVisibilityChanged(NavigationItem item, boolean visible) {
                }

                @Override
                public void onSearchEntered(NavigationItem item, String entered) {
                }

                @Override
                public void onNavItemSet(NavigationItem item) {
                }
            });
            final NavigationItem item = new NavigationItem();
            item.title = holder.theme.name;
            item.hasBack = false;
            holder.toolbar.setNavigationItem(false, true, item, holder.theme);
            holder.toolbar.setOnClickListener(colorClick);
        }

        private class ThemePreviewHolder
                extends RecyclerView.ViewHolder {
            private final Theme theme;
            private final RecyclerView recyclerView;
            private final TextView accentText;
            private final Toolbar toolbar;

            public ThemePreviewHolder(Theme theme, @NonNull View itemView) {
                super(itemView);
                this.theme = theme;
                recyclerView = itemView.findViewById(R.id.posts_recycler);
                accentText = itemView.findViewById(R.id.text_accent);
                toolbar = itemView.findViewById(R.id.theme_toolbar);
                toolbar.setMenuDrawable(R.drawable.ic_fluent_paint_brush_20_filled);
            }
        }
    }

    private static class ColorsAdapter
            extends BaseAdapter {
        private final List<FloatingMenuItem<MaterialColorStyle>> colors;
        private final boolean useAccentColors;

        public ColorsAdapter(List<FloatingMenuItem<MaterialColorStyle>> items, boolean useAccentColors) {
            this.colors = items;
            this.useAccentColors = useAccentColors;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) (convertView != null
                    ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.toolbar_menu_item, parent, false));
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
