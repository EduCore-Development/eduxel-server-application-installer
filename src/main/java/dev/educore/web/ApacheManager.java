package dev.educore.web;

import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;
import dev.educore.core.os.PackageManager;

import java.nio.file.Path;
import java.util.List;

public final class ApacheManager {

    private final PackageManager pm;

    public ApacheManager(PackageManager pm) {
        this.pm = pm;
    }

    public void ensureInstalled() {
        if (!CommandRunner.exists("apache2ctl") && !CommandRunner.exists("httpd")) {
            ConsoleUI.info("Installiere Apache...");
            if ("apt".equals(pm.name())) pm.install("apache2");
            else if ("dnf".equals(pm.name()) || "yum".equals(pm.name())) pm.install("httpd");
            else pm.install("apache2");
        }
        CommandRunner.run(List.of("sh","-lc","systemctl enable apache2 >/dev/null 2>&1 || systemctl enable httpd >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh","-lc","systemctl restart apache2 >/dev/null 2>&1 || systemctl restart httpd >/dev/null 2>&1 || true"));
    }

    public void ensureListen8080() {
        if (!"apt".equals(pm.name())) {
            ConsoleUI.warn("Apache Port-Shift (8080) ist primär für Debian/Ubuntu automatisiert.");
            return;
        }
        CommandRunner.must(List.of("sh","-lc",
                "sed -i 's/^Listen 80$/Listen 8080/g' /etc/apache2/ports.conf || true; " +
                        "grep -q '^Listen 8080' /etc/apache2/ports.conf || echo 'Listen 8080' >> /etc/apache2/ports.conf"
        ));
        CommandRunner.must(List.of("sh","-lc","systemctl restart apache2 || true"));
        ConsoleUI.ok("Apache hört auf Port 8080.");
    }

    public void ensureVhost(String domain, Path webRoot) {
        if (!"apt".equals(pm.name())) {
            ConsoleUI.warn("VHost Auto-Setup ist primär für Debian/Ubuntu automatisiert.");
            return;
        }

        String site = domain.replaceAll("[^a-zA-Z0-9.-]", "_");
        String conf = "/etc/apache2/sites-available/" + site + ".conf";

        CommandRunner.must(List.of("sh","-lc",
                "mkdir -p '" + webRoot + "'; " +
                        "cat > '" + conf + "' <<'CONF'\n" +
                        "<VirtualHost *:8080>\n" +
                        "  ServerName " + domain + "\n" +
                        "  DocumentRoot " + webRoot + "\n" +
                        "  <Directory " + webRoot + ">\n" +
                        "    AllowOverride All\n" +
                        "    Require all granted\n" +
                        "  </Directory>\n" +
                        "  ErrorLog ${APACHE_LOG_DIR}/" + site + "_error.log\n" +
                        "  CustomLog ${APACHE_LOG_DIR}/" + site + "_access.log combined\n" +
                        "</VirtualHost>\n" +
                        "CONF\n"
        ));

        CommandRunner.run(List.of("sh","-lc","a2ensite '" + site + ".conf' >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh","-lc","a2enmod rewrite >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh","-lc","systemctl reload apache2 >/dev/null 2>&1 || systemctl restart apache2 >/dev/null 2>&1 || true"));
        ConsoleUI.ok("Apache VHost erstellt: " + domain + " -> " + webRoot + " (8080)");
    }
}
