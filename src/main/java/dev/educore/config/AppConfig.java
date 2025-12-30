package dev.educore.config;

public record AppConfig(
        String mode,
        Database database,
        App app
) {
    public static AppConfig defaults(int appPort) {
        return new AppConfig(
                "auto",
                new Database("127.0.0.1", 3306, "eduxel", "CHANGEME", "eduxel"),
                new App(appPort, "CHANGEME")
        );
    }

    public AppConfig withNewSecret(String secret) {
        return new AppConfig(mode, database, new App(app.port, secret));
    }

    public AppConfig withDbPassword(String pass) {
        return new AppConfig(mode, new Database(database.host, database.port, database.user, pass, database.database), app);
    }

    public AppConfig withMode(String m) { return new AppConfig(m, database, app); }
    public AppConfig withDb(Database d) { return new AppConfig(mode, d, app); }

    public record Database(String host, int port, String user, String password, String database) {}
    public record App(int port, String secret) {}
}
