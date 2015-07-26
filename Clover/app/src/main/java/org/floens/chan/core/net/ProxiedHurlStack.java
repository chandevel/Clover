package org.floens.chan.core.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import com.android.volley.toolbox.HurlStack;
import org.floens.chan.core.settings.ChanSettings;

public class ProxiedHurlStack extends HurlStack {

    public ProxiedHurlStack(String userAgent) {
        super(userAgent, null);
    }

    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
        // Start the connection by specifying a proxy server
        if (ChanSettings.proxyEnabled.get())
        {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(
                    ChanSettings.proxyAddress.get(),
                    ChanSettings.proxyPort.get()));
            return (HttpURLConnection) url.openConnection(proxy);
        }
        else
        {
            return (HttpURLConnection) url.openConnection();
        }
    }
}