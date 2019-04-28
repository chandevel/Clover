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
package org.floens.chan.core.exception;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.ParseError;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;

import org.floens.chan.R;

import javax.net.ssl.SSLException;

public class ChanLoaderException extends Exception {
    private VolleyError volleyError;

    public ChanLoaderException(VolleyError volleyError) {
        this.volleyError = volleyError;
    }

    public boolean isNotFound() {
        return volleyError instanceof ServerError && isServerErrorNotFound((ServerError) volleyError);
    }

    public int getErrorMessage() {
        int errorMessage;
        if (volleyError.getCause() instanceof SSLException) {
            errorMessage = R.string.thread_load_failed_ssl;
        } else if (volleyError instanceof NetworkError ||
                volleyError instanceof TimeoutError ||
                volleyError instanceof ParseError ||
                volleyError instanceof AuthFailureError) {
            errorMessage = R.string.thread_load_failed_network;
        } else if (volleyError instanceof ServerError) {
            if (isServerErrorNotFound((ServerError) volleyError)) {
                errorMessage = R.string.thread_load_failed_not_found;
            } else {
                errorMessage = R.string.thread_load_failed_server;
            }
        } else {
            errorMessage = R.string.thread_load_failed_parsing;
        }
        return errorMessage;
    }

    private boolean isServerErrorNotFound(ServerError serverError) {
        return serverError.networkResponse != null && serverError.networkResponse.statusCode == 404;
    }
}
