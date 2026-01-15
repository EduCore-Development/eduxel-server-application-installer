package dev.educore.commands;

import dev.educore.config.AppConfig;
import dev.educore.config.ConfigManager;
import dev.educore.core.ConsoleUI;
import dev.educore.core.Root;
import dev.educore.core.os.PackageManager;
import dev.educore.core.os.PackageManagers;
import dev.educore.db.MariaDbManager;
import dev.educore.dns.DnsChecker;
import dev.educore.net.PublicIpResolver;
import dev.educore.service.SystemdManager;
import dev.educore.web.CaddyManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(name = "install", description = "Installiert Eduxel + optional Webstack (Caddy/Apache) + optional Web-Deploy.")
public class InstallCommand implements Runnable {

    @Option(names = {"--wizard"}, description = "Interaktiver Installations-Wizard")
    boolean wizard;

    @Option(names = {"--app-port"}, description = "Port für Eduxel Credential Server", defaultValue = "45821")
    int appPort;

    @Option(names = {"--db-mode"}, description = "auto oder manual", defaultValue = "auto")
    String dbMode;

    @Option(names = {"--domain"}, description = "Domain für Caddy/Apache Setup (z.B. panel.example.com)")
    String domain;

    @Option(names = {"--repo"}, description = "GitHub Repo URL für Website Deploy")
    String repo;

    @Option(names = {"--branch"}, description = "Branch", defaultValue = "main")
    String branch;

    @Option(names = {"--skip-web"}, description = "Webstack (Caddy/Apache/DNS/Deploy) überspringen")
    boolean skipWeb;

    @Option(names = {"--skip-mariadb"}, description = "MariaDB Setup überspringen")
    boolean skipMariaDb;

    @Option(names = {"--allow-no-dns"}, description = "DNS Check nicht hart blockieren (nur warnen)")
    boolean allowNoDns;

    @Override
    public void run() {
        Root.requireRoot();

        ConsoleUI.banner("E D U X E L  •  Installer");

        if (wizard) {
            runWizard();
        }

        PackageManager pm = PackageManagers.detectOrThrow();
        ConsoleUI.ok("Package Manager: " + pm.name());

        pm.install("curl", "openssl", "git");

        String publicIp = PublicIpResolver.resolveBestEffort(pm);
        ConsoleUI.info("Public IP: " + publicIp);

        AppConfig cfg = AppConfig.defaults(appPort);

        if (!skipMariaDb) {
            MariaDbManager db = new MariaDbManager(pm);
            db.ensureInstalledAndRunning();
            if ("auto".equalsIgnoreCase(dbMode)) {
                cfg = db.provisionAuto(cfg, publicIp);
            } else {
                cfg = db.provisionManual(cfg);
            }
        } else {
            ConsoleUI.warn("MariaDB Setup übersprungen.");
        }

        ConfigManager.write(cfg);

        SystemdManager systemd = new SystemdManager(pm);
        systemd.installSelfToOptIfJar();
        systemd.writeAndEnableService();

        if (!skipWeb) {
            if (domain == null || domain.isBlank()) {
                domain = ConsoleUI.ask("Domain (für Website/Caddy): ");
            }

            boolean dnsOk = DnsChecker.matchesPublicIp(domain, publicIp);
            if (!dnsOk) {
                String msg = "DNS passt NICHT: " + domain + " zeigt nicht auf " + publicIp;
                if (allowNoDns) ConsoleUI.warn(msg);
                else throw new IllegalStateException(msg + " (nutze --allow-no-dns zum erzwingen)");
            } else {
                ConsoleUI.ok("DNS Check: OK");
            }

            Path webRoot = Path.of("/var/www", domain);
            new dev.educore.web.LandingPageWriter().write(webRoot, cfg, domain, publicIp);
            ConsoleUI.ok("Landingpage erstellt: " + webRoot.resolve("index.html"));

            CaddyManager caddy = new CaddyManager(pm);
            caddy.ensureInstalled();
            caddy.ensureStaticSite(domain, webRoot);
            caddy.reload();
        } else {
            ConsoleUI.warn("Webstack übersprungen (--skip-web).");
        }

        printSummary(cfg, publicIp);
    }

    private void printSummary(AppConfig cfg, String publicIp) {
        ConsoleUI.banner("✓ Setup fertig");

        ConsoleUI.info("Server-IP: " + publicIp);
        ConsoleUI.info("Config: /etc/eduxel/config.json");
        ConsoleUI.info("Jar: /opt/eduxel/eduxel.jar");
        ConsoleUI.info("CLI: eduxel info");
        ConsoleUI.info("Service: systemctl status eduxel --no-pager");
        ConsoleUI.info("Logs: eduxel logs --tail");

        ConsoleUI.banner("APP");
        ConsoleUI.info("Port: " + cfg.app().port());
        ConsoleUI.info("Secret: " + cfg.app().secret());

        ConsoleUI.banner("DATABASE");
        ConsoleUI.info("Mode: " + cfg.mode());
        ConsoleUI.info("Host: " + cfg.database().host());
        ConsoleUI.info("Port: " + cfg.database().port());
        ConsoleUI.info("Database: " + cfg.database().database());
        ConsoleUI.info("User: " + cfg.database().user());
        ConsoleUI.info("Password: " + cfg.database().password());
    }

    private void runWizard() {
        ConsoleUI.banner("Wizard");

        appPort = askInt("App Port", appPort);

        String db = askWithDefault("DB Mode (auto/manual)", dbMode);
        if (!db.isBlank()) dbMode = db.trim();

        boolean setupDb = askYesNo("MariaDB Setup aktivieren?", !skipMariaDb);
        skipMariaDb = !setupDb;

        boolean setupWeb = askYesNo("Webstack aktivieren (Caddy)?", !skipWeb);
        skipWeb = !setupWeb;

        if (!skipWeb) {
            String d = askWithDefault("Domain", domain == null ? "" : domain);
            if (!d.isBlank()) domain = d.trim();

            String r = askWithDefault("Website Repo (optional)", repo == null ? "" : repo);
            repo = r.isBlank() ? null : r.trim();

            String b = askWithDefault("Branch", branch);
            if (!b.isBlank()) branch = b.trim();

            allowNoDns = askYesNo("DNS Mismatch erlauben?", allowNoDns);
        }
    }

    private int askInt(String prompt, int defaultValue) {
        String raw = askWithDefault(prompt, String.valueOf(defaultValue));
        if (raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            ConsoleUI.warn("Ungültige Zahl, nutze Default: " + defaultValue);
            return defaultValue;
        }
    }

    private String askWithDefault(String prompt, String defaultValue) {
        String suffix = defaultValue == null || defaultValue.isBlank() ? "" : " [" + defaultValue + "]";
        String answer = ConsoleUI.ask(prompt + suffix + ": ");
        return answer == null || answer.isBlank() ? (defaultValue == null ? "" : defaultValue) : answer;
    }

    private boolean askYesNo(String prompt, boolean defaultYes) {
        String hint = defaultYes ? "[J/n]" : "[j/N]";
        String answer = ConsoleUI.ask(prompt + " " + hint + ": ");
        if (answer == null || answer.isBlank()) return defaultYes;
        String a = answer.trim().toLowerCase();
        return a.equals("j") || a.equals("ja") || a.equals("y") || a.equals("yes");
    }

}
