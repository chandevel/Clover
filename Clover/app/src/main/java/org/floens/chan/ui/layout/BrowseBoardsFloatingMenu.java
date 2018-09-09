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
package org.floens.chan.ui.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.presenter.BoardsMenuPresenter;
import org.floens.chan.core.presenter.BoardsMenuPresenter.Item;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.utils.AndroidUtils;

import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.removeFromParentView;

/**
 * A ViewGroup that attaches above the entire window, containing a list of boards the user can
 * select. The list is aligned to a view, given to
 * {@link #show(ViewGroup, View, ClickCallback, Board)}.
 * This view completely covers the window to catch any input that goes outside the inner list view.
 * It also features a search field at the top. The data shown is controlled by
 * {@link BoardsMenuPresenter}.
 */
public class BrowseBoardsFloatingMenu extends FrameLayout implements BoardsMenuPresenter.Callback,
        Observer {
    private static final int MINIMAL_WIDTH_DP = 4 * 56;
    private static final int ELEVATION_DP = 4;
    private static final int OFFSET_X_DP = 5;
    private static final int OFFSET_Y_DP = 5;
    private static final int MARGIN_DP = 5;
    private static final int ANIMATE_IN_TRANSLATION_Y_DP = 25;

    private View anchor;
    private RecyclerView recyclerView;

    private Point position = new Point(0, 0);
    private boolean dismissed = false;

    @Inject
    private BoardsMenuPresenter presenter;
    private BoardsMenuPresenter.Items items;

    private BrowseBoardsAdapter adapter;

    private ClickCallback clickCallback;
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;

    public BrowseBoardsFloatingMenu(Context context) {
        this(context, null);
    }

    public BrowseBoardsFloatingMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BrowseBoardsFloatingMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inject(this);

        layoutListener = this::repositionToAnchor;

        setFocusableInTouchMode(true);
        setFocusable(true);
    }

    public void show(ViewGroup baseView, View anchor, ClickCallback clickCallback,
                     Board selectedBoard) {
        this.anchor = anchor;
        this.clickCallback = clickCallback;

        ViewGroup rootView = baseView.getRootView().findViewById(android.R.id.content);

        setupChildViews();

        adapter = new BrowseBoardsAdapter();

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);

        rootView.addView(this, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        requestFocus();

        watchAnchor();

        animateIn();

        presenter.create(this, selectedBoard);
        items = presenter.items();
        items.addObserver(this);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == presenter.items()) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void scrollToPosition(int position) {
        recyclerView.scrollToPosition(position);
    }

    private void itemClicked(Site site, Board board) {
        if (!isInteractive()) return;

        if (board != null) {
            clickCallback.onBoardClicked(board);
        } else {
            clickCallback.onSiteClicked(site);
        }
        dismiss();
    }

    private void inputChanged(String input) {
        presenter.filterChanged(input);
    }

    private void dismiss() {
        if (dismissed) return;
        dismissed = true;

        items.deleteObserver(this);
        presenter.destroy();

        AndroidUtils.hideKeyboard(this);

        // ???
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            anchor.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        }

        animateOut(() -> removeFromParentView(this));
    }

    private void setupChildViews() {
        // View creation
        recyclerView = new RecyclerView(getContext());

        // View setup
        recyclerView.setBackgroundColor(AndroidUtils.getAttrColor(getContext(), R.attr.backcolor));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            recyclerView.setElevation(dp(ELEVATION_DP));
        }

        // View attaching
        int recyclerWidth = Math.max(
                anchor.getWidth(),
                dp(MINIMAL_WIDTH_DP));

        LayoutParams params = new LayoutParams(
                recyclerWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(MARGIN_DP);
        params.topMargin = dp(MARGIN_DP);
        params.rightMargin = dp(MARGIN_DP);
        params.bottomMargin = dp(MARGIN_DP);
        addView(recyclerView, params);
    }

    private void watchAnchor() {
        repositionToAnchor();
        anchor.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    private void repositionToAnchor() {
        int[] anchorPos = new int[2];
        int[] recyclerViewPos = new int[2];
        anchor.getLocationInWindow(anchorPos);
        recyclerView.getLocationInWindow(recyclerViewPos);
        anchorPos[0] += dp(OFFSET_X_DP);
        anchorPos[1] += dp(OFFSET_Y_DP);
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
        if (isInteractive() && keyCode == KeyEvent.KEYCODE_BACK && event.isTracking() &&
                !event.isCanceled()) {
            dismiss();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isInteractive()) return super.onTouchEvent(event);

        dismiss();
        return true;
    }

    private boolean isInteractive() {
        return !dismissed;
    }

    private void animateIn() {
        setAlpha(0f);
        setTranslationY(-dp(ANIMATE_IN_TRANSLATION_Y_DP));
		post(() -> animate()
				.alpha(1f)
				.translationY(0f)
				.setInterpolator(new DecelerateInterpolator(2f))
				.setDuration(250).start());
    }

    private void animateOut(Runnable done) {
        animate().alpha(0f)
                .setInterpolator(new DecelerateInterpolator(2f)).setDuration(250)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        done.run();
                    }
                })
                .start();
    }

    private class BrowseBoardsAdapter extends RecyclerView.Adapter<ViewHolder> {
        public BrowseBoardsAdapter() {
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            return items.getCount();
        }

        @Override
        public long getItemId(int position) {
            Item item = items.getAtPosition(position);
            return item.id;
        }

        @Override
        public int getItemViewType(int position) {
            Item item = items.getAtPosition(position);
            return item.type.typeId;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == Item.Type.SEARCH.typeId) {
                return new InputViewHolder(inflater.inflate(
                        R.layout.cell_browse_input, parent, false));
            } else if (viewType == Item.Type.SITE.typeId) {
                return new SiteViewHolder(inflater.inflate(
                        R.layout.cell_browse_site, parent, false));
            } else if (viewType == Item.Type.BOARD.typeId) {
                return new BoardViewHolder(inflater.inflate(
                        R.layout.cell_browse_board, parent, false));
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Item item = items.getAtPosition(position);
            if (holder instanceof InputViewHolder) {
                InputViewHolder inputViewHolder = ((InputViewHolder) holder);
            } else if (holder instanceof SiteViewHolder) {
                SiteViewHolder siteViewHolder = ((SiteViewHolder) holder);
                siteViewHolder.bind(item.site);
            } else if (holder instanceof BoardViewHolder) {
                BoardViewHolder boardViewHolder = ((BoardViewHolder) holder);
                boardViewHolder.bind(item.board);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private class InputViewHolder extends ViewHolder implements TextWatcher,
            OnFocusChangeListener, OnClickListener, OnKeyListener {
        private EditText input;

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
                AndroidUtils.hideKeyboard(v);
            }
        }

        @Override
        public void onClick(View v) {
            ((LinearLayoutManager) recyclerView.getLayoutManager())
                    .scrollToPositionWithOffset(0, 0);
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss();
            }
            return true;
        }
    }

    private class SiteViewHolder extends ViewHolder {
        View divider;
        ImageView image;
        TextView text;

        Site site;
        SiteIcon icon;

        public SiteViewHolder(View itemView) {
            super(itemView);

            itemView.setOnClickListener((v) -> itemClicked(site, null));

            // View binding
            divider = itemView.findViewById(R.id.divider);
            image = itemView.findViewById(R.id.image);
            text = itemView.findViewById(R.id.text);

            // View setup
            text.setTypeface(AndroidUtils.ROBOTO_MEDIUM);
        }

        public void bind(Site site) {
            this.site = site;

            divider.setVisibility(getAdapterPosition() == 0 ? View.GONE : View.VISIBLE);

            icon = site.icon();

            image.setTag(icon);
            image.setImageDrawable(null);
            icon.get((siteIcon, drawable) -> {
                if (image.getTag() == siteIcon) {
                    image.setImageDrawable(drawable);
                }
            });

            text.setText(site.name());
        }
    }

    private class BoardViewHolder extends ViewHolder {
        TextView text;

        Board board;

        public BoardViewHolder(View itemView) {
            super(itemView);

            itemView.setOnClickListener((v) -> itemClicked(null, board));

            // View binding
            text = (TextView) itemView;

            // View setup
            text.setTypeface(AndroidUtils.ROBOTO_MEDIUM);
        }

        public void bind(Board board) {
            this.board = board;
            text.setText(BoardHelper.getName(board));
        }
    }

    public interface ClickCallback {
        void onBoardClicked(Board item);

        void onSiteClicked(Site site);
    }
}
