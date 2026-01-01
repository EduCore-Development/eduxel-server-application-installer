package dev.educore.web;

import dev.educore.config.AppConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

        String now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));

        String dbHost = cfg.database().host();
        int dbPort = cfg.database().port();
        String dbName = cfg.database().database();
        String dbUser = cfg.database().user();

        String dbUrlSafe = "mariadb://" + dbHost + ":" + dbPort + "/" + dbName;

        String webappBlock = webappReleaseUrl.isBlank()
                ? "<div class=\"value muted\">Not set</div>"
                : "<a class=\"btn ghost\" href=\"" + escape(webappReleaseUrl) + "\" target=\"_blank\" rel=\"noreferrer\">" +
                icon("arrow") + "<span>Open WebApp Release</span></a>";

        String secret = cfg.app().secret() == null ? "" : cfg.app().secret();

        return """
                <!doctype html>
                <html lang="de">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width,initial-scale=1" />
                  <title>%s • Server</title>
                  <meta name="robots" content="noindex,nofollow" />
                  <meta name="color-scheme" content="dark" />
                  <style>
                    :root{
                      color-scheme:dark;
                      --bg0:#070910;
                      --bg1:#0b0f1f;
                      --card:rgba(255,255,255,.06);
                      --card2:rgba(255,255,255,.04);
                      --stroke:rgba(255,255,255,.10);
                      --stroke2:rgba(255,255,255,.08);
                      --txt:#eaf0ff;
                      --muted:rgba(234,240,255,.70);
                      --muted2:rgba(234,240,255,.55);
                      --accent:#a78bfa;
                      --accent2:#22d3ee;
                      --good:#34d399;
                      --shadow:0 18px 55px rgba(0,0,0,.45);
                      --r:22px;
                    }

                    *{ box-sizing:border-box; }
                    html,body{ height:100%%; }
                    body{
                      margin:0;
                      font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Arial;
                      color:var(--txt);
                      background:
                        radial-gradient(1200px 700px at 15%% 10%%, rgba(167,139,250,.25), transparent 55%%),
                        radial-gradient(900px 600px at 80%% 20%%, rgba(34,211,238,.18), transparent 55%%),
                        radial-gradient(800px 600px at 60%% 90%%, rgba(167,139,250,.14), transparent 60%%),
                        linear-gradient(180deg, var(--bg0), var(--bg1));
                      overflow-x:hidden;
                    }

                    .wrap{ max-width: 1100px; margin: 0 auto; padding: 46px 18px 56px; }

                    .shell{
                      border:1px solid var(--stroke);
                      background: linear-gradient(180deg, rgba(255,255,255,.08), rgba(255,255,255,.03));
                      border-radius: calc(var(--r) + 6px);
                      box-shadow: var(--shadow);
                      backdrop-filter: blur(10px);
                      -webkit-backdrop-filter: blur(10px);
                      overflow:hidden;
                      position:relative;
                    }

                    .shell::before{
                      content:"";
                      position:absolute;
                      inset:-2px;
                      background:
                        radial-gradient(600px 200px at 15%% 0%%, rgba(167,139,250,.35), transparent 55%%),
                        radial-gradient(600px 200px at 85%% 0%%, rgba(34,211,238,.30), transparent 55%%);
                      opacity:.55;
                      pointer-events:none;
                      filter: blur(10px);
                    }

                    .head{
                      position:relative;
                      padding: 22px 22px 18px;
                      border-bottom:1px solid rgba(255,255,255,.08);
                      display:flex;
                      align-items:flex-start;
                      justify-content:space-between;
                      gap:14px;
                      flex-wrap:wrap;
                    }

                    .brand{
                      display:flex;
                      gap:14px;
                      align-items:center;
                      min-width: 260px;
                    }

                    .mark{
                      width:44px; height:44px; border-radius:14px;
                      background: linear-gradient(135deg, rgba(167,139,250,.85), rgba(34,211,238,.85));
                      box-shadow: 0 0 35px rgba(167,139,250,.35);
                      display:grid; place-items:center;
                      border:1px solid rgba(255,255,255,.12);
                    }

                    .mark svg{ width:22px; height:22px; opacity:.95; }

                    h1{
                      margin:0;
                      font-size: 20px;
                      letter-spacing:.3px;
                      line-height:1.1;
                    }

                    .sub{
                      margin-top:6px;
                      font-size: 12px;
                      color: var(--muted);
                      display:flex;
                      gap:10px;
                      flex-wrap:wrap;
                      align-items:center;
                    }

                    .badge{
                      display:inline-flex;
                      gap:8px;
                      align-items:center;
                      padding: 8px 10px;
                      border-radius:999px;
                      border:1px solid rgba(255,255,255,.12);
                      background: rgba(0,0,0,.18);
                      color: var(--muted);
                      font-size: 12px;
                      user-select:none;
                    }

                    .dot{
                      width:8px; height:8px; border-radius:999px;
                      background: var(--good);
                      box-shadow: 0 0 18px rgba(52,211,153,.45);
                    }

                    .content{
                      position:relative;
                      padding: 18px 22px 22px;
                    }

                    .grid{
                      display:grid;
                      grid-template-columns: repeat(12, 1fr);
                      gap: 14px;
                    }

                    .card{
                      border:1px solid var(--stroke2);
                      background: rgba(0,0,0,.18);
                      border-radius: var(--r);
                      padding: 16px;
                      transition: transform .15s ease, border-color .15s ease, background .15s ease;
                    }

                    .card:hover{
                      transform: translateY(-1px);
                      border-color: rgba(167,139,250,.28);
                      background: rgba(0,0,0,.22);
                    }

                    .span-6{ grid-column: span 6; }
                    .span-4{ grid-column: span 4; }
                    .span-8{ grid-column: span 8; }
                    .span-12{ grid-column: span 12; }

                    @media (max-width: 920px){
                      .span-6,.span-4,.span-8{ grid-column: span 12; }
                    }

                    .title{
                      display:flex;
                      align-items:center;
                      gap:10px;
                      margin:0 0 12px;
                      font-size: 13px;
                      letter-spacing:.25px;
                      color: rgba(234,240,255,.86);
                    }

                    .title .ico{
                      width:30px; height:30px;
                      border-radius: 12px;
                      display:grid; place-items:center;
                      border:1px solid rgba(255,255,255,.10);
                      background: rgba(255,255,255,.05);
                    }

                    .title .ico svg{ width:16px; height:16px; opacity:.92; }

                    .kv{
                      display:flex;
                      flex-direction:column;
                      gap: 10px;
                    }

                    .row{
                      display:flex;
                      justify-content:space-between;
                      gap: 12px;
                      align-items:flex-start;
                      padding: 10px 12px;
                      border-radius: 16px;
                      border:1px solid rgba(255,255,255,.07);
                      background: rgba(255,255,255,.03);
                    }

                    .k{
                      font-size: 12px;
                      color: var(--muted2);
                      min-width: 120px;
                    }

                    .value{
                      font-size: 13px;
                      color: var(--txt);
                      word-break: break-all;
                      display:flex;
                      gap:10px;
                      align-items:center;
                      justify-content:flex-end;
                      flex-wrap:wrap;
                      text-align:right;
                    }

                    code{
                      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
                      font-size: 12px;
                      padding: 3px 8px;
                      border-radius: 10px;
                      border:1px solid rgba(255,255,255,.10);
                      background: rgba(0,0,0,.28);
                    }

                    .muted{ color: var(--muted); }

                    .btn{
                      display:inline-flex;
                      gap:8px;
                      align-items:center;
                      border-radius: 14px;
                      padding: 10px 12px;
                      border:1px solid rgba(255,255,255,.10);
                      background: rgba(255,255,255,.06);
                      color: var(--txt);
                      text-decoration:none;
                      font-size: 12px;
                      cursor:pointer;
                      user-select:none;
                      transition: transform .12s ease, background .12s ease, border-color .12s ease;
                    }

                    .btn:hover{
                      transform: translateY(-1px);
                      border-color: rgba(167,139,250,.28);
                      background: rgba(167,139,250,.10);
                    }

                    .btn:active{
                      transform: translateY(0px);
                    }

                    .btn svg{ width:14px; height:14px; opacity:.95; }

                    .btn.ghost{
                      background: rgba(0,0,0,.16);
                    }

                    .actions{
                      display:flex;
                      gap:10px;
                      flex-wrap:wrap;
                      margin-top: 12px;
                    }

                    .foot{
                      position:relative;
                      padding: 14px 22px;
                      border-top:1px solid rgba(255,255,255,.08);
                      display:flex;
                      justify-content:space-between;
                      gap:10px;
                      flex-wrap:wrap;
                      font-size: 12px;
                      color: var(--muted);
                    }

                    .toast{
                      position: fixed;
                      left: 50%%;
                      bottom: 18px;
                      transform: translateX(-50%%);
                      background: rgba(0,0,0,.55);
                      border:1px solid rgba(255,255,255,.12);
                      padding: 10px 12px;
                      border-radius: 14px;
                      box-shadow: var(--shadow);
                      backdrop-filter: blur(10px);
                      -webkit-backdrop-filter: blur(10px);
                      color: var(--txt);
                      font-size: 12px;
                      opacity: 0;
                      pointer-events:none;
                      transition: opacity .15s ease, transform .15s ease;
                      transform-origin: center;
                    }

                    .toast.show{
                      opacity: 1;
                      transform: translateX(-50%%) translateY(-2px);
                    }

                    .sep{
                      height: 1px;
                      background: rgba(255,255,255,.08);
                      margin: 10px 0 0;
                    }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="shell">
                      <div class="head">
                        <div class="brand">
                          <div class="mark" aria-hidden="true">
                            %s
                          </div>
                          <div>
                            <h1>%s Server</h1>
                            <div class="sub">
                              <span>%s</span>
                              <span class="muted">•</span>
                              <span>build %s</span>
                              <span class="muted">•</span>
                              <span class="muted">Generated %s</span>
                            </div>
                          </div>
                        </div>
                        <div class="badge"><span class="dot"></span><span>TLS/SSL by Caddy</span></div>
                      </div>

                      <div class="content">
                        <div class="grid">
                          <div class="card span-6">
                            <div class="title">
                              <span class="ico">%s</span>
                              <span>Endpoints</span>
                            </div>
                            <div class="kv">
                              <div class="row">
                                <div class="k">Domain</div>
                                <div class="value">
                                  <code id="v-domain">%s</code>
                                  <button class="btn" type="button" onclick="copyId('v-domain')">%s<span>Copy</span></button>
                                </div>
                              </div>
                              <div class="row">
                                <div class="k">Public IP</div>
                                <div class="value">
                                  <code id="v-ip">%s</code>
                                  <button class="btn" type="button" onclick="copyId('v-ip')">%s<span>Copy</span></button>
                                </div>
                              </div>
                              <div class="row">
                                <div class="k">Credential API</div>
                                <div class="value">
                                  <span class="muted">Port</span>
                                  <code id="v-port">%d</code>
                                  <button class="btn" type="button" onclick="copyId('v-port')">%s<span>Copy</span></button>
                                </div>
                              </div>
                            </div>
                          </div>

                          <div class="card span-6">
                            <div class="title">
                              <span class="ico">%s</span>
                              <span>Secrets</span>
                            </div>
                            <div class="kv">
                              <div class="row">
                                <div class="k">App Secret</div>
                                <div class="value">
                                  <code id="v-secret" data-raw="%s" data-masked="••••••••••••••••">%s</code>
                                  <button class="btn" type="button" onclick="toggleSecret()">%s<span id="secretBtn">Reveal</span></button>
                                  <button class="btn" type="button" onclick="copySecret()">%s<span>Copy</span></button>
                                </div>
                              </div>
                              <div class="row">
                                <div class="k">Config Path</div>
                                <div class="value"><code>/etc/eduxel/config.json</code></div>
                              </div>
                              <div class="row">
                                <div class="k">Service</div>
                                <div class="value"><code>systemctl status eduxel</code></div>
                              </div>
                            </div>
                          </div>

                          <div class="card span-8">
                            <div class="title">
                              <span class="ico">%s</span>
                              <span>Database</span>
                            </div>
                            <div class="kv">
                              <div class="row">
                                <div class="k">URL</div>
                                <div class="value">
                                  <code id="v-dburl">%s</code>
                                  <button class="btn" type="button" onclick="copyId('v-dburl')">%s<span>Copy</span></button>
                                </div>
                              </div>
                              <div class="row">
                                <div class="k">Host</div>
                                <div class="value"><code>%s</code></div>
                              </div>
                              <div class="row">
                                <div class="k">User</div>
                                <div class="value"><code>%s</code></div>
                              </div>
                              <div class="row">
                                <div class="k">Database</div>
                                <div class="value"><code>%s</code></div>
                              </div>
                            </div>
                          </div>

                          <div class="card span-4">
                            <div class="title">
                              <span class="ico">%s</span>
                              <span>WebApp</span>
                            </div>
                            <div class="kv">
                              <div class="row">
                                <div class="k">Release</div>
                                <div class="value">%s</div>
                              </div>
                              <div class="actions">
                                <button class="btn ghost" type="button" onclick="location.reload()">%s<span>Refresh</span></button>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div class="foot">
                        <div>%s</div>
                        <div class="muted">Internal landing page • noindex/nofollow</div>
                      </div>
                    </div>
                  </div>

                  <div id="toast" class="toast">Copied</div>

                  <script>
                    function toast(msg){
                      const t = document.getElementById('toast');
                      t.textContent = msg || 'OK';
                      t.classList.add('show');
                      clearTimeout(window.__toastTimer);
                      window.__toastTimer = setTimeout(() => t.classList.remove('show'), 1200);
                    }

                    async function copyText(text){
                      try{
                        if(navigator.clipboard && window.isSecureContext){
                          await navigator.clipboard.writeText(text);
                        }else{
                          const ta = document.createElement('textarea');
                          ta.value = text;
                          ta.style.position = 'fixed';
                          ta.style.opacity = '0';
                          document.body.appendChild(ta);
                          ta.focus();
                          ta.select();
                          document.execCommand('copy');
                          document.body.removeChild(ta);
                        }
                        toast('Copied');
                      }catch(e){
                        toast('Copy failed');
                      }
                    }

                    function copyId(id){
                      const el = document.getElementById(id);
                      if(!el) return;
                      copyText(el.textContent.trim());
                    }

                    function toggleSecret(){
                      const el = document.getElementById('v-secret');
                      const btn = document.getElementById('secretBtn');
                      if(!el) return;
                      const raw = el.getAttribute('data-raw') || '';
                      const masked = el.getAttribute('data-masked') || '••••••••';
                      const showing = el.textContent !== masked;
                      el.textContent = showing ? masked : raw;
                      btn.textContent = showing ? 'Reveal' : 'Hide';
                      toast(showing ? 'Hidden' : 'Revealed');
                    }

                    function copySecret(){
                      const el = document.getElementById('v-secret');
                      if(!el) return;
                      const raw = el.getAttribute('data-raw') || el.textContent || '';
                      copyText(raw.trim());
                    }
                  </script>
                </body>
                </html>
                """.formatted(
                escape(name),
                icon("spark"),
                escape(name),
                escape(version),
                escape(build),
                escape(now),

                icon("server"),
                escape(domain),
                icon("copy"),
                escape(publicIp),
                icon("copy"),
                cfg.app().port(),
                icon("copy"),

                icon("shield"),
                escape(secret),
                escape("••••••••••••••••"),
                icon("eye"),
                icon("copy"),

                icon("db"),
                escape(dbUrlSafe),
                icon("copy"),
                escape(dbHost + ":" + dbPort),
                escape(dbUser),
                escape(dbName),

                icon("globe"),
                webappBlock,
                icon("refresh"),

                escape("© " + escape(name) + " • " + now)
        );
    }

    private String icon(String name) {
        return switch (name) {
            case "spark" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M12 2l1.2 4.3L17.5 8 13.2 9.2 12 13.5 10.8 9.2 6.5 8l4.3-1.7L12 2Z" fill="rgba(255,255,255,.92)"/>
                      <path d="M19 12l.7 2.6L22 15l-2.3.7L19 18l-.7-2.3L16 15l2.3-.4L19 12Z" fill="rgba(255,255,255,.80)"/>
                      <path d="M6 13l.8 2.9L10 17l-3.2.8L6 21l-.8-3.2L2 17l3.2-1.1L6 13Z" fill="rgba(255,255,255,.75)"/>
                    </svg>
                    """;
            case "server" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M6 7c0-1.1.9-2 2-2h8c1.1 0 2 .9 2 2v2c0 1.1-.9 2-2 2H8c-1.1 0-2-.9-2-2V7Z" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                      <path d="M6 15c0-1.1.9-2 2-2h8c1.1 0 2 .9 2 2v2c0 1.1-.9 2-2 2H8c-1.1 0-2-.9-2-2v-2Z" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                      <path d="M9 8h.01M9 16h.01" stroke="rgba(34,211,238,.95)" stroke-width="3" stroke-linecap="round"/>
                    </svg>
                    """;
            case "shield" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M12 3l7 4v6c0 5-3.3 8.6-7 9-3.7-.4-7-4-7-9V7l7-4Z" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                      <path d="M9.5 12.3l1.6 1.6 3.8-4.2" stroke="rgba(52,211,153,.95)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    """;
            case "db" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M12 4c4 0 7 1.3 7 3s-3 3-7 3-7-1.3-7-3 3-3 7-3Z" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                      <path d="M5 7v5c0 1.7 3 3 7 3s7-1.3 7-3V7" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                      <path d="M5 12v5c0 1.7 3 3 7 3s7-1.3 7-3v-5" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                    </svg>
                    """;
            case "globe" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                      <path d="M2 12h20" stroke="rgba(234,240,255,.60)" stroke-width="1.6"/>
                      <path d="M12 2c2.8 2.7 4.3 6.2 4.3 10S14.8 19.3 12 22c-2.8-2.7-4.3-6.2-4.3-10S9.2 4.7 12 2Z" stroke="rgba(34,211,238,.85)" stroke-width="1.6"/>
                    </svg>
                    """;
            case "copy" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M9 9h10v10H9V9Z" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                      <path d="M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1" stroke="rgba(234,240,255,.55)" stroke-width="1.6"/>
                    </svg>
                    """;
            case "eye" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" stroke="rgba(234,240,255,.85)" stroke-width="1.6"/>
                      <path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" stroke="rgba(34,211,238,.95)" stroke-width="1.6"/>
                    </svg>
                    """;
            case "refresh" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M20 12a8 8 0 1 1-2.3-5.7" stroke="rgba(234,240,255,.85)" stroke-width="1.6" stroke-linecap="round"/>
                      <path d="M20 4v6h-6" stroke="rgba(34,211,238,.95)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    """;
            case "arrow" -> """
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                      <path d="M7 17L17 7" stroke="rgba(234,240,255,.90)" stroke-width="1.6" stroke-linecap="round"/>
                      <path d="M10 7h7v7" stroke="rgba(34,211,238,.95)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    """;
            default -> "";
        };
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
