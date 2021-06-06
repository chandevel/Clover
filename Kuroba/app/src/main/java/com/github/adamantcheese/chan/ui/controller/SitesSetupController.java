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
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.presenter.SitesSetupPresenter;
import com.github.adamantcheese.chan.core.presenter.SitesSetupPresenter.SiteBoardCount;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteRegistry;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.RecyclerUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.ArrowPositionRules;
import com.skydoves.balloon.Balloon;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class SitesSetupController
        extends StyledToolbarNavigationController
        implements SitesSetupPresenter.Callback {

    @Inject
    SiteRepository siteRepository;

    SitesSetupPresenter presenter;

    private CrossfadeView crossfadeView;
    private FloatingActionButton addButton;
    private SitesAdapter sitesAdapter;
    private ItemTouchHelper itemTouchHelper;
    private final List<SiteBoardCount> sites = new ArrayList<>();

    private final ItemTouchHelper.SimpleCallback touchHelperCallback =
            new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder, ViewHolder target) {
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

        // Inflate
        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_sites_setup, null);

        // Navigation
        navigation.setTitle(R.string.setup_sites_title);

        // View binding
        crossfadeView = view.findViewById(R.id.crossfade);
        RecyclerView sitesRecyclerview = view.findViewById(R.id.sites_recycler);
        addButton = view.findViewById(R.id.add);

        // Adapters
        sitesAdapter = new SitesAdapter();

        // View setup
        sitesRecyclerview.getLayoutManager().setItemPrefetchEnabled(false);
        sitesRecyclerview.setAdapter(sitesAdapter);
        sitesRecyclerview.addItemDecoration(RecyclerUtils.getBottomDividerDecoration(context));

        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(sitesRecyclerview);
        addButton.setOnClickListener(v -> showAddDialog());
        crossfadeView.toggle(false, false);

        // Presenter
        presenter = new SitesSetupPresenter(context, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        presenter.destroy();
    }

    private void showAddDialog() {
        final RecyclerView dialogView = new RecyclerView(context);
        dialogView.setLayoutManager(new LinearLayoutManager(context));
        dialogView.addItemDecoration(RecyclerUtils.getBottomDividerDecoration(context));

        SitePreviewAdapter adapter = new SitePreviewAdapter();
        if (adapter.siteClasses.isEmpty()) {
            showToast(context, "All sites added!");
            return;
        }
        dialogView.setAdapter(adapter);

        final AlertDialog dialog = getDefaultAlertBuilder(context).setView(dialogView).create();
        adapter.setDialog(dialog);
        dialog.show();
    }

    @Override
    public void setSites(List<SiteBoardCount> sites) {
        this.sites.clear();
        this.sites.addAll(sites);
        sitesAdapter.notifyDataSetChanged();

        crossfadeView.toggle(!sites.isEmpty(), true);
        if (!sites.isEmpty()) {
            AndroidUtils.getBaseToolTip(context)
                    .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                    .setArrowOrientation(ArrowOrientation.BOTTOM)
                    .setTextResource(R.string.setup_sites_add_hint)
                    .setPreferenceName("AddSite")
                    .build()
                    .showAlignTop(addButton);
        }
    }

    private class SitesAdapter
            extends RecyclerView.Adapter<SiteHolder> {
        public SitesAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return sites.get(position).site.id();
        }

        @NonNull
        @Override
        public SiteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SiteHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_site, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SiteHolder holder, int position) {
            holder.setSiteBoardCount(sites.get(position));
        }

        @Override
        public void onViewRecycled(@NonNull SiteHolder holder) {
            holder.image.setImageDrawable(null);
            holder.text.setText("");
            holder.description.setText("");
            if (holder.hint != null) {
                holder.hint.dismiss();
                holder.hint = null;
            }
        }

        @Override
        public int getItemCount() {
            return sites.size();
        }
    }

    private class SiteHolder
            extends ViewHolder {
        private final ImageView image;
        private final TextView text;
        private final TextView description;

        private Site site;
        private Balloon hint;

        @SuppressLint("ClickableViewAccessibility")
        public SiteHolder(View itemView) {
            super(itemView);

            // Bind views
            image = itemView.findViewById(R.id.image);
            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);
            ImageView removeSite = itemView.findViewById(R.id.remove_site);
            ImageView reorder = itemView.findViewById(R.id.reorder);

            // Setup views
            itemView.setOnClickListener(v -> navigationController.pushController(new SiteSetupController(context,
                    site
            )));
            removeSite.setOnClickListener(v -> getDefaultAlertBuilder(v.getContext()).setTitle(getString(R.string.delete_site_dialog_title))
                    .setMessage(getString(R.string.delete_site_dialog_message, site.name()))
                    .setPositiveButton(R.string.delete, (dialog, which) -> presenter.removeSite(site))
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .create()
                    .show());
            // even though we don't react to click events, for ripple drawing this needs to be set
            reorder.setOnClickListener(v -> {});

            reorder.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this);
                }
                return false;
            });
        }

        private void setSiteBoardCount(SiteBoardCount siteBoardCount) {
            this.site = siteBoardCount.site;
            site.icon().get(image::setImageDrawable);
            text.setText(site.name());

            int boardCount = siteBoardCount.boardCount;
            String descriptionText = getQuantityString(R.plurals.board, boardCount, boardCount);
            description.setText(descriptionText);

            if (boardCount == 0) {
                hint = AndroidUtils.getBaseToolTip(context)
                        .setPreferenceName("AddBords")
                        .setArrowOrientation(ArrowOrientation.LEFT)
                        .setTextResource(R.string.setup_sites_add_boards_hint)
                        .build();
                hint.showAlignRight(description);
            }
        }
    }

    private class SitePreviewAdapter
            extends RecyclerView.Adapter<SitePreviewAdapter.NewSiteViewHolder> {

        private final List<Class<? extends Site>> siteClasses = new ArrayList<>();
        private AlertDialog dialog;

        public SitePreviewAdapter() {
            List<String> addedSites = new ArrayList<>();
            for (Site s : siteRepository.all().getAll()) {
                addedSites.add(s.getClass().getSimpleName());
            }
            for (Class<? extends Site> s : SiteRegistry.SITE_CLASSES.values()) {
                if (!addedSites.contains(s.getSimpleName())) {
                    siteClasses.add(s);
                }
            }
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public NewSiteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new NewSiteViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_site_preview, null)) {};
        }

        @Override
        public void onBindViewHolder(@NonNull NewSiteViewHolder holder, int position) {
            Site s = siteRepository.instantiateSiteClass(siteClasses.get(position));
            s.icon().get(holder.favicon::setImageDrawable);
            holder.siteName.setText(s.name());
            holder.itemView.setOnClickListener((v -> {
                presenter.onAddClicked(siteClasses.get(position));
                dialog.dismiss();
            }));
        }

        public void setDialog(AlertDialog dialog) {
            this.dialog = dialog;
        }

        @Override
        public long getItemId(int position) {
            return siteClasses.get(position).hashCode();
        }

        @Override
        public int getItemCount() {
            return siteClasses.size();
        }

        private class NewSiteViewHolder
                extends ViewHolder {
            ImageView favicon;
            TextView siteName;

            public NewSiteViewHolder(@NonNull View itemView) {
                super(itemView);
                favicon = itemView.findViewById(R.id.site_icon);
                siteName = itemView.findViewById(R.id.site_name);
            }
        }
    }
}
