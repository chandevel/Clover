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

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

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

    public static int[] getIndexAndTop(RecyclerView recyclerView) {
        int index = 0, top = 0;
        if (recyclerView.getLayoutManager().getChildCount() > 0) {
            View topChild = recyclerView.getLayoutManager().getChildAt(0);
            index = ((RecyclerView.LayoutParams) topChild.getLayoutParams()).getViewLayoutPosition();
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) topChild.getLayoutParams();
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.getPaddingTop();
        }
        return new int[]{index, top};
    }

    public static ColorDrawable getDivider(Context context) {
        return new ColorDrawable(getAttrColor(context, R.attr.divider_color)) {
            @Override
            public int getIntrinsicHeight() {
                return dp(context, 1);
            }

            @Override
            public int getIntrinsicWidth() {
                return dp(context, 1);
            }
        };
    }

    // From https://github.com/DhruvamSharma/Recycler-View-Series; comments are there
    public static RecyclerView.ItemDecoration getDividerDecoration(ColorDrawable divider) {
        return new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.onDraw(c, parent, state);
                int paddingPx = dp(parent.getContext(), ChanSettings.fontSize.get() - 6);
                for (int i = 0; i < parent.getChildCount(); i++) {
                    if (i != parent.getChildCount() - 1) {
                        View child = parent.getChildAt(i);
                        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

                        int dividerTop = child.getBottom() + params.bottomMargin;
                        int dividerBottom = dividerTop + dp(1);

                        divider.setBounds(paddingPx, dividerTop, parent.getWidth() - paddingPx, dividerBottom);
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
                if (parent.getChildAdapterPosition(view) == 0) {
                    return;
                }
                outRect.top = dp(1);
            }
        };
    }
}
