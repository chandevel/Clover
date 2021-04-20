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
package com.github.adamantcheese.chan.core.model.orm;

import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.settings.primitives.JsonSettings;
import com.github.adamantcheese.chan.utils.Logger;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "site")
public class SiteModel {
    @DatabaseField(generatedId = true, allowGeneratedIdInsert = true)
    public int id;

    @DatabaseField
    public String configuration;

    @DatabaseField
    public String userSettings;

    @DatabaseField
    public int order;

    @DatabaseField
    public int classID;

    public SiteModel(int id, String configuration, String userSettings, int order, int classID) {
        this.id = id;
        this.configuration = configuration;
        this.userSettings = userSettings;
        this.order = order;
        this.classID = classID;
    }

    public SiteModel() {}

    public void storeUserSettings(JsonSettings userSettings) {
        this.userSettings = AppModule.gson.toJson(userSettings);
        Logger.test("userSettings = " + this.userSettings);
    }

    public JsonSettings loadConfig() {
        JsonSettings settings = AppModule.gson.fromJson(this.userSettings, JsonSettings.class);
        Logger.d(this, "Config: " + configuration + ", Settings: " + userSettings);

        return settings;
    }
}
