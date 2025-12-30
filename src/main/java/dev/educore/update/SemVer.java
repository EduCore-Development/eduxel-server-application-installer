package dev.educore.update;

public final class SemVer {
    private SemVer() {}

    public static int compare(String a, String b) {
        int[] x = parse(a);
        int[] y = parse(b);
        for (int i = 0; i < 3; i++) {
            if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
        }
        return 0;
    }

    private static int[] parse(String v) {
        String[] p = v.split("\\.");
        int[] out = new int[] {0,0,0};
        for (int i = 0; i < Math.min(3, p.length); i++) {
            try { out[i] = Integer.parseInt(p[i].replaceAll("[^0-9]", "")); }
            catch (Exception ignored) {}
        }
        return out;
    }
}
