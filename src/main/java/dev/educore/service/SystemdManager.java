package dev.educore.service;

import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;
import dev.educore.core.os.PackageManager;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SystemdManager {

    private final PackageManager pm;

    public SystemdManager(PackageManager pm) {
        this.pm = pm;
    }

    public void installSelfToOptIfJar() {
        try {
            Path src = Path.of(SystemdManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!src.toString().endsWith(".jar")) {
                ConsoleUI.warn("Läuft nicht aus einer .jar (dev mode). Build zuerst: mvn package");
                return;
            }
            Path dir = Path.of("/opt/eduxel");
            Path dst = dir.resolve("eduxel.jar");
            Files.createDirectories(dir);
            Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ConsoleUI.ok("Jar nach /opt/eduxel kopiert.");
            writeWrapper();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Self-install fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private void writeWrapper() {
        Path bin = Path.of("/usr/local/bin/eduxel");
        String content = """
                #!/usr/bin/env bash
                set -euo pipefail
                exec /usr/bin/java --enable-native-access=ALL-UNNAMED -jar /opt/eduxel/eduxel.jar "$@"
                """;
        try {
            Files.writeString(bin, content);
            CommandRunner.must(List.of("sh", "-lc", "chmod +x /usr/local/bin/eduxel"));
            ConsoleUI.ok("Wrapper installiert: /usr/local/bin/eduxel");
        } catch (Exception e) {
            throw new IllegalStateException("Wrapper schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public void writeAndEnableService() {
        Path svc = Path.of("/etc/systemd/system/eduxel.service");
        String unit = """
                [Unit]
                Description=Eduxel Credential Server
                After=network.target

                [Service]
                ExecStart=/usr/bin/java --enable-native-access=ALL-UNNAMED -jar /opt/eduxel/eduxel.jar serve
                Restart=always
                RestartSec=2
                StandardOutput=append:/var/log/eduxel/eduxel.log
                StandardError=append:/var/log/eduxel/eduxel.log

                [Install]
                WantedBy=multi-user.target
                """;
        try {
            ensureLogDir();
            Files.writeString(svc, unit);
            CommandRunner.run(List.of("sh", "-lc", "systemctl daemon-reload >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "systemctl enable eduxel >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "systemctl restart eduxel >/dev/null 2>&1 || systemctl start eduxel >/dev/null 2>&1 || true"));
            writeLogrotateConfig();
            ConsoleUI.ok("Service aktiv: eduxel");
        } catch (Exception e) {
            throw new IllegalStateException("Service schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public void stopDisableRemoveService() {
        CommandRunner.run(List.of("sh", "-lc", "systemctl stop eduxel >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh", "-lc", "systemctl disable eduxel >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh", "-lc", "rm -f /etc/systemd/system/eduxel.service || true"));
        CommandRunner.run(List.of("sh", "-lc", "systemctl daemon-reload >/dev/null 2>&1 || true"));
        ConsoleUI.ok("Service entfernt.");
    }

    public void writeUpdateTimer(String onCalendar) {
        Path svc = Path.of("/etc/systemd/system/eduxel-update.service");
        Path timer = Path.of("/etc/systemd/system/eduxel-update.timer");
        String serviceUnit = """
                [Unit]
                Description=Eduxel Auto Update
                After=network-online.target
                Wants=network-online.target

                [Service]
                Type=oneshot
                ExecStart=/usr/local/bin/eduxel update -y
                """;
        String timerUnit = """
                [Unit]
                Description=Eduxel Auto Update Timer

                [Timer]
                OnCalendar=%s
                Persistent=true

                [Install]
                WantedBy=timers.target
                """.formatted(onCalendar);
        try {
            Files.writeString(svc, serviceUnit);
            Files.writeString(timer, timerUnit);
            CommandRunner.run(List.of("sh", "-lc", "systemctl daemon-reload >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "systemctl enable eduxel-update.timer >/dev/null 2>&1 || true"));
            CommandRunner.run(List.of("sh", "-lc", "systemctl restart eduxel-update.timer >/dev/null 2>&1 || true"));
            ConsoleUI.ok("Update-Timer aktiv: " + onCalendar);
        } catch (Exception e) {
            throw new IllegalStateException("Update-Timer schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public void disableUpdateTimer() {
        CommandRunner.run(List.of("sh", "-lc", "systemctl stop eduxel-update.timer >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh", "-lc", "systemctl disable eduxel-update.timer >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh", "-lc", "rm -f /etc/systemd/system/eduxel-update.timer /etc/systemd/system/eduxel-update.service || true"));
        CommandRunner.run(List.of("sh", "-lc", "systemctl daemon-reload >/dev/null 2>&1 || true"));
        ConsoleUI.ok("Update-Timer entfernt.");
    }

    private void ensureLogDir() {
        try {
            Files.createDirectories(Path.of("/var/log/eduxel"));
        } catch (Exception e) {
            throw new IllegalStateException("Log-Verzeichnis fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private void writeLogrotateConfig() {
        Path cfg = Path.of("/etc/logrotate.d/eduxel");
        String content = """
                /var/log/eduxel/*.log {
                  daily
                  rotate 14
                  compress
                  missingok
                  notifempty
                  copytruncate
                }
                """;
        try {
            Files.writeString(cfg, content);
            ConsoleUI.ok("Logrotate aktiv: " + cfg);
        } catch (Exception e) {
            throw new IllegalStateException("Logrotate schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }
}
