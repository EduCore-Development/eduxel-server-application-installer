package dev.educore.core;

public final class Root {
    private Root() {}

    public static void requireRoot() {
        String euid = System.getenv("EUID");
        if (euid != null && !"0".equals(euid)) throw new IllegalStateException("Bitte als root ausführen (sudo).");
        String user = System.getProperty("user.name");
        if (user != null && !"root".equals(user)) throw new IllegalStateException("Bitte als root ausführen (sudo).");
    }
}
