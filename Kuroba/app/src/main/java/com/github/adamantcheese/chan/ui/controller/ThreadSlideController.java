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

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.ControllerTransition;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.layout.ThreadSlidingPaneLayout;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.utils.Logger;

import java.lang.reflect.Field;

import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.PHONE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.clearAnySelectionsAndKeyboards;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class ThreadSlideController
        extends Controller
        implements DoubleNavigationController, SlidingPaneLayout.PanelSlideListener,
                   ToolbarNavigationController.ToolbarSearchCallback {
    public Controller leftController;
    public Controller rightController;

    private boolean leftOpen = true;
    private ViewGroup emptyView;
    private ThreadSlidingPaneLayout slidingPaneLayout;

    public ThreadSlideController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        doubleNavigationController = this;

        navigation.swipeable = false;
        navigation.handlesToolbarInset = true;
        navigation.hasDrawer = true;

        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_thread_slide, null);

        slidingPaneLayout = view.findViewById(R.id.sliding_pane_layout);
        slidingPaneLayout.setThreadSlideController(this);
        slidingPaneLayout.setPanelSlideListener(this);
        slidingPaneLayout.openPaneNoAnimation();
        // Emulate the Clover phone layout by removing the side drag bar; effectively works like phone mode, but with
        // more consistency because we're using the same layout as slide mode
        if (ChanSettings.layoutMode.get() == PHONE) {
            try {
                Field overhang = SlidingPaneLayout.class.getDeclaredField("mOverhangSize");
                overhang.setAccessible(true);
                overhang.set(slidingPaneLayout, 0);
                overhang.setAccessible(false);
            } catch (Exception ignored) {
            }
            // In order to "snap", no parallax and no dimming, and remove the shadow because you can't drag anyways
            slidingPaneLayout.setParallaxDistance(0);
            slidingPaneLayout.setShadowDrawableLeft(null);
            slidingPaneLayout.setSliderFadeColor(Color.TRANSPARENT);
        } else {
            //regular slide stuff, with view dimming
            slidingPaneLayout.setParallaxDistance(dp(100));
            slidingPaneLayout.setShadowResourceLeft(R.drawable.panel_shadow);
            int fadeColor = (getAttrColor(context, R.attr.backcolor) & 0xffffff) + 0xCC000000;
            slidingPaneLayout.setSliderFadeColor(fadeColor);
        }

        setLeftController(null);
        setRightController(null);
    }

    public void onSlidingPaneLayoutStateRestored() {
        // SlidingPaneLayout does some annoying things for state restoring and incorrectly
        // tells us if the restored state was open or closed
        // We need to use reflection to get the private field that stores this correct state
        boolean restoredOpen = false;
        try {
            Field field = SlidingPaneLayout.class.getDeclaredField("mPreservedOpenState");
            field.setAccessible(true);
            restoredOpen = field.getBoolean(slidingPaneLayout);
        } catch (Exception e) {
            Logger.e(this, "Error getting restored open state with reflection", e);
        }
        if (restoredOpen != leftOpen) {
            leftOpen = restoredOpen;
            slideStateChanged();
        }
    }

    @Override
    public void onPanelSlide(@NonNull View panel, float slideOffset) {
    }

    @Override
    public void onPanelOpened(@NonNull View panel) {
        if (this.leftOpen != leftOpen()) {
            this.leftOpen = leftOpen();
            slideStateChanged();
        }
    }

    @Override
    public void onPanelClosed(@NonNull View panel) {
        if (this.leftOpen != leftOpen()) {
            this.leftOpen = leftOpen();
            slideStateChanged();
        }
    }

    @Override
    public void switchToController(boolean leftController) {
        if (leftController != leftOpen()) {
            if (ChanSettings.layoutMode.get() == PHONE) {
                if (leftController) {
                    slidingPaneLayout.openPaneNoAnimation();
                    setRightController(null);
                } else {
                    slidingPaneLayout.closePaneNoAnimation();
                }
            } else {
                if (leftController) {
                    slidingPaneLayout.openPane();
                } else {
                    slidingPaneLayout.closePane();
                }
            }
            Toolbar toolbar = ((ToolbarNavigationController) navigationController).toolbar;
            toolbar.processScrollCollapse(Toolbar.TOOLBAR_COLLAPSE_SHOW, true);

            if (slidingPaneLayout.getWidth() == 0) {
                // It won't tell us it switched when it's not laid out yet.
                leftOpen = leftController;
                slideStateChanged();
            }
        }
    }

    @Override
    public void setEmptyView(ViewGroup emptyView) {
        this.emptyView = emptyView;
    }

    public void setLeftController(Controller leftController) {
        if (this.leftController != null) {
            this.leftController.onHide();
            removeChildController(this.leftController);
        }

        this.leftController = leftController;

        if (leftController != null) {
            addChildController(leftController);
            leftController.attachToParentView(slidingPaneLayout.leftPane);
            leftController.onShow();
            if (leftOpen()) {
                setParentNavigationItem(true);
            }
        }
    }

    public void setRightController(Controller rightController) {
        if (this.rightController != null) {
            this.rightController.onHide();
            removeChildController(this.rightController);
        } else {
            this.slidingPaneLayout.rightPane.removeAllViews();
        }

        this.rightController = rightController;

        if (rightController != null) {
            addChildController(rightController);
            rightController.attachToParentView(slidingPaneLayout.rightPane);
            rightController.onShow();
            if (!leftOpen()) {
                setParentNavigationItem(false);
            }
        } else {
            slidingPaneLayout.rightPane.addView(emptyView);
        }
    }

    @Override
    public Controller getLeftController() {
        return leftController;
    }

    @Override
    public Controller getRightController() {
        return rightController;
    }

    @Override
    public boolean pushController(Controller to) {
        return navigationController.pushController(to);
    }

    @Override
    public boolean pushController(Controller to, boolean animated) {
        return navigationController.pushController(to, animated);
    }

    @Override
    public boolean pushController(Controller to, ControllerTransition controllerTransition) {
        return navigationController.pushController(to, controllerTransition);
    }

    @Override
    public boolean popController() {
        return navigationController.popController();
    }

    @Override
    public boolean popController(boolean animated) {
        return navigationController.popController(animated);
    }

    @Override
    public boolean popController(ControllerTransition controllerTransition) {
        return navigationController.popController(controllerTransition);
    }

    @Override
    public boolean isViewingCatalog() {
        return leftOpen(); // catalog is on the left pane
    }

    @Override
    public boolean onBack() {
        if (!leftOpen()) {
            if (rightController != null && rightController.onBack()) {
                return true;
            } else {
                switchToController(true);
                return true;
            }
        } else {
            if (leftController != null && leftController.onBack()) {
                return true;
            }
        }

        return super.onBack();
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (leftOpen() && leftController != null
                && leftController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) leftController).onSearchVisibilityChanged(visible);
        }
        if (!leftOpen() && rightController != null
                && rightController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) rightController).onSearchVisibilityChanged(visible);
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        if (leftOpen() && leftController != null
                && leftController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) leftController).onSearchEntered(entered);
        }
        if (!leftOpen() && rightController != null
                && rightController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) rightController).onSearchEntered(entered);
        }
    }

    @Override
    public void onNavItemSet() {
        if (leftOpen() && leftController != null
                && leftController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) leftController).onNavItemSet();
        }
        if (!leftOpen() && rightController != null
                && rightController instanceof ToolbarNavigationController.ToolbarSearchCallback) {
            ((ToolbarNavigationController.ToolbarSearchCallback) rightController).onNavItemSet();
        }
    }

    private boolean leftOpen() {
        return slidingPaneLayout.isOpen();
    }

    private void slideStateChanged() {
        clearAnySelectionsAndKeyboards(context);
        setParentNavigationItem(leftOpen);
        notifySlideChanged(leftOpen ? leftController : rightController);
    }

    private void notifySlideChanged(Controller controller) {
        if (controller == null) {
            return;
        }

        if (controller instanceof SlideChangeListener) {
            ((SlideChangeListener) controller).onSlideChanged();
        }

        for (Controller childController : controller.childControllers) {
            notifySlideChanged(childController);
        }
    }

    private void setParentNavigationItem(boolean left) {
        Toolbar toolbar = ((ToolbarNavigationController) navigationController).toolbar;

        //default, blank navigation item with no menus or titles, so other layouts don't mess up
        NavigationItem item = new NavigationItem();
        if (left) {
            if (leftController != null) {
                item = leftController.navigation;
            }
        } else {
            if (rightController != null) {
                item = rightController.navigation;
            }
        }

        navigation = item;
        navigation.swipeable = false;
        navigation.handlesToolbarInset = true;
        navigation.hasDrawer = true;
        toolbar.setNavigationItem(true, true, navigation, null);
    }

    public interface SlideChangeListener {
        void onSlideChanged();
    }
}
