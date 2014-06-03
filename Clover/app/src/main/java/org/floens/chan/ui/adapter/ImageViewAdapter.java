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
package org.floens.chan.ui.adapter;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;

import org.floens.chan.core.model.Post;
import org.floens.chan.ui.activity.ImageViewActivity;
import org.floens.chan.ui.fragment.ImageViewFragment;

import java.util.ArrayList;
import java.util.List;

public class ImageViewAdapter extends FragmentStatePagerAdapter {
    private final ImageViewActivity activity;
    private final ArrayList<Post> postList = new ArrayList<>();

    public ImageViewAdapter(FragmentManager fragmentManager, ImageViewActivity activity) {
        super(fragmentManager);
        this.activity = activity;
    }

    @Override
    public int getCount() {
        return postList.size();
    }

    @Override
    public Fragment getItem(int position) {
        return ImageViewFragment.newInstance(postList.get(position), activity, position);
    }

    public Post getPost(int position) {
        if (position < 0 || position >= getCount())
            return null;

        return postList.get(position);
    }

    @Override
    public void destroyItem(View collection, int position, Object o) {
        View view = (View) o;
        ((ViewPager) collection).removeView(view);
        view = null;
    }

    public void setList(ArrayList<Post> list) {
        postList.clear();
        postList.addAll(list);

        notifyDataSetChanged();
    }

    public List<Post> getList() {
        return postList;
    }
}
