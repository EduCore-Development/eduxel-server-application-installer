package dev.educore.core.os;

public interface PackageManager {
    String name();
    void install(String... packages);
}
