package dev.educore.commands;

import dev.educore.config.AppConfig;
import dev.educore.config.ConfigManager;
import dev.educore.core.ConsoleUI;
import dev.educore.core.Root;
import dev.educore.core.os.PackageManager;
import dev.educore.core.os.PackageManagers;
import dev.educore.net.PublicIpResolver;
import dev.educore.service.SystemdManager;
import dev.educore.update.UpdateChecker;
import dev.educore.web.LandingPageWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Command(name = "update", description = "Prüft und führt System-Updates durch (JAR, Landing Page, Config, etc.)")
public class UpdateCommand implements Runnable {

    @Option(names = {"--check-only"}, description = "Nur prüfen, keine Änderungen durchführen")
    boolean checkOnly;

    @Option(names = {"--yes", "-y"}, description = "Alle Änderungen automatisch akzeptieren")
    boolean autoYes;

    @Override
    public void run() {
        Root.requireRoot();

        ConsoleUI.banner("E D U X E L  •  Update");

        // Version Check
        UpdateChecker.Result versionCheck = UpdateChecker.check();
        if (versionCheck.status() == UpdateChecker.Status.UPDATE_AVAILABLE) {
            ConsoleUI.warn("Neue Version verfügbar: " + versionCheck.message());
            ConsoleUI.info("Um die neueste JAR zu installieren, führe aus:");
            ConsoleUI.info("  wget <download-url> -O /opt/eduxel/eduxel.jar");
            ConsoleUI.info("  systemctl restart eduxel");
        } else {
            ConsoleUI.ok("Version aktuell: " + UpdateChecker.localVersion());
        }

        // Config prüfen
        if (!Files.exists(ConfigManager.FILE)) {
            ConsoleUI.error("Konfiguration nicht gefunden: " + ConfigManager.FILE);
            ConsoleUI.info("Führe 'eduxel install' aus, um das System zu installieren.");
            return;
        }

        AppConfig cfg = ConfigManager.readOrThrow();
        PackageManager pm = PackageManagers.detectOrThrow();

        List<ChangeItem> changes = new ArrayList<>();

        // 1. Landing Page Änderungen erkennen
        changes.addAll(detectLandingPageChanges(cfg, pm));

        // 2. Service-Datei Änderungen
        changes.addAll(detectServiceChanges(pm));

        if (changes.isEmpty()) {
            ConsoleUI.ok("✓ Keine Updates erforderlich. System ist aktuell.");
            return;
        }

        // Änderungen anzeigen
        ConsoleUI.banner("Erkannte Änderungen");
        for (int i = 0; i < changes.size(); i++) {
            ChangeItem item = changes.get(i);
            ConsoleUI.info((i + 1) + ". " + item.description());
            if (item.details() != null) {
                ConsoleUI.muted("   → " + item.details());
            }
        }

        if (checkOnly) {
            ConsoleUI.info("\n--check-only aktiviert. Keine Änderungen durchgeführt.");
            return;
        }

        // Bestätigung
        boolean proceed = autoYes;
        if (!autoYes) {
            String answer = ConsoleUI.ask("\nÄnderungen anwenden? [j/N]: ");
            proceed = answer.trim().equalsIgnoreCase("j") || answer.trim().equalsIgnoreCase("ja");
        }

        if (!proceed) {
            ConsoleUI.warn("Update abgebrochen.");
            return;
        }

        // Änderungen durchführen
        ConsoleUI.banner("Änderungen werden angewendet");
        for (ChangeItem item : changes) {
            try {
                ConsoleUI.info("→ " + item.description());
                item.action().run();
                ConsoleUI.ok("  ✓ Erfolgreich");
            } catch (Exception e) {
                ConsoleUI.error("  ✗ Fehler: " + e.getMessage());
            }
        }

        ConsoleUI.banner("✓ Update abgeschlossen");
        ConsoleUI.info("Service neustarten: systemctl restart eduxel");
    }

    private List<ChangeItem> detectLandingPageChanges(AppConfig cfg, PackageManager pm) {
        List<ChangeItem> changes = new ArrayList<>();

        // Prüfe, ob Landing Page existiert und ob sie neu generiert werden sollte
        String domain = findDomain();
        if (domain == null) {
            return changes; // Keine Domain konfiguriert
        }

        Path webRoot = Path.of("/var/www", domain);
        Path indexHtml = webRoot.resolve("index.html");

        if (!Files.exists(indexHtml)) {
            changes.add(new ChangeItem(
                    "Landing Page fehlt",
                    "Erstelle Landing Page für " + domain,
                    () -> regenerateLandingPage(cfg, pm, domain, webRoot)
            ));
            return changes;
        }

        // Prüfe ob die Landing Page veraltet ist (Hash-basiert oder Timestamp)
        try {
            String currentContent = Files.readString(indexHtml);
            String currentVersion = UpdateChecker.localVersion();

            // Wenn die Version in der HTML nicht übereinstimmt, regenerieren
            if (!currentContent.contains(currentVersion)) {
                changes.add(new ChangeItem(
                        "Landing Page veraltet",
                        "Aktualisiere Landing Page auf Version " + currentVersion,
                        () -> regenerateLandingPage(cfg, pm, domain, webRoot)
                ));
            }
        } catch (Exception e) {
            changes.add(new ChangeItem(
                    "Landing Page prüfen fehlgeschlagen",
                    "Regeneriere Landing Page (Fehler beim Lesen: " + e.getMessage() + ")",
                    () -> regenerateLandingPage(cfg, pm, domain, webRoot)
            ));
        }

        return changes;
    }

    private List<ChangeItem> detectServiceChanges(PackageManager pm) {
        List<ChangeItem> changes = new ArrayList<>();

        Path serviceFile = Path.of("/etc/systemd/system/eduxel.service");
        if (!Files.exists(serviceFile)) {
            changes.add(new ChangeItem(
                    "Systemd Service fehlt",
                    "Erstelle /etc/systemd/system/eduxel.service",
                    () -> {
                        SystemdManager systemd = new SystemdManager(pm);
                        systemd.writeAndEnableService();
                    }
            ));
            return changes;
        }

        // Prüfe ob JAR in /opt/eduxel/ existiert
        Path jarPath = Path.of("/opt/eduxel/eduxel.jar");
        if (!Files.exists(jarPath)) {
            changes.add(new ChangeItem(
                    "JAR fehlt in /opt/eduxel/",
                    "Installiere JAR nach /opt/eduxel/eduxel.jar",
                    () -> {
                        SystemdManager systemd = new SystemdManager(pm);
                        systemd.installSelfToOptIfJar();
                    }
            ));
        }

        return changes;
    }

    private String findDomain() {
        // Versuche Domain aus bestehenden Webroot-Verzeichnissen zu finden
        Path varWww = Path.of("/var/www");
        if (Files.exists(varWww)) {
            try (var stream = Files.list(varWww)) {
                return stream
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> !name.equals("html") && name.contains("."))
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                // Ignorieren
            }
        }
        return null;
    }

    private void regenerateLandingPage(AppConfig cfg, PackageManager pm, String domain, Path webRoot) {
        String publicIp = PublicIpResolver.resolveBestEffort(pm);
        LandingPageWriter writer = new LandingPageWriter();
        writer.write(webRoot, cfg, domain, publicIp);
        ConsoleUI.ok("Landing Page regeneriert: " + webRoot.resolve("index.html"));
    }

    private record ChangeItem(String description, String details, Runnable action) {}
}

