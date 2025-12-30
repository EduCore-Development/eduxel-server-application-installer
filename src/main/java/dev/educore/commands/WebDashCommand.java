package dev.educore.commands;

import dev.educore.core.ConsoleUI;
import dev.educore.core.Root;
import dev.educore.web.WebDashDeployer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(name = "webdash", description = "WebDashboard Management")
public class WebDashCommand implements Runnable {

    @Option(names = {"--deploy"}, description = "Deploy WebDashboard", defaultValue = "false")
    boolean deploy;

    @Option(names = {"--domain"}, description = "Domain (für Webroot), z.B. panel.example.com")
    String domain;

    @Option(names = {"--webroot"}, description = "Webroot Pfad, Default: /var/www/<domain>")
    String webroot;

    @Option(names = {"--release-url"}, description = "Override Release URL für WebDash")
    String releaseUrl;

    @Override
    public void run() {
        Root.requireRoot();

        if (!deploy) {
            ConsoleUI.warn("Nutze: eduxel webdash --deploy --domain <domain>");
            return;
        }

        if (domain == null || domain.isBlank()) {
            domain = ConsoleUI.ask("Domain (für WebDash Deploy): ");
        }

        Path root = (webroot != null && !webroot.isBlank())
                ? Path.of(webroot)
                : Path.of("/var/www", domain);

        WebDashDeployer d = new WebDashDeployer();
        d.deploy(root, releaseUrl);

        ConsoleUI.ok("WebDash deployed nach: " + root);
        ConsoleUI.info("Reload Caddy falls nötig: systemctl reload caddy");
    }
}
