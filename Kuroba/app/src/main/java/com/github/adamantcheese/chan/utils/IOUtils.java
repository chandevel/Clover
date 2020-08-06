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
package com.github.adamantcheese.chan.utils;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

public class IOUtils {
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    public static String assetAsString(Context context, String assetName) {
        String res = null;
        try {
            res = readString(context.getResources().getAssets().open(assetName));
        } catch (IOException ignored) {
        }
        return res;
    }

    public static String readString(InputStream is) {
        try (Reader sr = new InputStreamReader(is); Writer sw = new StringWriter()) {
            copy(sr, sw);
            return sw.toString();
        } catch (IOException e) {
            Logger.e("IOUtils", "readString failed: ", e);
            return "";
        }
    }

    public static void copy(InputStream is, OutputStream os)
            throws IOException {
        int read;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
    }

    public static boolean copy(InputStream is, OutputStream os, long maxBytes)
            throws IOException {
        long total = 0;
        int read;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
            total += read;
            if (total >= maxBytes) {
                return false;
            }
        }
        return true;
    }

    public static void copy(Reader input, Writer output)
            throws IOException {
        char[] buffer = new char[DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
