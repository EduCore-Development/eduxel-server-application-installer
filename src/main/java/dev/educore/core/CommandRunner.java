package dev.educore.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class CommandRunner {
    private CommandRunner() {}

    public static Result run(List<String> cmd) {
        return run(cmd, Duration.ofMinutes(5));
    }

    public static Result run(List<String> cmd, Duration timeout) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(baos);
            }

            boolean done = p.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                return new Result(124, baos.toString(StandardCharsets.UTF_8) + "\nTIMEOUT");
            }

            int code = p.exitValue();
            String out = baos.toString(StandardCharsets.UTF_8);
            return new Result(code, out);
        } catch (Exception e) {
            return new Result(999, e.toString());
        }
    }

    public static Result must(List<String> cmd) {
        Result r = run(cmd);
        if (!r.ok()) {
            throw new IllegalStateException("Command failed: " + String.join(" ", cmd) + "\n" + r.output());
        }
        return r;
    }

    public static boolean exists(String bin) {
        Result r = run(List.of("sh", "-lc", "command -v " + bin + " >/dev/null 2>&1"));
        return r.ok();
    }

    public record Result(int code, String output) {
        public int exitCode() { return code; }
        public boolean ok() { return code == 0; }
    }
}
