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
package com.github.adamantcheese.chan.utils;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

import java.lang.reflect.Field;

public class RecyclerUtils {
    private static final String TAG = "RecyclerUtils";

    public static void clearRecyclerCache(RecyclerView recyclerView) {
        try {
            Field field = RecyclerView.class.getDeclaredField("mRecycler");
            field.setAccessible(true);
            RecyclerView.Recycler recycler = (RecyclerView.Recycler) field.get(recyclerView);
            recycler.clear();
        } catch (Exception e) {
            Logger.e(TAG, "Error clearing RecyclerView cache with reflection", e);
        }
    }

    public static RecyclerViewPosition getIndexAndTop(RecyclerView recyclerView) {
        int index = 0, top = 0;
        if (recyclerView.getLayoutManager().getChildCount() > 0) {
            View topChild = recyclerView.getLayoutManager().getChildAt(0);
            index = ((RecyclerView.LayoutParams) topChild.getLayoutParams()).getViewLayoutPosition();
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) topChild.getLayoutParams();
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.getPaddingTop();
        }
        return new RecyclerViewPosition(index, top);
    }

    public static final class RecyclerViewPosition {
        public int index;
        public int top;

        public RecyclerViewPosition(int index, int top) {
            this.index = index;
            this.top = top;
        }
    }

    // From https://github.com/DhruvamSharma/Recycler-View-Series; comments are there
    public static RecyclerView.ItemDecoration getDividerDecoration(
            Context context, ShowDividerFunction shouldShowDivider
    ) {
        final ColorDrawable divider = new ColorDrawable(getAttrColor(context, R.attr.divider_color)) {
            @Override
            public int getIntrinsicHeight() {
                return (int) dp(context, 1);
            }
        };

        return new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.onDraw(c, parent, state);
                float paddingPx = dp(parent.getContext(), ChanSettings.fontSize.get() - 6);
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (shouldShowDivider.shouldShowDivider(parent.getAdapter().getItemCount(),
                            parent.getChildAdapterPosition(child)
                    )) {
                        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

                        float dividerTop, dividerBottom;
                        if (shouldShowDivider.showDividerTop()) {
                            dividerBottom = child.getTop() - params.topMargin;
                            dividerTop = dividerBottom - divider.getIntrinsicHeight();
                        } else {
                            dividerTop = child.getBottom() + params.bottomMargin;
                            dividerBottom = dividerTop + divider.getIntrinsicHeight();
                        }

                        divider.setBounds((int) paddingPx,
                                (int) dividerTop,
                                (int) (parent.getWidth() - paddingPx),
                                (int) dividerBottom
                        );
                        divider.draw(c);
                    }
                }
            }

            @Override
            public void getItemOffsets(
                    @NonNull Rect outRect,
                    @NonNull View view,
                    @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state
            ) {
                super.getItemOffsets(outRect, view, parent, state);
                if (shouldShowDivider.showDividerTop()) {
                    outRect.bottom = divider.getIntrinsicHeight();
                } else {
                    outRect.top = divider.getIntrinsicHeight();
                }
            }
        };
    }

    public static RecyclerView.ItemDecoration getBottomDividerDecoration(Context context) {
        return getDividerDecoration(context, new ShowDividerFunction() {
            @Override
            public boolean shouldShowDivider(int adapterSize, int adapterPosition) {
                // ignore the last item
                return adapterPosition != adapterSize - 1;
            }

            @Override
            public boolean showDividerTop() {
                return false;
            }
        });
    }

    public abstract static class ShowDividerFunction {
        public abstract boolean shouldShowDivider(int adapterSize, int adapterPosition);

        /**
         * @return true if the divider be draw on top instead of on the bottom
         */
        public abstract boolean showDividerTop();
    }

    public static class DPSpacingItemDecoration
            extends RecyclerView.ItemDecoration {
        private final float spacing;

        public DPSpacingItemDecoration(Context context, float spacing) {
            this.spacing = dp(context, spacing);
        }

        @Override
        public void getItemOffsets(
                @NonNull Rect outRect,
                @NonNull View view,
                @NonNull RecyclerView parent,
                @NonNull RecyclerView.State state
        ) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.bottom = (int) spacing;
        }
    }
}
