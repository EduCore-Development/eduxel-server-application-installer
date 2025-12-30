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
        ConsoleUI.info("Logs: journalctl -u eduxel -f");

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
}
