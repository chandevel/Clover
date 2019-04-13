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
package org.floens.chan.ui.toolbar;

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

    private Callback callback;

    private NavigationItem item;
    private NavigationItem search;
    private NavigationItem transition;

    void create(Callback callback) {
        this.callback = callback;
    }

    void set(NavigationItem newItem, AnimationStyle animation) {
        cancelTransitionIfNeeded();
        if (closeSearchIfNeeded()) {
            animation = AnimationStyle.FADE;
        }

        item = newItem;

        callback.showForNavigationItem(item, animation);
    }

    void update(NavigationItem updatedItem) {
        callback.updateViewForItem(updatedItem, updatedItem == item);
    }

    void startTransition(NavigationItem newItem, TransitionAnimationStyle animation) {
        cancelTransitionIfNeeded();
        if (closeSearchIfNeeded()) {
            callback.showForNavigationItem(item, AnimationStyle.NONE);
        }

        transition = newItem;

        callback.containerStartTransition(transition, animation);
    }

    void stopTransition(boolean didComplete) {
        if (transition == null) {
            return;
        }

        callback.containerStopTransition(didComplete);

        if (didComplete) {
            item = transition;
            callback.showForNavigationItem(item, AnimationStyle.NONE);
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
        if (search != null) {
            return;
        }

        cancelTransitionIfNeeded();

        search = new NavigationItem();
        search.search = true;
        callback.showForNavigationItem(search, AnimationStyle.FADE);

        callback.onSearchVisibilityChanged(item, true);
    }

    boolean closeSearch() {
        if (search == null) {
            return false;
        }

        search = null;
        set(item, AnimationStyle.FADE);

        callback.onSearchVisibilityChanged(item, false);

        return true;
    }

    private void cancelTransitionIfNeeded() {
        if (transition != null) {
            callback.containerStopTransition(false);
            transition = null;
        }
    }

    private boolean closeSearchIfNeeded() {
        // Cancel search
        if (search != null) {
            search = null;
            callback.onSearchVisibilityChanged(item, false);
            return true;
        }
        return false;
    }

    void searchInput(String input) {
        if (search == null) {
            return;
        }

        search.searchText = input;
        callback.onSearchInput(item, search.searchText);
    }

    interface Callback {
        void showForNavigationItem(NavigationItem item, AnimationStyle animation);

        void containerStartTransition(NavigationItem item, TransitionAnimationStyle animation);

        void containerStopTransition(boolean didComplete);

        void containerSetTransitionProgress(float progress);

        void onSearchVisibilityChanged(NavigationItem item, boolean visible);

        void onSearchInput(NavigationItem item, String input);

        void updateViewForItem(NavigationItem item, boolean current);
    }
}
