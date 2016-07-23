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

    public ChanLoaderException() {
    }

    public ChanLoaderException(String message) {
        super(message);
    }

    public ChanLoaderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChanLoaderException(Throwable cause) {
        super(cause);
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
