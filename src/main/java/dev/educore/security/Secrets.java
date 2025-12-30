package dev.educore.security;

import java.security.SecureRandom;

public final class Secrets {
    private Secrets() {}

    private static final SecureRandom R = new SecureRandom();
    private static final char[] ALPH = "0123456789abcdef".toCharArray();
    private static final char[] PASS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+=-".toCharArray();

    public static String hex(int bytes) {
        byte[] b = new byte[bytes];
        R.nextBytes(b);
        char[] out = new char[bytes * 2];
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            out[i*2] = ALPH[v >>> 4];
            out[i*2+1] = ALPH[v & 0x0F];
        }
        return new String(out);
    }

    public static String password(int len) {
        char[] out = new char[len];
        for (int i = 0; i < len; i++) out[i] = PASS[R.nextInt(PASS.length)];
        return new String(out);
    }
}
