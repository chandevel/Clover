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
package org.floens.chan.controller;

import android.content.Context;
import android.view.KeyEvent;

import org.floens.chan.controller.transition.PopControllerTransition;
import org.floens.chan.controller.transition.PushControllerTransition;
import org.floens.chan.controller.ui.NavigationControllerContainerLayout;

public abstract class NavigationController extends Controller {
    protected NavigationControllerContainerLayout container;

    protected ControllerTransition controllerTransition;
    protected boolean blockingInput = false;

    public NavigationController(Context context) {
        super(context);
    }

    public boolean pushController(final Controller to) {
        return pushController(to, true);
    }

    public boolean pushController(final Controller to, boolean animated) {
        return pushController(to, animated ? new PushControllerTransition() : null);
    }

    public boolean pushController(final Controller to, ControllerTransition controllerTransition) {
        if (blockingInput) return false;

        final Controller from = getTop();

        if (from == null && controllerTransition != null) {
            throw new IllegalArgumentException("Cannot animate push when from is null");
        }

        to.navigationController = this;
        to.previousSiblingController = from;

        transition(from, to, true, controllerTransition);

        return true;
    }

    public boolean popController() {
        return popController(true);
    }

    public boolean popController(boolean animated) {
        return popController(animated ? new PopControllerTransition() : null);
    }

    public boolean popController(ControllerTransition controllerTransition) {
        if (blockingInput) return false;

        final Controller from = getTop();
        final Controller to = childControllers.size() > 1 ? childControllers.get(childControllers.size() - 2) : null;

        transition(from, to, false, controllerTransition);

        return true;
    }

    public boolean isBlockingInput() {
        return blockingInput;
    }

    public boolean beginSwipeTransition(final Controller from, final Controller to) {
        if (blockingInput) return false;

        if (this.controllerTransition != null) {
            throw new IllegalArgumentException("Cannot transition while another transition is in progress.");
        }

        to.attach(container, false);
        to.onShow();

        return true;
    }

    public void swipeTransitionProgress(float progress) {
    }

    public void endSwipeTransition(final Controller from, final Controller to, boolean finish) {
        if (finish) {
            from.onHide();
            from.detach();
            removeChildController(from);
        } else {
            to.onHide();
            to.detach();
        }

        controllerTransition = null;
        blockingInput = false;
    }

    public void transition(final Controller from, final Controller to, final boolean pushing, ControllerTransition controllerTransition) {
        if (this.controllerTransition != null) {
            throw new IllegalArgumentException("Cannot transition while another transition is in progress.");
        }

        if (!pushing && childControllers.size() == 0) {
            throw new IllegalArgumentException("Cannot pop with no controllers left");
        }

        if (pushing && to != null) {
            addChildController(to);
        }

        if (to != null) {
            to.attach(container, pushing);
            to.onShow();
        }

        if (controllerTransition != null) {
            controllerTransition.from = from;
            controllerTransition.to = to;
            blockingInput = true;
            this.controllerTransition = controllerTransition;
            controllerTransition.setCallback(new ControllerTransition.Callback() {
                @Override
                public void onControllerTransitionCompleted(ControllerTransition transition) {
                    finishTransition(from, pushing);
                }
            });
            controllerTransition.perform();
        } else {
            finishTransition(from, pushing);
        }

        if (to != null) {
            if (pushing) {
                controllerPushed(to);
            } else {
                controllerPopped(to);
            }
        }
    }

    private void finishTransition(Controller from, boolean pushing) {
        if (from != null) {
            from.onHide();
            from.detach();
        }

        if (!pushing && from != null) {
            removeChildController(from);
        }

        controllerTransition = null;
        blockingInput = false;
    }

    protected void controllerPushed(Controller controller) {
    }

    protected void controllerPopped(Controller controller) {
    }

    public boolean onBack() {
        if (blockingInput) return true;

        if (childControllers.size() > 0) {
            Controller top = getTop();
            if (top.onBack()) {
                return true;
            } else {
                if (childControllers.size() > 1) {
                    popController();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Controller top = getTop();
        return (top != null && top.dispatchKeyEvent(event));
    }
}
