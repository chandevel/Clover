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
package com.github.adamantcheese.chan.ui.toolbar;

import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.AnimationStyle.FADE;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.AnimationStyle.NONE;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.TransitionAnimationStyle.POP;

public class ToolbarPresenter {
    public enum AnimationStyle {
        NONE,
        PUSH,
        POP,
        FADE
    }

    public enum TransitionAnimationStyle {
        PUSH,
        POP
    }

    private final Callback callback;

    private NavigationItem item;
    private NavigationItem transition;

    public ToolbarPresenter(Callback callback) {
        this.callback = callback;
    }

    void set(NavigationItem newItem, AnimationStyle animation) {
        cancelTransitionIfNeeded();
        if (closeSearchIfNeeded()) {
            animation = FADE;
        }

        item = newItem;

        callback.showForNavigationItem(item, animation);
    }

    void update(NavigationItem updatedItem) {
        callback.updateViewForItem(updatedItem);
    }

    void startTransition(NavigationItem newItem) {
        cancelTransitionIfNeeded();
        if (closeSearchIfNeeded()) {
            callback.showForNavigationItem(item, NONE);
        }

        transition = newItem;

        callback.containerStartTransition(transition, POP);
    }

    void stopTransition(boolean didComplete) {
        if (transition == null) {
            return;
        }

        callback.containerStopTransition(didComplete);

        if (didComplete) {
            item = transition;
            callback.showForNavigationItem(item, NONE);
        }
        transition = null;
    }

    void setTransitionProgress(float progress) {
        if (transition == null) {
            return;
        }

        callback.containerSetTransitionProgress(progress);
    }

    void openSearch() {
        if (isSearchOpen()) return;

        cancelTransitionIfNeeded();

        item.search = true;
        callback.showForNavigationItem(item, NONE);

        callback.onSearchVisibilityChanged(item, true);
    }

    boolean closeSearch() {
        if (!isSearchOpen()) return false;

        item.search = false;
        item.searchText = null;
        set(item, FADE);

        callback.onSearchVisibilityChanged(item, false);

        return true;
    }

    boolean isSearchOpen() {
        return item != null && item.search;
    }

    private void cancelTransitionIfNeeded() {
        if (transition != null) {
            callback.containerStopTransition(false);
            transition = null;
        }
    }

    /**
     * Returns true if search was closed, false otherwise
     */
    public boolean closeSearchIfNeeded() {
        // Cancel search, but don't unmark it as a search item so that onback will automatically pull up the search window
        if (isSearchOpen()) {
            callback.onSearchVisibilityChanged(item, false);
            return true;
        }
        return false;
    }

    void searchInput(String input) {
        if (!isSearchOpen()) {
            return;
        }

        item.searchText = input;
        callback.onSearchInput(item, input);
    }

    interface Callback {
        void showForNavigationItem(NavigationItem item, AnimationStyle animation);

        void containerStartTransition(NavigationItem item, TransitionAnimationStyle animation);

        void containerStopTransition(boolean didComplete);

        void containerSetTransitionProgress(float progress);

        void onSearchVisibilityChanged(NavigationItem item, boolean visible);

        void onSearchInput(NavigationItem item, String input);

        void updateViewForItem(NavigationItem item);
    }
}
