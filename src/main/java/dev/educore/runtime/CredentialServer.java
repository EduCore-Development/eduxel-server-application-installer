package dev.educore.runtime;

import dev.educore.config.AppConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CredentialServer {

    private final AppConfig cfg;

    public CredentialServer(AppConfig cfg) {
        this.cfg = cfg;
    }

    public void startBlocking() {
        int port = cfg.app().port();

        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("0.0.0.0", port), 50);

            ExecutorService pool = Executors.newCachedThreadPool();

            System.out.println("[Eduxel] API läuft auf Port " + port);

            while (true) {
                Socket s = server.accept();
                pool.execute(() -> handle(s));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Server konnte nicht starten: " + e.getMessage(), e);
        }
    }

    private void handle(Socket s) {
        try (s) {
            s.setSoTimeout(600);

            String token = readTokenOnce(s);
            if (token.isBlank()) {
                try {
                    OutputStream out = s.getOutputStream();
                    out.write("READY\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception ignored) {
                }

                s.setSoTimeout(2000);
                token = readTokenOnce(s);
            }

            OutputStream out = s.getOutputStream();

            if (!token.equals(cfg.app().secret())) {
                out.write("INVALID".getBytes(StandardCharsets.UTF_8));
                out.flush();
                return;
            }

            var db = cfg.database();
            String resp = "OK;HOST=" + db.host() +
                    ";PORT=" + db.port() +
                    ";USER=" + db.user() +
                    ";PASS=" + db.password() +
                    ";DB=" + db.database();

            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception ignored) {
        }
    }

    private String readTokenOnce(Socket s) {
        try {
            InputStream in = s.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[512];

            while (baos.size() < 4096) {
                int n;
                try {
                    n = in.read(buf);
                } catch (SocketTimeoutException e) {
                    break;
                }
                if (n <= 0) break;

                baos.write(buf, 0, n);

                String cur = baos.toString(StandardCharsets.UTF_8);
                if (cur.indexOf('\n') >= 0 || cur.indexOf('\r') >= 0) break;
            }

            return baos.toString(StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
