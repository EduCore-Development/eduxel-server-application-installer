package dev.educore.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.educore.config.AppConfig;
import dev.educore.config.ConfigManager;
import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;
import dev.educore.core.Root;
import dev.educore.core.os.PackageManager;
import dev.educore.core.os.PackageManagers;
import dev.educore.net.PublicIpResolver;
import dev.educore.service.SystemdManager;
import dev.educore.update.UpdateChecker;
import dev.educore.update.VersionProvider;
import dev.educore.web.LandingPageWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Command(name = "update", description = "Prueft und fuehrt System-Updates durch (JAR, Landing Page, Config, etc.)")
public class UpdateCommand implements Runnable {

    private static final ObjectMapper M = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Option(names = {"--check-only"}, description = "Nur pruefen, keine Aenderungen durchfuehren")
    boolean checkOnly;

    @Option(names = {"--yes", "-y"}, description = "Alle Aenderungen automatisch akzeptieren")
    boolean autoYes;

    @Option(names = {"--canary"}, description = "Canary-Update mit separatem Port starten")
    boolean canary;

    @Option(names = {"--canary-port"}, description = "Port fuer Canary-Instanz (Standard: App-Port + 1)", defaultValue = "0")
    int canaryPort;

    @Option(names = {"--enable-timer"}, description = "Auto-Update Timer aktivieren")
    boolean enableTimer;

    @Option(names = {"--disable-timer"}, description = "Auto-Update Timer deaktivieren")
    boolean disableTimer;

    @Option(names = {"--timer-calendar"}, description = "systemd OnCalendar Ausdruck fuer Auto-Updates", defaultValue = "daily")
    String timerCalendar;

    @Option(names = {"--show-changelog"}, description = "Changelog anzeigen, auch wenn keine Updates anliegen")
    boolean showChangelog;

    @Override
    public void run() {
        Root.requireRoot();

        ConsoleUI.banner("E D U X E L    Update");

        PackageManager pm = PackageManagers.detectOrThrow();
        SystemdManager systemd = new SystemdManager(pm);

        if (enableTimer) {
            systemd.writeUpdateTimer(timerCalendar);
            return;
        }
        if (disableTimer) {
            systemd.disableUpdateTimer();
            return;
        }
        if (canary) {
            runCanary(pm);
            return;
        }

        UpdateChecker.Result versionCheck = UpdateChecker.check();
        if (versionCheck.status() == UpdateChecker.Status.UPDATE_AVAILABLE) {
            ConsoleUI.warn("Neue Version verfuegbar: " + versionCheck.message());
            ConsoleUI.info("Um die neueste JAR zu installieren, fuehre aus:");
            ConsoleUI.info("  wget <download-url> -O /opt/eduxel/eduxel.jar");
            ConsoleUI.info("  systemctl restart eduxel");
        } else if (versionCheck.status() == UpdateChecker.Status.SKIPPED) {
            ConsoleUI.warn("Update-Check uebersprungen: " + versionCheck.message());
        } else {
            ConsoleUI.ok("Version aktuell: " + UpdateChecker.localVersion());
        }

        if (!Files.exists(ConfigManager.FILE)) {
            ConsoleUI.error("Konfiguration nicht gefunden: " + ConfigManager.FILE);
            ConsoleUI.info("Fuehre 'eduxel install' aus, um das System zu installieren.");
            return;
        }

        AppConfig cfg = ConfigManager.readOrThrow();
        showChangelogIfAvailable(versionCheck);
        showConfigDiff();

        List<ChangeItem> changes = new ArrayList<>();
        changes.addAll(detectLandingPageChanges(cfg, pm));
        changes.addAll(detectServiceChanges(pm));

        if (changes.isEmpty()) {
            ConsoleUI.ok("V Keine Updates erforderlich. System ist aktuell.");
            backupConfig();
            return;
        }

        ConsoleUI.banner("Erkannte Aenderungen");
        for (int i = 0; i < changes.size(); i++) {
            ChangeItem item = changes.get(i);
            ConsoleUI.info((i + 1) + ". " + item.description());
            if (item.details() != null) {
                ConsoleUI.muted("   -> " + item.details());
            }
        }

        if (checkOnly) {
            ConsoleUI.info("\n--check-only aktiviert. Keine Aenderungen durchgefuehrt.");
            return;
        }

        boolean proceed = autoYes;
        if (!autoYes) {
            String answer = ConsoleUI.ask("\nAenderungen anwenden? [j/N]: ");
            proceed = answer.trim().equalsIgnoreCase("j") || answer.trim().equalsIgnoreCase("ja");
        }

        if (!proceed) {
            ConsoleUI.warn("Update abgebrochen.");
            return;
        }

        ConsoleUI.banner("Aenderungen werden angewendet");
        for (ChangeItem item : changes) {
            try {
                ConsoleUI.info("-> " + item.description());
                item.action().run();
                ConsoleUI.ok("  V Erfolgreich");
            } catch (Exception e) {
                ConsoleUI.error("  ? Fehler: " + e.getMessage());
            }
        }

        ConsoleUI.banner("V Update abgeschlossen");
        ConsoleUI.info("Service neustarten: systemctl restart eduxel");
        backupConfig();
    }

    private List<ChangeItem> detectLandingPageChanges(AppConfig cfg, PackageManager pm) {
        List<ChangeItem> changes = new ArrayList<>();

        String domain = findDomain();
        if (domain == null) {
            return changes;
        }

        Path webRoot = Path.of("/var/www", domain);
        Path indexHtml = webRoot.resolve("index.html");

        if (!Files.exists(indexHtml)) {
            changes.add(new ChangeItem(
                    "Landing Page fehlt",
                    "Erstelle Landing Page fuer " + domain,
                    () -> regenerateLandingPage(cfg, pm, domain, webRoot)
            ));
            return changes;
        }

        try {
            String currentContent = Files.readString(indexHtml);
            String currentVersion = UpdateChecker.localVersion();

            if (!currentContent.contains(currentVersion)) {
                changes.add(new ChangeItem(
                        "Landing Page veraltet",
                        "Aktualisiere Landing Page auf Version " + currentVersion,
                        () -> regenerateLandingPage(cfg, pm, domain, webRoot)
                ));
            }
        } catch (Exception e) {
            changes.add(new ChangeItem(
                    "Landing Page pruefen fehlgeschlagen",
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

        try {
            String content = Files.readString(serviceFile);
            boolean needsLog = !content.contains("StandardOutput=append:/var/log/eduxel/eduxel.log");
            boolean missingRotate = !Files.exists(Path.of("/etc/logrotate.d/eduxel"));
            if (needsLog || missingRotate) {
                changes.add(new ChangeItem(
                        "Service/Logging veraltet",
                        "Aktualisiere Service + Logrotate",
                        () -> {
                            SystemdManager systemd = new SystemdManager(pm);
                            systemd.writeAndEnableService();
                        }
                ));
            }
        } catch (Exception e) {
            changes.add(new ChangeItem(
                    "Service pruefen fehlgeschlagen",
                    "Service erneut schreiben (Fehler beim Lesen: " + e.getMessage() + ")",
                    () -> {
                        SystemdManager systemd = new SystemdManager(pm);
                        systemd.writeAndEnableService();
                    }
            ));
        }

        return changes;
    }

    private String findDomain() {
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
                return null;
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

    private void showChangelogIfAvailable(UpdateChecker.Result versionCheck) {
        if (!showChangelog && versionCheck.status() != UpdateChecker.Status.UPDATE_AVAILABLE) return;
        ReleaseInfo release = fetchLatestRelease();
        if (release == null || release.body() == null || release.body().isBlank()) return;
        ConsoleUI.banner("Changelog");
        System.out.println(release.body().trim());
    }

    private void showConfigDiff() {
        Path backup = ConfigManager.DIR.resolve("config.json.bak");
        if (!Files.exists(backup)) {
            ConsoleUI.muted("Config-Diff uebersprungen (kein Backup)");
            return;
        }
        if (CommandRunner.exists("diff")) {
            String cmd = "diff -u " + backup + " " + ConfigManager.FILE + " || true";
            CommandRunner.Result r = CommandRunner.run(List.of("sh", "-lc", cmd));
            if (r.output().trim().isEmpty()) {
                ConsoleUI.ok("Config unveraendert seit letztem Update");
            } else {
                ConsoleUI.banner("Config Diff");
                System.out.println(r.output().trim());
            }
            return;
        }
        ConsoleUI.muted("Config-Diff uebersprungen (diff fehlt)");
    }

    private void backupConfig() {
        Path backup = ConfigManager.DIR.resolve("config.json.bak");
        try {
            Files.copy(ConfigManager.FILE, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            ConsoleUI.warn("Config-Backup fehlgeschlagen: " + e.getMessage());
        }
    }

    private void runCanary(PackageManager pm) {
        ConsoleUI.banner("Canary Update");

        if (!Files.exists(ConfigManager.FILE)) {
            ConsoleUI.error("Konfiguration nicht gefunden: " + ConfigManager.FILE);
            return;
        }

        AppConfig cfg = ConfigManager.readOrThrow();
        int port = canaryPort > 0 ? canaryPort : (cfg.app().port() + 1);

        ReleaseInfo release = fetchLatestRelease();
        if (release == null || release.jarUrl() == null || release.jarUrl().isBlank()) {
            ConsoleUI.error("Kein Canary-Asset gefunden.");
            return;
        }

        Path jarPath = Path.of("/opt/eduxel/eduxel-canary.jar");
        Path cfgPath = ConfigManager.DIR.resolve("config-canary.json");
        try {
            Files.createDirectories(jarPath.getParent());
            Files.createDirectories(ConfigManager.DIR);
            downloadToFile(release.jarUrl(), jarPath);
            writeCanaryConfig(cfg, port, cfgPath);
            writeCanaryService(jarPath, cfgPath);
            enableService("eduxel-canary");
            ConsoleUI.ok("Canary aktiv auf Port " + port);
        } catch (Exception e) {
            ConsoleUI.error("Canary fehlgeschlagen: " + e.getMessage());
        }
    }

    private void writeCanaryConfig(AppConfig cfg, int port, Path cfgPath) throws Exception {
        AppConfig canary = new AppConfig(cfg.mode(), cfg.database(), new AppConfig.App(port, cfg.app().secret()));
        M.writeValue(cfgPath.toFile(), canary);
        ConsoleUI.ok("Canary Config: " + cfgPath);
    }

    private void writeCanaryService(Path jarPath, Path cfgPath) throws Exception {
        Files.createDirectories(Path.of("/var/log/eduxel"));
        Path svc = Path.of("/etc/systemd/system/eduxel-canary.service");
        String unit = """
                [Unit]
                Description=Eduxel Canary
                After=network.target

                [Service]
                ExecStart=/usr/bin/java --enable-native-access=ALL-UNNAMED -jar %s serve --config %s
                Restart=always
                RestartSec=2
                StandardOutput=append:/var/log/eduxel/eduxel-canary.log
                StandardError=append:/var/log/eduxel/eduxel-canary.log

                [Install]
                WantedBy=multi-user.target
                """.formatted(jarPath, cfgPath);
        Files.writeString(svc, unit);
        CommandRunner.run(List.of("sh", "-lc", "systemctl daemon-reload >/dev/null 2>&1 || true"));
        ConsoleUI.ok("Canary Service geschrieben: " + svc);
    }

    private void enableService(String name) {
        CommandRunner.run(List.of("sh", "-lc", "systemctl enable " + name + " >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh", "-lc", "systemctl restart " + name + " >/dev/null 2>&1 || systemctl start " + name + " >/dev/null 2>&1 || true"));
    }

    private ReleaseInfo fetchLatestRelease() {
        Properties p = VersionProvider.props();
        String repo = normalizeRepo(p.getProperty("update.githubRepo", "").trim());
        if (repo.isBlank()) return null;
        String api = "https://api.github.com/repos/" + repo + "/releases/latest";
        try {
            String json = fetch(api);
            JsonNode root = M.readTree(json);
            String tag = root.path("tag_name").asText("");
            String body = root.path("body").asText("");
            String jarAsset = p.getProperty("update.jarAsset", "eduxel.jar").trim();
            String jarUrl = null;
            for (JsonNode asset : root.path("assets")) {
                if (jarAsset.equals(asset.path("name").asText(""))) {
                    jarUrl = asset.path("browser_download_url").asText(null);
                    break;
                }
            }
            return new ReleaseInfo(tag, body, jarUrl, jarAsset);
        } catch (Exception e) {
            ConsoleUI.warn("Release Info nicht verfuegbar: " + e.getMessage());
            return null;
        }
    }

    private String fetch(String url) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "eduxel-updater")
                .GET().build();
        return c.send(r, HttpResponse.BodyHandlers.ofString()).body();
    }

    private void downloadToFile(String url, Path target) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "eduxel-updater")
                .GET().build();
        HttpResponse<Path> resp = c.send(r, HttpResponse.BodyHandlers.ofFile(target));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Download fehlgeschlagen: " + resp.statusCode());
        }
    }

    private String normalizeRepo(String repo) {
        if (repo == null) return "";
        repo = repo.trim();
        if (repo.isBlank()) return "";
        if (repo.startsWith("http")) {
            int idx = repo.indexOf("github.com/");
            if (idx >= 0) {
                String tail = repo.substring(idx + "github.com/".length());
                if (tail.endsWith(".git")) tail = tail.substring(0, tail.length() - 4);
                String[] parts = tail.split("/");
                if (parts.length >= 2) return parts[0] + "/" + parts[1];
            }
            return "";
        }
        return repo;
    }

    private record ReleaseInfo(String tag, String body, String jarUrl, String jarAsset) {}
    private record ChangeItem(String description, String details, Runnable action) {}
}