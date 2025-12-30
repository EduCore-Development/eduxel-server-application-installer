package dev.educore.web;

import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class WebDashDeployer {

    public void deploy(Path webRoot, String overrideReleaseUrl) {
        try {
            Files.createDirectories(webRoot);

            String releaseUrl = overrideReleaseUrl;
            if (releaseUrl == null || releaseUrl.isBlank()) {
                releaseUrl = loadDefaultReleaseUrl();
            }

            if (releaseUrl == null || releaseUrl.isBlank()) {
                throw new IllegalStateException("Keine WebDash Release URL gesetzt.");
            }

            ConsoleUI.info("WebDash Release: " + releaseUrl);

            Path tmp = Files.createTempDirectory("eduxel-webdash");
            Path zip = tmp.resolve("webdash.zip");

            ConsoleUI.info("Lade WebDash…");
            try (InputStream in = new URL(releaseUrl).openStream()) {
                Files.copy(in, zip);
            }

            ConsoleUI.info("Entpacke WebDash nach " + webRoot);
            CommandRunner.must(
                    java.util.List.of(
                            "sh", "-lc",
                            "rm -rf '" + webRoot + "/*' && unzip -q '" + zip + "' -d '" + webRoot + "'"
                    )
            );

            Files.writeString(
                    webRoot.resolve("version.txt"),
                    "Deployed from: " + releaseUrl + "\n"
            );

            ConsoleUI.ok("WebDash erfolgreich deployed.");
        } catch (Exception e) {
            throw new IllegalStateException("WebDash Deploy fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private String loadDefaultReleaseUrl() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("eduxel-version.properties")) {

            if (in == null) return null;

            Properties p = new Properties();
            p.load(in);
            return p.getProperty("webapp.releaseUrl");
        } catch (Exception e) {
            return null;
        }
    }
}
