package dev.educore.commands;

import dev.educore.config.AppConfig;
import dev.educore.config.ConfigManager;
import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;
import dev.educore.dns.DnsChecker;
import dev.educore.net.PublicIpResolver;
import dev.educore.core.os.PackageManager;
import dev.educore.core.os.PackageManagers;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Command(name = "doctor", description = "Systemdiagnose und Checks")
public class DoctorCommand implements Runnable {
    @Override
    public void run() {
        ConsoleUI.banner("E D U X E L    Doctor");

        boolean hasConfig = Files.exists(ConfigManager.FILE);
        if (!hasConfig) {
            ConsoleUI.error("Config fehlt: " + ConfigManager.FILE);
        } else {
            ConsoleUI.ok("Config gefunden: " + ConfigManager.FILE);
        }

        AppConfig cfg = null;
        if (hasConfig) {
            try {
                cfg = ConfigManager.readOrThrow();
            } catch (Exception e) {
                ConsoleUI.error("Config lesen fehlgeschlagen: " + e.getMessage());
            }
        }

        checkJava();
        checkService("eduxel");
        checkPort(cfg);
        checkDatabaseService();
        checkDns(cfg);
        checkLogs();
        checkUpdateTimer();
    }

    private void checkJava() {
        if (!CommandRunner.exists("java")) {
            ConsoleUI.error("Java fehlt: java");
            return;
        }
        CommandRunner.Result r = CommandRunner.run(java.util.List.of("sh", "-lc", "java -version 2>&1 | head -n 1"));
        if (r.ok()) {
            ConsoleUI.ok("Java: " + r.output().trim());
        } else {
            ConsoleUI.warn("Java Version nicht lesbar: " + r.output().trim());
        }
    }

    private void checkService(String name) {
        if (!CommandRunner.exists("systemctl")) {
            ConsoleUI.warn("systemctl nicht verfuegbar");
            return;
        }
        CommandRunner.Result active = CommandRunner.run(java.util.List.of("sh", "-lc", "systemctl is-active " + name + " 2>/dev/null"));
        if (active.ok() && active.output().trim().equals("active")) {
            ConsoleUI.ok("Service aktiv: " + name);
        } else {
            ConsoleUI.warn("Service nicht aktiv: " + name + " (" + active.output().trim() + ")");
        }
    }

    private void checkPort(AppConfig cfg) {
        if (cfg == null) return;
        int port = cfg.app().port();
        if (CommandRunner.exists("ss")) {
            CommandRunner.Result r = CommandRunner.run(java.util.List.of("sh", "-lc", "ss -ltn | grep -E \":" + port + "\\b\""));
            if (r.ok()) {
                ConsoleUI.ok("Port hoert zu: " + port);
            } else {
                ConsoleUI.warn("Port nicht gefunden: " + port);
            }
            return;
        }
        ConsoleUI.muted("Port-Check uebersprungen (ss fehlt)");
    }

    private void checkDatabaseService() {
        if (!CommandRunner.exists("systemctl")) return;
        CommandRunner.Result active = CommandRunner.run(java.util.List.of("sh", "-lc", "systemctl is-active mariadb 2>/dev/null"));
        if (active.ok() && active.output().trim().equals("active")) {
            ConsoleUI.ok("MariaDB aktiv");
        } else {
            ConsoleUI.warn("MariaDB nicht aktiv (" + active.output().trim() + ")");
        }
    }

    private void checkDns(AppConfig cfg) {
        String domain = findDomain();
        if (domain == null || domain.isBlank()) {
            ConsoleUI.muted("DNS-Check uebersprungen (keine Domain gefunden)");
            return;
        }
        PackageManager pm = PackageManagers.detectOrThrow();
        String publicIp = PublicIpResolver.resolveBestEffort(pm);
        if (DnsChecker.matchesPublicIp(domain, publicIp)) {
            ConsoleUI.ok("DNS OK: " + domain + " -> " + publicIp);
        } else {
            ConsoleUI.warn("DNS Mismatch: " + domain + " != " + publicIp);
        }
    }

    private void checkLogs() {
        Path logFile = Path.of("/var/log/eduxel/eduxel.log");
        if (Files.exists(logFile)) {
            ConsoleUI.ok("Logfile: " + logFile);
        } else {
            ConsoleUI.warn("Logfile fehlt: " + logFile);
        }
        Path rotate = Path.of("/etc/logrotate.d/eduxel");
        if (Files.exists(rotate)) {
            ConsoleUI.ok("Logrotate: " + rotate);
        } else {
            ConsoleUI.warn("Logrotate fehlt: " + rotate);
        }
    }

    private void checkUpdateTimer() {
        if (!CommandRunner.exists("systemctl")) return;
        Path timer = Path.of("/etc/systemd/system/eduxel-update.timer");
        if (!Files.exists(timer)) {
            ConsoleUI.muted("Update-Timer nicht installiert");
            return;
        }
        CommandRunner.Result active = CommandRunner.run(java.util.List.of("sh", "-lc", "systemctl is-active eduxel-update.timer 2>/dev/null"));
        if (active.ok() && active.output().trim().equals("active")) {
            ConsoleUI.ok("Update-Timer aktiv");
        } else {
            ConsoleUI.warn("Update-Timer nicht aktiv (" + active.output().trim() + ")");
        }
    }

    private String findDomain() {
        Path varWww = Path.of("/var/www");
        if (Files.exists(varWww)) {
            try (var stream = Files.list(varWww)) {
                Optional<String> found = stream
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> !name.equals("html") && name.contains("."))
                        .findFirst();
                return found.orElse(null);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}