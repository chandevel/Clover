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
package com.github.adamantcheese.chan.core.site.parser;

import android.util.JsonReader;

/**
 * Specifies methods to load threads or catalogs, as well as read posts from JSON.
 */
public interface ChanReader {
    PostParser getParser();

    void loadThread(JsonReader reader, ChanReaderProcessingQueue queue)
            throws Exception;

    void loadCatalog(JsonReader reader, ChanReaderProcessingQueue queue)
            throws Exception;

    void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue)
            throws Exception;
}
