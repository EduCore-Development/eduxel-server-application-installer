package dev.educore.web;

import dev.educore.config.AppConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

public final class LandingPageWriter {

    public void write(Path webRoot, AppConfig cfg, String domain, String publicIp) {
        try {
            Files.createDirectories(webRoot);

            Properties p = loadVersionProps();
            String appName = p.getProperty("app.name", "Eduxel");
            String appVersion = p.getProperty("app.version", "unknown");
            String appBuild = p.getProperty("app.build", "unknown");
            String webappReleaseUrl = p.getProperty("webapp.releaseUrl", "").trim();

            String html = buildHtml(appName, appVersion, appBuild, webappReleaseUrl, cfg, domain, publicIp);

            Files.writeString(webRoot.resolve("index.html"), html, StandardCharsets.UTF_8);
            Files.writeString(webRoot.resolve("robots.txt"), "User-agent: *\nDisallow:\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Landingpage schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private Properties loadVersionProps() {
        try (InputStream in = LandingPageWriter.class.getClassLoader().getResourceAsStream("eduxel-version.properties")) {
            Properties p = new Properties();
            if (in != null) p.load(in);
            return p;
        } catch (Exception e) {
            throw new IllegalStateException("eduxel-version.properties konnte nicht geladen werden: " + e.getMessage(), e);
        }
    }

    private String buildHtml(String name, String version, String build, String webappReleaseUrl,
                             AppConfig cfg, String domain, String publicIp) {

        String dbUrl = "mariadb://" + cfg.database().user() + "@" + cfg.database().host() + ":" + cfg.database().port() + "/" + cfg.database().database();
        String now = Instant.now().toString();

        String webappLine = webappReleaseUrl.isBlank()
                ? "<span class=\"muted\">WebApp Release URL: not set</span>"
                : "<a class=\"link\" href=\"" + escape(webappReleaseUrl) + "\" target=\"_blank\" rel=\"noreferrer\">WebApp Release URL</a>";

        return """
                <!doctype html>
                <html lang="de">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width,initial-scale=1" />
                  <title>%s • Server</title>
                  <meta name="robots" content="noindex,nofollow" />
                  <style>
                    :root { color-scheme: dark; }
                    body { margin:0; font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Arial; background:#0b0e14; color:#e8eefc; }
                    .wrap { max-width: 980px; margin: 0 auto; padding: 42px 18px; }
                    .card { background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.10); border-radius: 18px; padding: 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.35); }
                    .top { display:flex; gap:14px; align-items:center; justify-content:space-between; flex-wrap:wrap; }
                    .brand { display:flex; gap:12px; align-items:center; }
                    .dot { width:12px; height:12px; border-radius:999px; background: #7c3aed; box-shadow: 0 0 24px rgba(124,58,237,0.8); }
                    h1 { font-size: 22px; margin:0; letter-spacing:0.4px; }
                    .pill { font-size: 12px; padding: 7px 10px; border-radius:999px; background: rgba(124,58,237,0.15); border:1px solid rgba(124,58,237,0.35); }
                    .grid { display:grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 14px; margin-top:14px; }
                    .item { background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 14px; }
                    .k { font-size: 12px; opacity:0.75; margin-bottom:6px; }
                    .v { font-size: 14px; word-break: break-all; }
                    .muted { opacity:0.7; }
                    .link { color:#a78bfa; text-decoration:none; }
                    .link:hover { text-decoration:underline; }
                    .footer { margin-top: 14px; font-size: 12px; opacity: 0.6; display:flex; justify-content:space-between; gap: 10px; flex-wrap:wrap; }
                    code { background: rgba(0,0,0,0.35); padding: 2px 6px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.08); }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="card">
                      <div class="top">
                        <div class="brand">
                          <div class="dot"></div>
                          <div>
                            <h1>%s Server</h1>
                            <div class="muted">%s • build %s</div>
                          </div>
                        </div>
                        <div class="pill">TLS/SSL by Caddy • %s</div>
                      </div>

                      <div class="grid">
                        <div class="item">
                          <div class="k">Domain</div>
                          <div class="v">%s</div>
                        </div>
                        <div class="item">
                          <div class="k">Public IP</div>
                          <div class="v">%s</div>
                        </div>
                        <div class="item">
                          <div class="k">Credential API</div>
                          <div class="v">Port: <code>%d</code></div>
                        </div>
                        <div class="item">
                          <div class="k">Secret</div>
                          <div class="v"><code>%s</code></div>
                        </div>
                        <div class="item">
                          <div class="k">Database</div>
                          <div class="v">%s</div>
                        </div>
                        <div class="item">
                          <div class="k">WebApp</div>
                          <div class="v">%s</div>
                        </div>
                      </div>

                      <div class="footer">
                        <div>Config: <code>/etc/eduxel/config.json</code></div>
                        <div>Service: <code>systemctl status eduxel</code></div>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(name),
                escape(name),
                escape(version),
                escape(build),
                escape(now),
                escape(domain),
                escape(publicIp),
                cfg.app().port(),
                escape(cfg.app().secret()),
                escape(dbUrl),
                webappLine
        );
    }

    private String escape(String s) {
        return s == null ? "" : s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
