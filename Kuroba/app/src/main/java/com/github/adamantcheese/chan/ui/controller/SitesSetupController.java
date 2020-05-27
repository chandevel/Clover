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
package com.github.adamantcheese.chan.ui.controller;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig;
import com.github.adamantcheese.chan.core.presenter.SitesSetupPresenter;
import com.github.adamantcheese.chan.core.presenter.SitesSetupPresenter.SiteBoardCount;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.json.JsonSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteRegistry;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;
import com.github.adamantcheese.chan.ui.view.DividerItemDecoration;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

public class SitesSetupController
        extends StyledToolbarNavigationController
        implements SitesSetupPresenter.Callback, View.OnClickListener {

    @Inject
    SitesSetupPresenter presenter;

    private CrossfadeView crossfadeView;
    private FloatingActionButton addButton;
    @Nullable
    private HintPopup hintPopup = null;
    private SitesAdapter sitesAdapter;
    private ItemTouchHelper itemTouchHelper;
    private List<SiteBoardCount> sites = new ArrayList<>();

    private ItemTouchHelper.SimpleCallback touchHelperCallback =
            new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(
                        RecyclerView recyclerView, ViewHolder viewHolder, ViewHolder target
                ) {
                    int from = viewHolder.getAdapterPosition();
                    int to = target.getAdapterPosition();

                    presenter.move(from, to);

                    return true;
                }

                @Override
                public void onSwiped(ViewHolder viewHolder, int direction) {
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
        view = inflate(context, R.layout.controller_sites_setup);

        // Navigation
        navigation.setTitle(R.string.setup_sites_title);

        // View binding
        crossfadeView = view.findViewById(R.id.crossfade);
        RecyclerView sitesRecyclerview = view.findViewById(R.id.sites_recycler);
        addButton = view.findViewById(R.id.add);

        // Adapters
        sitesAdapter = new SitesAdapter();

        // View setup
        sitesRecyclerview.setAdapter(sitesAdapter);
        sitesRecyclerview.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(sitesRecyclerview);
        addButton.setOnClickListener(this);
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

        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }

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
        String s = getString(R.string.setup_sites_add_hint);

        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }

        hintPopup = new HintPopup(context, addButton, s, 0, 0, true);
        hintPopup.wiggle();
        hintPopup.show();
    }

    @Override
    public void showAddDialog() {
        @SuppressLint("InflateParams")
        final ListView dialogView = new ListView(context);
        SitePreviewAdapter adapter = new SitePreviewAdapter();
        if (adapter.sites.isEmpty()) {
            showToast(context, "All sites added!");
            return;
        }
        dialogView.setAdapter(adapter);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(dialogView).create();
        dialog.show();
        adapter.setDialog(dialog);
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

    @Override
    public void onSiteDeleted(Site site) {
        ((StartActivity) context).restartApp();
    }

    private void onSiteCellSettingsClicked(Site site) {
        presenter.onSiteCellSettingsClicked(site);
    }

    private void onRemoveSiteSettingClicked(Site site) {
        new AlertDialog.Builder(context).setTitle(getString(R.string.delete_site_dialog_title))
                .setMessage(getString(R.string.delete_site_dialog_message, site.name()))
                .setPositiveButton(R.string.delete, (dialog, which) -> presenter.removeSite(site))
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private class SitesAdapter
            extends RecyclerView.Adapter<SiteCell> {
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
            return new SiteCell(inflate(parent.getContext(), R.layout.cell_site, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SiteCell holder, int position) {
            SiteBoardCount site = sites.get(position);
            holder.setSite(site.site);
            holder.text.setText(site.site.name());

            String descriptionText = getQuantityString(R.plurals.board, site.boardCount, site.boardCount);
            holder.description.setText(descriptionText);

            if (site.boardCount == 0) {
                if (hintPopup != null) {
                    hintPopup.dismiss();
                    hintPopup = null;
                }

                hintPopup = HintPopup.show(context, holder.settings, R.string.setup_sites_add_boards_hint);
                hintPopup.wiggle();
            }
        }

        @Override
        public int getItemCount() {
            return sites.size();
        }
    }

    private class SiteCell
            extends ViewHolder
            implements View.OnClickListener {
        private ImageView image;
        private TextView text;
        private TextView description;
        private ImageView removeSite;
        private ImageView settings;

        private Site site;

        @SuppressLint("ClickableViewAccessibility")
        public SiteCell(View itemView) {
            super(itemView);

            // Bind views
            image = itemView.findViewById(R.id.image);
            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);
            removeSite = itemView.findViewById(R.id.remove_site);
            settings = itemView.findViewById(R.id.settings);
            ImageView reorder = itemView.findViewById(R.id.reorder);

            // Setup views
            itemView.setOnClickListener(this);
            removeSite.setOnClickListener(this);

            reorder.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this);
                }
                return false;
            });
        }

        private void setSite(Site site) {
            this.site = site;
            site.icon().get(image::setImageDrawable);
        }

        @Override
        public void onClick(View v) {
            if (v == removeSite) {
                onRemoveSiteSettingClicked(site);
            } else if (v == itemView) {
                onSiteCellSettingsClicked(site);
            }
        }
    }

    private class SitePreviewAdapter
            extends BaseAdapter {

        @Inject
        SiteRepository siteRepository;

        private List<Class<? extends Site>> sites;
        private AlertDialog dialog;

        public SitePreviewAdapter() {
            inject(this);
            sites = new ArrayList<>();
            List<String> addedSites = new ArrayList<>();
            for (Site s : siteRepository.all().getAll()) {
                addedSites.add(s.getClass().getSimpleName());
            }
            for (int i = 0; i < SiteRegistry.SITE_CLASSES.size(); i++) {
                Class<? extends Site> s = SiteRegistry.SITE_CLASSES.valueAt(i);
                if (!addedSites.contains(s.getSimpleName())) {
                    sites.add(s);
                }
            }
        }

        public void setDialog(AlertDialog dialog) {
            this.dialog = dialog;
        }

        @Override
        public int getCount() {
            return sites.size();
        }

        @Override
        public Object getItem(int position) {
            return sites.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Site s = siteRepository.instantiateSiteClass(sites.get(position));
            s.initialize(0, new SiteConfig(), new JsonSettings());
            LinearLayout previewCell = (LinearLayout) inflate(context, R.layout.layout_site_preview);
            ImageView favicon = previewCell.findViewById(R.id.site_icon);
            TextView siteName = previewCell.findViewById(R.id.site_name);
            s.icon().get(favicon::setImageDrawable);
            siteName.setText(s.name());
            previewCell.setOnClickListener((v -> {
                presenter.onAddClicked(sites.get(position));
                dialog.dismiss();
            }));
            return previewCell;
        }
    }
}
