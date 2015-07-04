/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewPager;
import android.support.v7.internal.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.chan.ChanParser;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.cell.PostCell;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.ui.toolbar.NavigationItem;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.ui.view.ViewPagerAdapter;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class ThemeSettingsController extends Controller implements View.OnClickListener {
    private ViewPager pager;
    private FloatingActionButton done;

    private Adapter adapter;
    private ThemeHelper themeHelper;

    private List<ThemeHelper.PrimaryColor> colors = new ArrayList<>();

    private PostCell.PostCellCallback DUMMY_POST_CALLBACK;

    public ThemeSettingsController(Context context) {
        super(context);

        DUMMY_POST_CALLBACK = new PostCell.PostCellCallback() {
            private Loadable loadable = new Loadable("g", 1234);

            @Override
            public Loadable getLoadable() {
                return loadable;
            }

            @Override
            public void onPostClicked(Post post) {
            }

            @Override
            public void onThumbnailClicked(Post post, ThumbnailView thumbnail) {
            }

            @Override
            public void onShowPostReplies(Post post) {
            }

            @Override
            public void onPopulatePostOptions(Post post, List<FloatingMenuItem> menu) {
                menu.add(new FloatingMenuItem(1, "Option"));
            }

            @Override
            public void onPostOptionClicked(Post post, Object id) {
            }

            @Override
            public void onPostLinkableClicked(PostLinkable linkable) {
            }

            @Override
            public void onPostNoClicked(Post post) {
            }
        };
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.setTitle(R.string.settings_screen_theme);
        view = inflateRes(R.layout.controller_theme);

        themeHelper = ThemeHelper.getInstance();

        pager = (ViewPager) view.findViewById(R.id.pager);
        done = (FloatingActionButton) view.findViewById(R.id.add);
        done.setOnClickListener(this);

        adapter = new Adapter();
        pager.setAdapter(adapter);

        ChanSettings.ThemeColor currentSettingsTheme = ChanSettings.getThemeAndColor();
        for (int i = 0; i < themeHelper.getThemes().size(); i++) {
            Theme theme = themeHelper.getThemes().get(i);
            ThemeHelper.PrimaryColor color = theme.primaryColor;
            if (theme.name.equals(currentSettingsTheme.theme)) {
                pager.setCurrentItem(i, false);
                if (currentSettingsTheme.color != null) {
                    color = themeHelper.getColor(currentSettingsTheme.color);
                }
            }
            colors.add(color);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == done) {
            Theme theme = themeHelper.getThemes().get(pager.getCurrentItem());
            themeHelper.changeTheme(theme, colors.get(pager.getCurrentItem()));
            ((StartActivity) context).restart();
        }
    }

    private class Adapter extends ViewPagerAdapter {
        private List<Theme> themes;

        public Adapter() {
            themes = themeHelper.getThemes();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return super.getPageTitle(position);
        }

        @Override
        public View getView(final int position, ViewGroup parent) {
            final Theme theme = themes.get(position);

            Context themeContext = new ContextThemeWrapper(context, theme.resValue);

            Post post = new Post();
            post.no = 123456789;
            post.time = (Time.get() - (30 * 60 * 1000)) / 1000;
            post.repliesFrom.add(1);
            post.repliesFrom.add(2);
            post.repliesFrom.add(3);
            post.subject = "Lorem ipsum";
            post.rawComment = "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a><br>" +
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit.<br>" +
                    "<br>" +
                    "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a><br>" +
                    "http://example.com/" +
                    "<br>" +
                    "Phasellus consequat semper sodales. Donec dolor lectus, aliquet nec mollis vel, rutrum vel enim.";
            ChanParser.getInstance().parse(theme, post);

            LinearLayout linearLayout = new LinearLayout(themeContext);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setBackgroundColor(getAttrColor(themeContext, R.attr.backcolor));

            final Toolbar toolbar = new Toolbar(themeContext);
            final View.OnClickListener colorClick = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<FloatingMenuItem> items = new ArrayList<>();
                    FloatingMenuItem selected = null;
                    for (ThemeHelper.PrimaryColor color : themeHelper.getColors()) {
                        FloatingMenuItem floatingMenuItem = new FloatingMenuItem(color, color.displayName);
                        items.add(floatingMenuItem);
                        if (color == colors.get(position)) {
                            selected = floatingMenuItem;
                        }
                    }

                    FloatingMenu menu = new FloatingMenu(context);
                    menu.setItems(items);
                    menu.setAdapter(new ColorsAdapter(items));
                    menu.setSelectedItem(selected);
                    menu.setAnchor(toolbar, Gravity.CENTER, 0, dp(5));
                    menu.setPopupWidth(toolbar.getWidth());
                    menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                        @Override
                        public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                            ThemeHelper.PrimaryColor primaryColor = (ThemeHelper.PrimaryColor) item.getId();
                            colors.set(position, primaryColor);
                            toolbar.setBackgroundColor(primaryColor.color);
                        }

                        @Override
                        public void onFloatingMenuDismissed(FloatingMenu menu) {
                        }
                    });
                    menu.show();
                }
            };
            toolbar.setCallback(new Toolbar.ToolbarCallback() {
                @Override
                public void onMenuOrBackClicked(boolean isArrow) {
                    colorClick.onClick(toolbar);
                }

                @Override
                public void onSearchVisibilityChanged(boolean visible) {
                }

                @Override
                public String getSearchHint() {
                    return null;
                }

                @Override
                public void onSearchEntered(String entered) {
                }
            });
            toolbar.setBackgroundColor(theme.primaryColor.color);
            final NavigationItem item = new NavigationItem();
            item.title = theme.displayName;
            item.hasBack = false;
            toolbar.setNavigationItem(false, true, item);
            toolbar.setOnClickListener(colorClick);

            linearLayout.addView(toolbar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    themeContext.getResources().getDimensionPixelSize(R.dimen.toolbar_height)));

            PostCell postCell = (PostCell) LayoutInflater.from(themeContext).inflate(R.layout.cell_post, null);
            postCell.setPost(theme, post, DUMMY_POST_CALLBACK, false, -1);
            linearLayout.addView(postCell, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            return linearLayout;
        }

        @Override
        public int getCount() {
            return themes.size();
        }
    }

    private class ColorsAdapter extends BaseAdapter {
        private List<FloatingMenuItem> items;

        public ColorsAdapter(List<FloatingMenuItem> items) {
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            @SuppressLint("ViewHolder")
            TextView textView = (TextView) LayoutInflater.from(context).inflate(R.layout.toolbar_menu_item, parent, false);
            textView.setText(getItem(position));
            textView.setTypeface(AndroidUtils.ROBOTO_MEDIUM);

            ThemeHelper.PrimaryColor color = (ThemeHelper.PrimaryColor) items.get(position).getId();

            textView.setBackgroundColor(color.color);
            boolean lightColor = (Color.red(color.color) * 0.299f) + (Color.green(color.color) * 0.587f) + (Color.blue(color.color) * 0.114f) > 125f;
            textView.setTextColor(lightColor ? 0xff000000 : 0xffffffff);

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
}
