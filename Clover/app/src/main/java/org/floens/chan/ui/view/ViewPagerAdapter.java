package org.floens.chan.ui.view;

import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import org.floens.chan.utils.AndroidUtils;

public abstract class ViewPagerAdapter extends PagerAdapter {
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        return getView(position);
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        AndroidUtils.removeFromParentView((View) object);
    }

    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    public abstract View getView(int position);
}
