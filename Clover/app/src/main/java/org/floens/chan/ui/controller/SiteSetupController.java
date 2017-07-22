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


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.support.v4.view.animation.PathInterpolatorCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.transition.FadeInTransition;
import org.floens.chan.core.presenter.SetupPresenter;
import org.floens.chan.ui.animation.AnimationUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class SiteSetupController extends StyledToolbarNavigationController implements View.OnClickListener, SetupPresenter.Callback {
    private EditText url;
    private View urlSubmit;
    private View spinner;
    private Button next;

    private boolean blocked = false;

    private SetupPresenter presenter;

    private RecyclerView sitesRecyclerview;
    private SitesAdapter sitesAdapter;
    private List<SetupPresenter.AddedSite> sites = new ArrayList<>();

    public SiteSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_site_setup);
        navigationItem.setTitle(R.string.setup_title);

        url = (EditText) view.findViewById(R.id.site_url);
        urlSubmit = view.findViewById(R.id.site_url_submit);
        urlSubmit.setOnClickListener(this);
        spinner = view.findViewById(R.id.progress);
        sitesRecyclerview = (RecyclerView) view.findViewById(R.id.sites_recycler);
        sitesRecyclerview.setLayoutManager(new LinearLayoutManager(context));
        next = (Button) view.findViewById(R.id.next_button);
        next.setOnClickListener(this);

        presenter = new SetupPresenter();

        sitesAdapter = new SitesAdapter();
        sitesRecyclerview.setAdapter(sitesAdapter);

        presenter.create(this);
    }

    @Override
    public void onClick(View v) {
        if (blocked) return;

        if (v == urlSubmit) {
            presenter.onUrlSubmitClicked(url.getText().toString());
        } else if (v == next) {
            presenter.onNextClicked();
        }
    }

    @Override
    public boolean onBack() {
        if (presenter.mayExit()) {
            return super.onBack();
        } else {
            return true;
        }
    }

    @Override
    public void moveToSavedBoards() {
        navigationController.pushController(new BoardSetupController(context), new FadeInTransition());
    }

    @Override
    public void goToUrlSubmittedState() {
        spinner.setVisibility(View.VISIBLE);
        urlSubmit.setVisibility(View.INVISIBLE);
    }

    @Override
    public void runSiteAddedAnimation(final SetupPresenter.AddedSite site) {
        spinner.setVisibility(View.INVISIBLE);
        urlSubmit.setVisibility(View.VISIBLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            runSiteAddedAnimationInternal(site);
        } else {
            resetUrl();
        }
    }

    @Override
    public void showUrlHint(String text) {
        spinner.setVisibility(View.INVISIBLE);
        urlSubmit.setVisibility(View.VISIBLE);

        url.setError(text, null);
    }

    @Override
    public void setAddedSites(List<SetupPresenter.AddedSite> sites) {
        this.sites.clear();
        this.sites.addAll(sites);
        sitesAdapter.notifyDataSetChanged();
    }

    @Override
    public void setNextAllowed(boolean nextAllowed, boolean animate) {
        next.setEnabled(nextAllowed);
        int newBackground = getAttrColor(context, nextAllowed ? R.attr.colorAccent : R.attr.backcolor);
        int newTextColor = nextAllowed ? Color.WHITE : getAttrColor(context, R.attr.text_color_hint);
        if (animate) {
            AnimationUtils.animateTextColor(next, newTextColor);
            AnimationUtils.animateBackgroundColorDrawable(next, newBackground);
        } else {
            next.setBackgroundColor(newBackground);
            next.setTextColor(newTextColor);
        }
    }

    private void resetUrl() {
        url.setText("");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void runSiteAddedAnimationInternal(SetupPresenter.AddedSite site) {
        blocked = true;
        sitesAdapter.invisibleSiteOnBind = site;

        SiteCell siteCell = new SiteCell(LayoutInflater.from(context).inflate(R.layout.cell_site, null));
        siteCell.setSite(site);
        final View siteCellView = siteCell.itemView;
        final View siteCellIcon = siteCell.image;
        siteCellView.measure(View.MeasureSpec.makeMeasureSpec(url.getMeasuredWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        siteCellView.layout(0, 0, siteCellView.getMeasuredWidth(), siteCellView.getMeasuredHeight());

        final ViewGroupOverlay overlay = view.getOverlay();

        overlay.add(siteCellView);

        int[] urlWinLoc = new int[2];
        url.getLocationInWindow(urlWinLoc);
        int[] recWinLoc = new int[2];
        sitesRecyclerview.getLocationInWindow(recWinLoc);
        recWinLoc[0] += sitesRecyclerview.getPaddingLeft();
        recWinLoc[1] += sitesRecyclerview.getPaddingTop() + siteCellView.getMeasuredHeight() * (sites.size() - 1);
        int[] viewWinLoc = new int[2];
        view.getLocationInWindow(viewWinLoc);

        String urlText = url.getText().toString();
        int indexOf = urlText.indexOf(site.title);
        int offsetLeft = 0;
        if (indexOf > 0) {
            Paint paint = new Paint();
            paint.setTextSize(url.getTextSize());
            offsetLeft = (int) paint.measureText(urlText.substring(0, indexOf));
        }

        final int staX = urlWinLoc[0] - viewWinLoc[0] - dp(48) + dp(4) + offsetLeft;
        final int staY = urlWinLoc[1] - viewWinLoc[1] - dp(4);
        final int desX = recWinLoc[0] - viewWinLoc[0];
        final int desY = recWinLoc[1] - viewWinLoc[1];

        siteCellView.setTranslationX(staX);
        siteCellView.setTranslationY(staY);

        final int textColor = url.getCurrentTextColor();
        final int textHintColor = url.getCurrentHintTextColor();

        ValueAnimator textAlpha = ValueAnimator.ofObject(new ArgbEvaluator(),
                textColor, textColor & 0xffffff);
        textAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                url.setTextColor((int) animation.getAnimatedValue());
            }
        });
        textAlpha.setDuration(300);
        textAlpha.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                resetUrl();
            }
        });

        url.setCursorVisible(false);
        url.setHintTextColor(0);

        ValueAnimator alpha = ObjectAnimator.ofFloat(siteCellView, View.ALPHA, 0f, 1f);
        alpha.setDuration(300);

        ValueAnimator iconAlpha = ValueAnimator.ofFloat(0f, 1f);
        iconAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                siteCellIcon.setAlpha((float) animation.getAnimatedValue());
            }
        });
        iconAlpha.setDuration(400);
        iconAlpha.setStartDelay(500);
        siteCellIcon.setAlpha(0f);

        ValueAnimator horizontal = ValueAnimator.ofFloat(0f, 1f);
        horizontal.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                siteCellView.setTranslationX(staX + (desX - staX) * value);
            }
        });
        horizontal.setDuration(600);
        horizontal.setStartDelay(400);

        ValueAnimator vertical = ValueAnimator.ofFloat(0f, 1f);
        vertical.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                siteCellView.setTranslationY(staY + (desY - staY) * value);
            }
        });

        Path path = new Path();

        float jump = 300f / Math.abs(desY - staY);
        path.cubicTo(0.5f, 0f - jump, 0.75f, 1.0f, 1f, 1f);
        vertical.setInterpolator(PathInterpolatorCompat.create(path));
        vertical.setDuration(600);
        vertical.setStartDelay(400);

        ValueAnimator hintAlpha = ValueAnimator.ofObject(new ArgbEvaluator(),
                textHintColor & 0xffffff, textHintColor);
        hintAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                url.setHintTextColor((int) animation.getAnimatedValue());
            }
        });
        hintAlpha.setDuration(300);
        hintAlpha.setStartDelay(1000);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, textAlpha, iconAlpha, horizontal, vertical, hintAlpha);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                blocked = false;
                overlay.remove(siteCellView);
                sitesAdapter.invisibleSiteOnBind = null;
                for (int i = 0; i < sitesRecyclerview.getChildCount(); i++) {
                    sitesRecyclerview.getChildAt(i).setVisibility(View.VISIBLE);
                }
                url.setTextColor(textColor);
                url.setHintTextColor(textHintColor);
                url.setCursorVisible(true);
            }
        });
        set.start();
    }

    private class SitesAdapter extends RecyclerView.Adapter<SiteCell> {
        private SetupPresenter.AddedSite invisibleSiteOnBind;

        public SitesAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return sites.get(position).id;
        }

        @Override
        public SiteCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SiteCell(LayoutInflater.from(context).inflate(R.layout.cell_site, parent, false));
        }

        @Override
        public void onBindViewHolder(SiteCell holder, int position) {
            SetupPresenter.AddedSite site = sites.get(position);
            holder.setSite(site);
            if (site == invisibleSiteOnBind) {
                holder.itemView.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return sites.size();
        }
    }

    private class SiteCell extends RecyclerView.ViewHolder {
        private ImageView image;
        private TextView text;

        public SiteCell(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.image);
            text = (TextView) itemView.findViewById(R.id.text);
        }

        private void setSite(SetupPresenter.AddedSite site) {
            image.setImageDrawable(site.drawable);
            text.setText(site.title);
        }
    }
}
