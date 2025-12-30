package dev.educore.web;

import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;
import dev.educore.core.os.PackageManager;

import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

public final class WebAppDeployer {

    private final PackageManager pm;

    public WebAppDeployer(PackageManager pm) {
        this.pm = pm;
    }

    public void deploy(String repo, String branch, Path targetRoot) {
        ensureNode();
        ensureGit();

        ConsoleUI.banner("Web Deploy");

        Path tmp = Path.of("/tmp/eduxel_web_" + System.currentTimeMillis());
        CommandRunner.must(List.of("sh","-lc","rm -rf '" + tmp + "' && mkdir -p '" + tmp + "'"));
        CommandRunner.must(List.of("sh","-lc","git clone --depth 1 --branch '" + branch + "' '" + repo + "' '" + tmp + "'"));

        boolean hasLock = Files.exists(tmp.resolve("package-lock.json"));
        String installCmd = hasLock ? "npm ci" : "npm install";
        CommandRunner.must(List.of("sh","-lc","cd '" + tmp + "' && " + installCmd));
        CommandRunner.must(List.of("sh","-lc","cd '" + tmp + "' && npm run build"));

        Path out = pickBuildOutput(tmp);
        if (out == null) throw new IllegalStateException("Build Output nicht gefunden (dist/build/out).");

        try {
            Files.createDirectories(targetRoot);
            wipeDir(targetRoot);

            copyDir(out, targetRoot);
            ConsoleUI.ok("Deployed nach: " + targetRoot);
        } catch (Exception e) {
            throw new IllegalStateException("Deploy fehlgeschlagen: " + e.getMessage(), e);
        }

        CommandRunner.run(List.of("sh","-lc","rm -rf '" + tmp + "' || true"));
    }

    private void ensureNode() {
        if (CommandRunner.exists("npm") && CommandRunner.exists("node")) {
            ConsoleUI.ok("Node/NPM ist da.");
            return;
        }
        ConsoleUI.info("Installiere Node/NPM (best effort)...");
        if ("apt".equals(pm.name())) pm.install("nodejs", "npm");
        else if ("dnf".equals(pm.name()) || "yum".equals(pm.name())) pm.install("nodejs", "npm");
        else pm.install("nodejs", "npm");
    }

    private void ensureGit() {
        if (CommandRunner.exists("git")) return;
        pm.install("git");
    }

    private Path pickBuildOutput(Path repoDir) {
        Path[] candidates = new Path[] {
                repoDir.resolve("dist"),
                repoDir.resolve("build"),
                repoDir.resolve("out")
        };
        for (Path p : candidates) if (Files.isDirectory(p)) return p;
        return null;
    }

    private static void wipeDir(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var w = Files.walk(dir)) {
            w.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        }
    }

    private static void copyDir(Path src, Path dst) throws Exception {
        try (var walk = Files.walk(src)) {
            walk.forEach(p -> {
                try {
                    Path rel = src.relativize(p);
                    Path t = dst.resolve(rel.toString());
                    if (Files.isDirectory(p)) Files.createDirectories(t);
                    else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
