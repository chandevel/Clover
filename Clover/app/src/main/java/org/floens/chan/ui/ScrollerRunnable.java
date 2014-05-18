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
package org.floens.chan.ui;

import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;

public class ScrollerRunnable implements Runnable {
    private static final int SCROLL_DURATION = 300;

    private static final int MOVE_DOWN_POS = 1;
    private static final int MOVE_UP_POS = 2;

    private final ListView mList;

    private int mMode;
    private int mTargetPos;
    private int mLastSeenPos;
    private int mScrollDuration;
    private final int mExtraScroll;

    public ScrollerRunnable(ListView listView) {
        mList = listView;
        mExtraScroll = ViewConfiguration.get(mList.getContext()).getScaledFadingEdgeLength();
    }

    public void start(int position) {
        stop();

        final int firstPos = mList.getFirstVisiblePosition();
        final int lastPos = firstPos + mList.getChildCount() - 1;

        int viewTravelCount = 0;
        if (position <= firstPos) {
            viewTravelCount = firstPos - position + 1;
            mMode = MOVE_UP_POS;
        } else if (position >= lastPos) {
            viewTravelCount = position - lastPos + 1;
            mMode = MOVE_DOWN_POS;
        } else {
            // Already on screen, nothing to do
            return;
        }

        if (viewTravelCount > 0) {
            mScrollDuration = SCROLL_DURATION / viewTravelCount;
        } else {
            mScrollDuration = SCROLL_DURATION;
        }
        mTargetPos = position;
        mLastSeenPos = ListView.INVALID_POSITION;

        mList.post(this);
    }

    void stop() {
        mList.removeCallbacks(this);
    }

    @Override
    public void run() {
        final int listHeight = mList.getHeight();
        final int firstPos = mList.getFirstVisiblePosition();

        switch (mMode) {
            case MOVE_DOWN_POS: {
                final int lastViewIndex = mList.getChildCount() - 1;
                final int lastPos = firstPos + lastViewIndex;

                if (lastViewIndex < 0) {
                    return;
                }

                if (lastPos == mLastSeenPos) {
                    // No new views, let things keep going.
                    mList.post(this);
                    return;
                }

                final View lastView = mList.getChildAt(lastViewIndex);
                final int lastViewHeight = lastView.getHeight();
                final int lastViewTop = lastView.getTop();
                final int lastViewPixelsShowing = listHeight - lastViewTop;
                final int extraScroll = lastPos < mList.getCount() - 1 ? mExtraScroll : mList.getPaddingBottom();

                mList.smoothScrollBy(lastViewHeight - lastViewPixelsShowing + extraScroll, mScrollDuration);

                mLastSeenPos = lastPos;
                if (lastPos < mTargetPos) {
                    mList.post(this);
                }
                break;
            }

            case MOVE_UP_POS: {
                if (firstPos == mLastSeenPos) {
                    // No new views, let things keep going.
                    mList.post(this);
                    return;
                }

                final View firstView = mList.getChildAt(0);
                if (firstView == null) {
                    return;
                }
                final int firstViewTop = firstView.getTop();
                final int extraScroll = firstPos > 0 ? mExtraScroll : mList.getPaddingTop();

                mList.smoothScrollBy(firstViewTop - extraScroll, mScrollDuration);

                mLastSeenPos = firstPos;

                if (firstPos > mTargetPos) {
                    mList.post(this);
                }
                break;
            }

            default:
                break;
        }
    }
}
