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
package com.github.adamantcheese.chan.ui.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.presenter.BoardsMenuPresenter;
import com.github.adamantcheese.chan.core.presenter.BoardsMenuPresenter.Item;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.RecyclerUtils;

import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.presenter.BoardsMenuPresenter.Item.Type.BOARD;
import static com.github.adamantcheese.chan.core.presenter.BoardsMenuPresenter.Item.Type.SEARCH;
import static com.github.adamantcheese.chan.core.presenter.BoardsMenuPresenter.Item.Type.SITE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.removeFromParentView;

/**
 * A ViewGroup that attaches above the entire window, containing a list of boards the user can
 * select. The list is aligned to a view, given to
 * {@link #show(ViewGroup, View, ClickCallback, Board)}.
 * This view completely covers the window to catch any input that goes outside the inner list view.
 * It also features a search field at the top. The data shown is controlled by
 * {@link BoardsMenuPresenter}.
 */
public class BrowseBoardsFloatingMenu
        extends FrameLayout
        implements BoardsMenuPresenter.Callback, Observer {
    private View anchor;
    private RecyclerView recyclerView;

    private final Point position = new Point(0, 0);
    private boolean dismissed = false;

    @Inject
    private BoardsMenuPresenter presenter;
    private BoardsMenuPresenter.Items items;

    private BrowseBoardsAdapter adapter;

    private ClickCallback clickCallback;
    private OneShotPreDrawListener repositionListener;

    public BrowseBoardsFloatingMenu(Context context) {
        this(context, null);
    }

    public BrowseBoardsFloatingMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BrowseBoardsFloatingMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inject(this);

        setFocusableInTouchMode(true);
        setFocusable(true);
    }

    public void show(ViewGroup baseView, View anchor, ClickCallback clickCallback, Board selectedBoard) {
        this.anchor = anchor;
        this.clickCallback = clickCallback;

        ViewGroup rootView = baseView.getRootView().findViewById(android.R.id.content);

        // View creation
        recyclerView = new RecyclerView(getContext());

        // View setup
        recyclerView.setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
        recyclerView.setElevation(dp(4));

        // View attaching
        int recyclerWidth = Math.max(anchor.getWidth(), dp(4 * 56));
        LayoutParams params = new LayoutParams(recyclerWidth, WRAP_CONTENT);
        params.setMargins(dp(5), dp(5), dp(5), dp(5));
        addView(recyclerView, params);

        adapter = new BrowseBoardsAdapter();

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);

        rootView.addView(this, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        requestFocus();

        watchAnchor();

        animateIn();

        presenter.create(this, selectedBoard, getContext());
        items = presenter.items();
        items.addObserver(this);

        // items should exist before this is added
        recyclerView.addItemDecoration(RecyclerUtils.getDividerDecoration(getContext(),
                new RecyclerUtils.ShowDividerFunction() {
                    @Override
                    public boolean shouldShowDivider(int adapterSize, int adapterPosition) {
                        // ignore first site, search bar acts as a divider
                        return items.getAtPosition(adapterPosition).type == SITE;
                    }

                    @Override
                    public boolean showDividerTop() {
                        return true;
                    }
                }
        ));
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == presenter.items()) {
            adapter.notifyDataSetChanged();
            recyclerView.invalidateItemDecorations();
        }
    }

    @Override
    public void scrollToPosition(int position) {
        recyclerView.scrollToPosition(position);
    }

    private void itemClicked(Site site, Board board) {
        if (!isInteractive()) return;

        if (board != null) {
            clickCallback.setBoard(board);
        } else {
            if (site.name().equals("App Setup")) {
                clickCallback.openSetup();
            } else {
                clickCallback.onSiteClicked(site);
            }
        }
        dismiss(true);
    }

    private void inputChanged(String input) {
        presenter.filterChanged(input);
    }

    private void dismiss(boolean animated) {
        if (dismissed) return;
        dismissed = true;

        items.deleteObserver(this);
        presenter.destroy();

        hideKeyboard(this);

        if (repositionListener != null) {
            repositionListener.removeListener();
            repositionListener = null;
        }

        if (animated) {
            animateOut(() -> removeFromParentView(this));
        } else {
            removeFromParentView(this);
        }
    }

    private void watchAnchor() {
        repositionToAnchor();
        repositionListener = OneShotPreDrawListener.add(anchor, this::repositionToAnchor);
    }

    private void repositionToAnchor() {
        int[] anchorPos = new int[2];
        int[] recyclerViewPos = new int[2];
        anchor.getLocationInWindow(anchorPos);
        recyclerView.getLocationInWindow(recyclerViewPos);
        anchorPos[0] += dp(5);
        anchorPos[1] += dp(5);
        recyclerViewPos[0] += -recyclerView.getTranslationX() - getTranslationX();
        recyclerViewPos[1] += -recyclerView.getTranslationY() - getTranslationY();

        int x = anchorPos[0] - recyclerViewPos[0];
        int y = anchorPos[1] - recyclerViewPos[1];

        if (!position.equals(x, y)) {
            position.set(x, y);

            recyclerView.setTranslationX(x);
            recyclerView.setTranslationY(y);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isInteractive() && keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isInteractive() && keyCode == KeyEvent.KEYCODE_BACK && event.isTracking() && !event.isCanceled()) {
            dismiss(true);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isInteractive()) return super.onTouchEvent(event);

        dismiss(true);
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        dismiss(false);
        super.onDetachedFromWindow();
    }

    private boolean isInteractive() {
        return !dismissed;
    }

    private void animateIn() {
        setAlpha(0f);
        setTranslationY(-dp(25));
        Interpolator slowdown = new DecelerateInterpolator(2f);
        post(() -> animate().alpha(1f).translationY(0f).setInterpolator(slowdown).start());
    }

    private void animateOut(Runnable done) {
        Interpolator slowdown = new DecelerateInterpolator(2f);
        animate().alpha(0f).setInterpolator(slowdown).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                done.run();
            }
        }).start();
    }

    private class BrowseBoardsAdapter
            extends RecyclerView.Adapter<ViewHolder> {
        public BrowseBoardsAdapter() {
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            return items.getCount();
        }

        @Override
        public long getItemId(int position) {
            return items.getAtPosition(position).id;
        }

        @Override
        public int getItemViewType(int position) {
            return items.getAtPosition(position).type.ordinal();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == SEARCH.ordinal()) {
                return new InputViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cell_browse_input, parent, false));
            } else if (viewType == SITE.ordinal()) {
                return new SiteViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cell_browse_site, parent, false));
            } else if (viewType == BOARD.ordinal()) {
                return new BoardViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cell_browse_board, parent, false));
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Item item = items.getAtPosition(position);
            if (holder instanceof SiteViewHolder) {
                SiteViewHolder siteViewHolder = ((SiteViewHolder) holder);
                siteViewHolder.bind(item.site);
            } else if (holder instanceof BoardViewHolder) {
                BoardViewHolder boardViewHolder = ((BoardViewHolder) holder);
                boardViewHolder.bind(item.board);
            }
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            if (holder instanceof SiteViewHolder) {
                SiteViewHolder siteViewHolder = ((SiteViewHolder) holder);
                siteViewHolder.image.setImageDrawable(null);
                siteViewHolder.text.setText("");
                siteViewHolder.site = null;
                siteViewHolder.itemView.setOnClickListener(null);
            } else if (holder instanceof BoardViewHolder) {
                BoardViewHolder boardViewHolder = ((BoardViewHolder) holder);
                boardViewHolder.text.setText("");
                boardViewHolder.board = null;
                boardViewHolder.itemView.setOnClickListener(null);
            }
        }
    }

    private class InputViewHolder
            extends ViewHolder
            implements TextWatcher, OnFocusChangeListener, OnClickListener, OnKeyListener {
        private final EditText input;

        public InputViewHolder(View itemView) {
            super(itemView);

            input = itemView.findViewById(R.id.input);
            input.addTextChangedListener(this);
            input.setOnFocusChangeListener(this);
            input.setOnClickListener(this);
            input.setOnKeyListener(this);
        }

        @Override
        public void afterTextChanged(Editable s) {
            inputChanged(input.getText().toString());
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus) {
                hideKeyboard(v);
            }
        }

        @Override
        public void onClick(View v) {
            ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss(true);
            }
            return true;
        }
    }

    private class SiteViewHolder
            extends ViewHolder {
        ImageView image;
        TextView text;

        Site site;

        public SiteViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image);
            text = itemView.findViewById(R.id.text);
            text.setTypeface(ThemeHelper.getTheme().mainFont);
        }

        public void bind(Site site) {
            this.site = site;
            itemView.setOnClickListener(v -> itemClicked(site, null));
            site.icon().get(image::setImageDrawable);
            text.setText(site.name());
        }
    }

    private class BoardViewHolder
            extends ViewHolder {
        TextView text;

        Board board;

        public BoardViewHolder(View itemView) {
            super(itemView);
            text = (TextView) itemView;
            text.setTypeface(ThemeHelper.getTheme().mainFont);
        }

        public void bind(Board board) {
            this.board = board;
            itemView.setOnClickListener(v -> itemClicked(null, board));
            text.setText(board.getFormattedName());
        }
    }

    public interface ClickCallback {
        void setBoard(Board item);

        void onSiteClicked(Site site);

        void openSetup();
    }
}
