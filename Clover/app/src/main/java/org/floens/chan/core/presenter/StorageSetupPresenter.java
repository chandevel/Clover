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
package org.floens.chan.core.presenter;

import android.app.Activity;
import android.content.Intent;

import org.floens.chan.core.storage.Storage;
import org.floens.chan.ui.activity.ActivityResultHelper;

import javax.inject.Inject;

public class StorageSetupPresenter {
    private Callback callback;

    private Storage storage;
    private ActivityResultHelper results;

    @Inject
    public StorageSetupPresenter(Storage storage, ActivityResultHelper results) {
        this.storage = storage;
        this.results = results;
    }

    public void create(Callback callback) {
        this.callback = callback;

        updateDescription();
    }

    public void saveLocationClicked() {
        Intent openTreeIntent = storage.getOpenTreeIntent();
        results.getResultFromIntent(openTreeIntent, (resultCode, result) -> {
            if (resultCode == Activity.RESULT_OK) {
                storage.handleOpenTreeIntent(result);
            }
        });
    }

    private void updateDescription() {
        String description = storage.currentStorageName();
        callback.setSaveLocationDescription(description);
    }

    public interface Callback {
        void setSaveLocationDescription(String description);
    }
}
