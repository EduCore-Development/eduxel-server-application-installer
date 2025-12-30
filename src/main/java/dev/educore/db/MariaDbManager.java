package dev.educore.db;

import dev.educore.config.AppConfig;
import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;
import dev.educore.core.os.PackageManager;
import dev.educore.security.Secrets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MariaDbManager {

    private final PackageManager pm;

    public MariaDbManager(PackageManager pm) {
        this.pm = pm;
    }

    public void ensureInstalledAndRunning() {
        if (!CommandRunner.exists("mariadb") && !CommandRunner.exists("mysql")) {
            ConsoleUI.info("Installiere MariaDB...");
            switch (pm.name()) {
                case "apt" -> pm.install("mariadb-server", "mariadb-client");
                case "dnf", "yum" -> pm.install("mariadb-server", "mariadb");
                case "pacman" -> pm.install("mariadb");
                case "zypper" -> pm.install("mariadb", "mariadb-client");
                case "apk" -> pm.install("mariadb", "mariadb-client");
                default -> throw new IllegalStateException("MariaDB Packages unbekannt für: " + pm.name());
            }
        }
        CommandRunner.run(List.of("sh","-lc","systemctl enable mariadb >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh","-lc","systemctl enable mysql >/dev/null 2>&1 || true"));
        CommandRunner.run(List.of("sh","-lc","systemctl restart mariadb >/dev/null 2>&1 || systemctl restart mysql >/dev/null 2>&1 || true"));
        enableRemoteBind();
    }

    public AppConfig provisionAuto(AppConfig cfg, String hostIp) {
        String dbName = "eduxel";
        String dbUser = "eduxel";
        String dbPass = Secrets.password(20);
        String secret = Secrets.hex(32);

        String mysqlCmd = mysqlCmdPrompt();
        String sql = """
                CREATE DATABASE IF NOT EXISTS %s;
                CREATE USER IF NOT EXISTS '%s'@'%%' IDENTIFIED BY '%s';
                ALTER USER '%s'@'%%' IDENTIFIED BY '%s';
                GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%';
                FLUSH PRIVILEGES;
                """.formatted(dbName, dbUser, dbPass, dbUser, dbPass, dbName, dbUser);

        CommandRunner.must(List.of("sh","-lc", mysqlCmd + " <<'SQL'\n" + sql + "\nSQL"));

        ConsoleUI.ok("MariaDB User/DB erstellt.");

        return new AppConfig(
                "auto",
                new AppConfig.Database(hostIp, 3306, dbUser, dbPass, dbName),
                new AppConfig.App(cfg.app().port(), secret)
        );
    }

    public AppConfig provisionManual(AppConfig cfg) {
        String secret = Secrets.hex(32);
        return new AppConfig(
                "manual",
                new AppConfig.Database("HIER_EINTRAGEN", 3306, "HIER_EINTRAGEN", "HIER_EINTRAGEN", "HIER_EINTRAGEN"),
                new AppConfig.App(cfg.app().port(), secret)
        );
    }

    public AppConfig rotatePasswordAuto(AppConfig cfg) {
        String newPass = Secrets.password(20);
        String mysqlCmd = mysqlCmdPrompt();

        String sql = """
                ALTER USER '%s'@'%%' IDENTIFIED BY '%s';
                GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%';
                FLUSH PRIVILEGES;
                """.formatted(cfg.database().user(), newPass, cfg.database().database(), cfg.database().user());

        CommandRunner.must(List.of("sh","-lc", mysqlCmd + " <<'SQL'\n" + sql + "\nSQL"));
        ConsoleUI.ok("DB Passwort rotiert.");
        return cfg.withDbPassword(newPass);
    }

    private void enableRemoteBind() {
        Path[] candidates = new Path[] {
                Path.of("/etc/mysql/mariadb.conf.d/50-server.cnf"),
                Path.of("/etc/mysql/mysql.conf.d/mysqld.cnf"),
                Path.of("/etc/my.cnf"),
                Path.of("/etc/my.cnf.d/mariadb-server.cnf"),
                Path.of("/etc/my.cnf.d/server.cnf"),
                Path.of("/etc/my.cnf.d/mysqld.cnf")
        };

        Path cnf = null;
        for (Path p : candidates) {
            if (Files.exists(p)) { cnf = p; break; }
        }
        if (cnf == null) return;

        CommandRunner.run(List.of("sh","-lc",
                "grep -qE '^[[:space:]]*bind-address' " + cnf + " && " +
                        "sed -i 's/^[[:space:]]*bind-address.*/bind-address = 0.0.0.0/' " + cnf +
                        " || printf '\\n[mysqld]\\nbind-address = 0.0.0.0\\n' >> " + cnf
        ));

        CommandRunner.run(List.of("sh","-lc","systemctl restart mariadb >/dev/null 2>&1 || systemctl restart mysql >/dev/null 2>&1 || true"));
        ConsoleUI.ok("MariaDB bind-address auf 0.0.0.0 gesetzt.");
    }

    private String mysqlCmdPrompt() {
        String detected = mysqlCmdDetect();
        if (!detected.isBlank()) return detected;

        ConsoleUI.warn("Kein automatischer Root-DB Zugriff. Passwort nötig.");
        String pass = ConsoleUI.askHidden("MariaDB root Passwort: ");
        if (pass.isBlank()) throw new IllegalStateException("Kein Passwort eingegeben.");

        String cmd = "mariadb -u root -p'" + pass.replace("'", "'\"'\"'") + "'";
        CommandRunner.Result r = CommandRunner.run(List.of("sh","-lc", cmd + " -e \"SELECT 1;\" >/dev/null 2>&1; echo $?"));
        if (!r.output().trim().endsWith("0")) throw new IllegalStateException("DB Login fehlgeschlagen (root).");
        return cmd;
    }

    private String mysqlCmdDetect() {
        if (CommandRunner.exists("mariadb")) {
            CommandRunner.Result r = CommandRunner.run(List.of("sh","-lc","mariadb -e \"SELECT 1;\" >/dev/null 2>&1; echo $?"));
            if (r.output().trim().endsWith("0")) return "mariadb";
        }
        if (CommandRunner.exists("mysql")) {
            CommandRunner.Result r = CommandRunner.run(List.of("sh","-lc","mysql -u root -e \"SELECT 1;\" >/dev/null 2>&1; echo $?"));
            if (r.output().trim().endsWith("0")) return "mysql -u root";
        }
        return "";
    }
}
