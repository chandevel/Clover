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
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.floens.chan.R;
import org.floens.chan.core.presenter.SitesSetupPresenter;
import org.floens.chan.core.presenter.SitesSetupPresenter.SiteBoardCount;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.ui.helper.HintPopup;
import org.floens.chan.ui.layout.SiteAddLayout;
import org.floens.chan.ui.view.CrossfadeView;
import org.floens.chan.ui.view.DividerItemDecoration;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;

public class SitesSetupController extends StyledToolbarNavigationController implements
        SitesSetupPresenter.Callback,
        View.OnClickListener {

    @Inject
    SitesSetupPresenter presenter;

    private CrossfadeView crossfadeView;
    private RecyclerView sitesRecyclerview;
    private FloatingActionButton addButton;

    private HintPopup addBoardsHint;

    private SitesAdapter sitesAdapter;
    private ItemTouchHelper itemTouchHelper;
    private List<SiteBoardCount> sites = new ArrayList<>();

    private ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN,
            0
    ) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();

            presenter.move(from, to);

            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        }
    };

    public SitesSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        // Inflate
        view = inflateRes(R.layout.controller_sites_setup);

        // Navigation
        navigation.setTitle(R.string.setup_sites_title);

        // View binding
        crossfadeView = view.findViewById(R.id.crossfade);
        sitesRecyclerview = view.findViewById(R.id.sites_recycler);
        addButton = view.findViewById(R.id.add);

        // Adapters
        sitesAdapter = new SitesAdapter();

        // View setup
        sitesRecyclerview.setLayoutManager(new LinearLayoutManager(context));
        sitesRecyclerview.setAdapter(sitesAdapter);
        sitesRecyclerview.addItemDecoration(
                new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(sitesRecyclerview);
        addButton.setOnClickListener(this);
        theme().applyFabColor(addButton);
        crossfadeView.toggle(false, false);

        // Presenter
        presenter.create(this);
    }

    @Override
    public void onShow() {
        super.onShow();

        presenter.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        presenter.destroy();
    }

    @Override
    public void onClick(View v) {
        if (v == addButton) {
            presenter.onShowDialogClicked();
        }
    }

    @Override
    public void showHint() {
        String s = context.getString(R.string.setup_sites_add_hint);
        HintPopup popup = new HintPopup(context, addButton, s, 0, 0, true);
        popup.wiggle();
        popup.show();
    }

    @Override
    public void showAddDialog() {
        @SuppressLint("InflateParams") final SiteAddLayout dialogView =
                (SiteAddLayout) LayoutInflater.from(context)
                        .inflate(R.layout.layout_site_add, null);

        dialogView.setPresenter(presenter);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setTitle(R.string.setup_sites_add_title)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialogView.setDialog(dialog);

        dialog.show();

        Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setOnClickListener((v) -> {
            dialogView.onPositiveClicked();
        });
    }

    @Override
    public void openSiteConfiguration(Site site) {
        SiteSetupController c = new SiteSetupController(context);
        c.setSite(site);
        navigationController.pushController(c);
    }

    @Override
    public void setSites(List<SiteBoardCount> sites) {
        this.sites.clear();
        this.sites.addAll(sites);
        sitesAdapter.notifyDataSetChanged();

        crossfadeView.toggle(!sites.isEmpty(), true);
    }

    private void onSiteCellSettingsClicked(Site site) {
        presenter.onSiteCellSettingsClicked(site);
    }

    private class SitesAdapter extends RecyclerView.Adapter<SiteCell> {
        public SitesAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return sites.get(position).site.id();
        }

        @NonNull
        @Override
        public SiteCell onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SiteCell(LayoutInflater.from(context).inflate(R.layout.cell_site, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SiteCell holder, int position) {
            SiteBoardCount site = sites.get(position);
            holder.setSite(site.site);
            holder.setSiteIcon(site.site);
            holder.text.setText(site.site.name());

            int boards = site.boardCount;
            String boardsString = context.getResources().getQuantityString(R.plurals.board, boards, boards);
            String descriptionText = context.getString(R.string.setup_sites_site_description, boardsString);
            holder.description.setText(descriptionText);

            if (boards == 0) {
                if (addBoardsHint != null) {
                    addBoardsHint.dismiss();
                }
                addBoardsHint = HintPopup.show(context, holder.settings, R.string.setup_sites_add_boards_hint);
                addBoardsHint.wiggle();
            }
        }

        @Override
        public int getItemCount() {
            return sites.size();
        }
    }

    private class SiteCell extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView image;
        private TextView text;
        private TextView description;
        private SiteIcon siteIcon;
        private ImageView settings;
        private ImageView reorder;

        private Site site;

        @SuppressLint("ClickableViewAccessibility")
        public SiteCell(View itemView) {
            super(itemView);

            // Bind views
            image = itemView.findViewById(R.id.image);
            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);
            settings = itemView.findViewById(R.id.settings);
            reorder = itemView.findViewById(R.id.reorder);

            // Setup views
            itemView.setOnClickListener(this);
            setRoundItemBackground(settings);
            theme().settingsDrawable.apply(settings);

            Drawable drawable = DrawableCompat.wrap(
                    context.getResources().getDrawable(R.drawable.ic_reorder_black_24dp)).mutate();
            DrawableCompat.setTint(drawable, getAttrColor(context, R.attr.text_color_hint));
            reorder.setImageDrawable(drawable);

            reorder.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this);
                }
                return false;
            });
        }

        private void setSite(Site site) {
            this.site = site;
        }

        private void setSiteIcon(Site site) {
            siteIcon = site.icon();
            siteIcon.get((siteIcon, icon) -> {
                if (SiteCell.this.siteIcon == siteIcon) {
                    image.setImageDrawable(icon);
                }
            });
        }

        @Override
        public void onClick(View v) {
            if (v == itemView) {
                onSiteCellSettingsClicked(site);
            }
        }
    }
}
