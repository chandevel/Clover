package com.github.adamantcheese.chan.core.net;

import androidx.annotation.NonNull;

import java.net.*;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Dns;

// This class is a modified copy of https://github.com/yschimke/okurl/blob/b24caf077223cf54e2ab26589839e5ba2205c691/src/main/java/com/baulsupp/oksocial/network/DnsSelector.java

public class DnsSelector
        implements Dns {
    public Mode mode;

    public DnsSelector(Mode mode) {
        this.mode = mode;
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname)
            throws UnknownHostException {
        List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
        if (mode == Mode.SYSTEM) {
            return addresses;
        }

        List<InetAddress> resultAddresses = new ArrayList<>();
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address) {
                resultAddresses.add(address);
            }
        }

        return resultAddresses;
    }

    public enum Mode {
        SYSTEM,
        IPV4_ONLY
    }
}