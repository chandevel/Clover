package org.floens.chan.core.loader;

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;

@SuppressWarnings("serial")
public class EndOfLineException extends VolleyError {
    public EndOfLineException(NetworkResponse networkResponse) {
        super(networkResponse);
    }

    public EndOfLineException() {
        super();
    }

    @Override
    public String getMessage() {
        return "End of the line";
    }
}
