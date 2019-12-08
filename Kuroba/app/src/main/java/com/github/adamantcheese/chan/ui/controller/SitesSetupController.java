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
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.presenter.SitesSetupPresenter;
import com.github.adamantcheese.chan.core.presenter.SitesSetupPresenter.SiteBoardCount;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.layout.SiteAddLayout;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;
import com.github.adamantcheese.chan.ui.view.DividerItemDecoration;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;

public class SitesSetupController
        extends StyledToolbarNavigationController
        implements SitesSetupPresenter.Callback, View.OnClickListener {

    @Inject
    SitesSetupPresenter presenter;

    private CrossfadeView crossfadeView;
    private FloatingActionButton addButton;

    private HintPopup addBoardsHint;

    private SitesAdapter sitesAdapter;
    private ItemTouchHelper itemTouchHelper;
    private List<SiteBoardCount> sites = new ArrayList<>();

    private ItemTouchHelper.SimpleCallback touchHelperCallback =
            new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(
                        RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target
                ) {
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
        sitesRecyclerview.setLayoutManager(new LinearLayoutManager(context));
        sitesRecyclerview.setAdapter(sitesAdapter);
        sitesRecyclerview.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(sitesRecyclerview);
        addButton.setOnClickListener(this);
        ThemeHelper.getTheme().applyFabColor(addButton);
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
        String s = getString(R.string.setup_sites_add_hint);
        HintPopup popup = new HintPopup(context, addButton, s, 0, 0, true);
        popup.wiggle();
        popup.show();
    }

    @Override
    public void showAddDialog() {
        @SuppressLint("InflateParams")
        final SiteAddLayout dialogView = (SiteAddLayout) inflate(context, R.layout.layout_site_add, null);

        dialogView.setPresenter(presenter);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(dialogView)
                .setTitle(R.string.setup_sites_add_title)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialogView.setDialog(dialog);

        dialog.show();

        Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> dialogView.onPositiveClicked());
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
            holder.setSiteIcon(site.site);
            holder.text.setText(site.site.name());

            int boards = site.boardCount;
            String boardsString = getQuantityString(R.plurals.board, boards, boards);
            String descriptionText = getString(R.string.setup_sites_site_description, boardsString);
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

    private class SiteCell
            extends RecyclerView.ViewHolder
            implements View.OnClickListener {
        private ImageView image;
        private TextView text;
        private TextView description;
        private SiteIcon siteIcon;
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

            setRoundItemBackground(settings);
            setRoundItemBackground(removeSite);
            ThemeHelper.getTheme().settingsDrawable.apply(settings);
            ThemeHelper.getTheme().clearDrawable.apply(removeSite);

            Drawable drawable = DrawableCompat.wrap(context.getDrawable(R.drawable.ic_reorder_black_24dp)).mutate();
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
                    image.getDrawable().setTintList(null);
                }
            });
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
}
