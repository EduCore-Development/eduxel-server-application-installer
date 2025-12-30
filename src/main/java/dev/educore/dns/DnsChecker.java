package dev.educore.dns;

import java.net.InetAddress;

public final class DnsChecker {
    private DnsChecker() {}

    public static boolean matchesPublicIp(String domain, String publicIp) {
        try {
            InetAddress[] all = InetAddress.getAllByName(domain);
            for (InetAddress a : all) {
                if (publicIp.equals(a.getHostAddress())) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
