package dev.educore.web;

import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;
import dev.educore.core.os.PackageManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CaddyManager {

    private final PackageManager pm;

    public CaddyManager(PackageManager pm) {
        this.pm = pm;
    }

    public void ensureInstalled() {
        if (hasCaddy()) return;

        ConsoleUI.info("Installiere Caddy (best effort)...");
        try {
            pm.install("caddy");
        } catch (Exception ignored) {
        }

        if (!hasCaddy() && "apt".equalsIgnoreCase(pm.name())) {
            CommandRunner.run(List.of("sh", "-lc", "apt-get update -y >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "DEBIAN_FRONTEND=noninteractive apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl gnupg >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt | tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "apt-get update -y >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "DEBIAN_FRONTEND=noninteractive apt-get install -y caddy >/dev/null 2>&1 || true"));
        }

        if (!hasCaddy()) throw new IllegalStateException("Caddy konnte nicht installiert werden.");

        CommandRunner.run(List.of("sh", "-lc", "systemctl enable caddy >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh", "-lc", "systemctl start caddy >/dev/null 2>&1 || true"));
    }

    public void ensureStaticSite(String domain, Path webRoot) {
        try {
            Files.createDirectories(webRoot);
        } catch (Exception e) {
            throw new IllegalStateException("Webroot konnte nicht erstellt werden: " + webRoot, e);
        }

        String start = "# --- EDUXEL " + domain + " START";
        String end = "# --- EDUXEL " + domain + " END";

        String block = start + "\n" +
                domain + " {\n" +
                "  root * " + webRoot + "\n" +
                "  encode zstd gzip\n" +
                "  try_files {path} /index.html\n" +
                "  file_server\n" +
                "}\n" +
                end + "\n";

        Path caddyfile = Path.of("/etc/caddy/Caddyfile");

        try {
            String current = Files.exists(caddyfile) ? Files.readString(caddyfile) : "";
            String updated = removeBlock(current, start, end);

            if (!updated.endsWith("\n") && !updated.isBlank()) updated += "\n";
            updated += block;

            Files.writeString(caddyfile, updated);
            ConsoleUI.ok("Caddyfile Eintrag hinzugefügt: " + domain + " -> " + webRoot);
        } catch (Exception e) {
            throw new IllegalStateException("Caddyfile konnte nicht geschrieben werden: " + e.getMessage(), e);
        }
    }

    public void reload() {
        CommandRunner.run(List.of("sh", "-lc", "systemctl reload caddy >/dev/null 2>&1 || systemctl restart caddy >/dev/null 2>&1 || true"));
        ConsoleUI.ok("Caddy reload/restart ausgeführt.");
    }

    private boolean hasCaddy() {
        return CommandRunner.exists("caddy");
    }

    private String removeBlock(String src, String start, String end) {
        int s = src.indexOf(start);
        if (s < 0) return src;
        int e = src.indexOf(end, s);
        if (e < 0) return src;
        e = e + end.length();
        String left = src.substring(0, s);
        String right = src.substring(e);
        return (left + right).trim() + "\n";
    }
}
