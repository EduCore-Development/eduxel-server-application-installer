package dev.educore.core.os;

import dev.educore.core.CommandRunner;

import java.util.List;

public final class PackageManagers {
    private PackageManagers() {}

    public static PackageManager detectOrThrow() {
        if (CommandRunner.exists("apt-get")) return new Apt();
        if (CommandRunner.exists("dnf")) return new Dnf();
        if (CommandRunner.exists("yum")) return new Yum();
        if (CommandRunner.exists("pacman")) return new Pacman();
        if (CommandRunner.exists("zypper")) return new Zypper();
        if (CommandRunner.exists("apk")) return new Apk();
        throw new IllegalStateException("Kein unterstützter Package Manager gefunden.");
    }

    static final class Apt implements PackageManager {
        public String name() { return "apt"; }
        public void install(String... packages) {
            CommandRunner.must(List.of("sh","-lc","apt-get update -y"));
            CommandRunner.must(List.of("sh","-lc","DEBIAN_FRONTEND=noninteractive apt-get install -y " + String.join(" ", packages)));
        }
    }

    static final class Dnf implements PackageManager {
        public String name() { return "dnf"; }
        public void install(String... packages) {
            CommandRunner.must(List.of("sh","-lc","dnf install -y " + String.join(" ", packages)));
        }
    }

    static final class Yum implements PackageManager {
        public String name() { return "yum"; }
        public void install(String... packages) {
            CommandRunner.must(List.of("sh","-lc","yum install -y " + String.join(" ", packages)));
        }
    }

    static final class Pacman implements PackageManager {
        public String name() { return "pacman"; }
        public void install(String... packages) {
            CommandRunner.must(List.of("sh","-lc","pacman -Sy --noconfirm " + String.join(" ", packages)));
        }
    }

    static final class Zypper implements PackageManager {
        public String name() { return "zypper"; }
        public void install(String... packages) {
            CommandRunner.must(List.of("sh","-lc","zypper --non-interactive in " + String.join(" ", packages)));
        }
    }

    static final class Apk implements PackageManager {
        public String name() { return "apk"; }
        public void install(String... packages) {
            CommandRunner.must(List.of("sh","-lc","apk add --no-cache " + String.join(" ", packages)));
        }
    }
}
