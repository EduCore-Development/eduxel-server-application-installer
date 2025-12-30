package dev.educore.runtime;

import dev.educore.config.AppConfig;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class CredentialServer {

    private final AppConfig cfg;

    public CredentialServer(AppConfig cfg) {
        this.cfg = cfg;
    }

    public void startBlocking() {
        try (ServerSocket s = new ServerSocket(cfg.app().port())) {
            System.out.println("[Eduxel] API läuft auf Port " + cfg.app().port());
            while (true) {
                Socket c = s.accept();
                handle(c);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Server crash: " + e.getMessage(), e);
        }
    }

    private void handle(Socket c) {
        try (Socket conn = c;
             InputStream in = conn.getInputStream();
             OutputStream out = conn.getOutputStream()) {

            byte[] buf = in.readNBytes(1024);
            String token = new String(buf, StandardCharsets.UTF_8).trim();

            if (!cfg.app().secret().equals(token)) {
                out.write("INVALID".getBytes(StandardCharsets.UTF_8));
                return;
            }

            var db = cfg.database();
            String payload = "OK;HOST=%s;PORT=%d;USER=%s;PASS=%s;DB=%s"
                    .formatted(db.host(), db.port(), db.user(), db.password(), db.database());
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}
