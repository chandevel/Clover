package org.floens.chan.net;

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
}
