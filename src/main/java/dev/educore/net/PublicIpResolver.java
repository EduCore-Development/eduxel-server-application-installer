package dev.educore.net;

import dev.educore.core.CommandRunner;
import dev.educore.core.os.PackageManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class PublicIpResolver {
    private PublicIpResolver() {}

    public static String resolveBestEffort(PackageManager pm) {
        try {
            HttpClient c = HttpClient.newHttpClient();
            HttpRequest r = HttpRequest.newBuilder(URI.create("https://api.ipify.org")).GET().build();
            String ip = c.send(r, HttpResponse.BodyHandlers.ofString()).body().trim();
            if (!ip.isBlank()) return ip;
        } catch (Exception ignored) {}

        CommandRunner.Result res = CommandRunner.run(java.util.List.of("sh","-lc","hostname -I 2>/dev/null | awk '{print $1}'"));
        String ip2 = res.output().trim();
        if (!ip2.isBlank()) return ip2;

        return "127.0.0.1";
    }
}
