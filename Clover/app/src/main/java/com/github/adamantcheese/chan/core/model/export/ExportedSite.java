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
package com.github.adamantcheese.chan.core.model.export;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ExportedSite {
    @SerializedName("site_id")
    private int siteId;
    @SerializedName("configuration")
    @Nullable
    private String configuration;
    @SerializedName("order")
    private int order;
    @SerializedName("user_settings")
    @Nullable
    private String userSettings;
    @SerializedName("exported_pins")
    private List<ExportedPin> exportedPins;

    public ExportedSite(
            int siteId,
            @NonNull
            String configuration,
            int order,
            @NonNull
            String userSettings,
            List<ExportedPin> exportedPins
    ) {
        this.siteId = siteId;
        this.configuration = configuration;
        this.order = order;
        this.userSettings = userSettings;
        this.exportedPins = exportedPins;
    }

    public int getSiteId() {
        return siteId;
    }

    @Nullable
    public String getConfiguration() {
        return configuration;
    }

    public int getOrder() {
        return order;
    }

    @Nullable
    public String getUserSettings() {
        return userSettings;
    }

    public List<ExportedPin> getExportedPins() {
        return exportedPins;
    }
}
