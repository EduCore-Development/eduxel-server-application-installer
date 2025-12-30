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
        if (CommandRunner.exists("caddy")) {
            ConsoleUI.ok("Caddy ist da.");
            return;
        }

        ConsoleUI.info("Installiere Caddy (best effort)...");
        try {
            pm.install("caddy");
        } catch (Exception e) {
            ConsoleUI.warn("Caddy install via PM fehlgeschlagen. Du musst evtl. das Caddy Repo hinzufügen.");
            throw e;
        }

        CommandRunner.run(List.of("sh","-lc","systemctl enable caddy >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh","-lc","systemctl restart caddy >/dev/null 2>&1 || true"));
    }

    public void ensureSite(String domain, String upstreamHost, int upstreamPort) {
        Path file = Path.of("/etc/caddy/Caddyfile");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "");
            }
            String content = Files.readString(file);

            String blockId = "# EDUXEL_SITE " + domain;
            if (content.contains(blockId)) {
                ConsoleUI.ok("Caddyfile Eintrag existiert schon: " + domain);
                return;
            }

            String block = """
                    
                    %s
                    %s {
                      encode zstd gzip
                      reverse_proxy %s:%d
                    }
                    """.formatted(blockId, domain, upstreamHost, upstreamPort);

            Files.writeString(file, content + block);
            ConsoleUI.ok("Caddyfile Eintrag hinzugefügt: " + domain + " -> " + upstreamHost + ":" + upstreamPort);
        } catch (Exception e) {
            throw new IllegalStateException("Caddyfile schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public void reload() {
        CommandRunner.run(List.of("sh","-lc","systemctl reload caddy >/dev/null 2>&1 || systemctl restart caddy >/dev/null 2>&1 || true"));
        ConsoleUI.ok("Caddy reload.");
    }
}
