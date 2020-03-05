package com.github.adamantcheese.chan.core.net;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Dns;

public class DnsSelector implements Dns {
    private Mode mode;

    public DnsSelector(Mode mode) {
        this.mode = mode;
    }

    @NonNull
    @Override public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
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