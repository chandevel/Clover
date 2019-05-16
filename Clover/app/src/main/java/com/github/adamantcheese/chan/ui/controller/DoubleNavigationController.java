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

import android.view.ViewGroup;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.ControllerTransition;

public interface DoubleNavigationController {
    void setEmptyView(ViewGroup emptyView);

    void setLeftController(Controller leftController);

    void setRightController(Controller rightController);

    Controller getLeftController();

    Controller getRightController();

    void switchToController(boolean leftController);

    boolean pushController(Controller to);

    boolean pushController(Controller to, boolean animated);

    boolean pushController(Controller to, ControllerTransition controllerTransition);

    boolean popController();

    boolean popController(boolean animated);

    boolean popController(ControllerTransition controllerTransition);
}
