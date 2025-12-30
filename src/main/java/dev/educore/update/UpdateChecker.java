package dev.educore.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

public final class UpdateChecker {

    public enum Status { UP_TO_DATE, UPDATE_AVAILABLE, SKIPPED }
    public record Result(Status status, String message) {}

    public static String localVersion() {
        Properties p = VersionProvider.props();
        return p.getProperty("version", "0.0.0");
    }

    public static Result check() {
        Properties p = VersionProvider.props();
        String repo = p.getProperty("update.githubRepo", "").trim();
        String url = p.getProperty("update.versionUrl", "").trim();

        if (repo.isBlank() && url.isBlank()) return new Result(Status.SKIPPED, "Keine Quelle gesetzt (update.githubRepo oder update.versionUrl).");

        String latest = null;

        try {
            if (!url.isBlank()) {
                latest = fetch(url).trim();
            } else {
                String api = "https://api.github.com/repos/" + repo + "/releases/latest";
                String json = fetch(api);
                latest = extractTagName(json);
            }
        } catch (Exception e) {
            return new Result(Status.SKIPPED, "Update-Check Fehler: " + e.getMessage());
        }

        if (latest == null || latest.isBlank()) return new Result(Status.SKIPPED, "Latest version nicht gefunden.");

        String local = localVersion();
        int cmp = SemVer.compare(clean(local), clean(latest));

        if (cmp >= 0) return new Result(Status.UP_TO_DATE, local + " (latest: " + latest + ")");
        return new Result(Status.UPDATE_AVAILABLE, local + " -> " + latest);
    }

    private static String fetch(String url) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "eduxel-updater")
                .GET().build();
        return c.send(r, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String extractTagName(String json) {
        String key = "\"tag_name\"";
        int i = json.indexOf(key);
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        int q1 = json.indexOf('"', c + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static String clean(String v) {
        v = v.trim();
        if (v.startsWith("v")) v = v.substring(1);
        return v;
    }
}
