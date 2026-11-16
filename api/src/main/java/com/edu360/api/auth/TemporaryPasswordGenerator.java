package com.edu360.api.auth;

import java.security.SecureRandom;

/**
 * One-time passwords handed to new or reset accounts. Every character is random; the password always holds
 * an upper-case letter, a lower-case letter, a digit and a symbol, so it passes the strictest rules
 * KeycloakRealmSecurityService can push (digits(1), specialChars(1)). Look-alike characters (0/O, 1/l/I) are
 * left out because student passwords are printed.
 */
public final class TemporaryPasswordGenerator {

    public static final int LENGTH = 16;

    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    static final String DIGITS = "23456789";
    static final String SYMBOLS = "@#$%&*!?";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswordGenerator() {
    }

    public static String generate() {
        char[] chars = new char[LENGTH];
        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        chars[3] = pick(SYMBOLS);
        for (int i = 4; i < LENGTH; i++) {
            chars[i] = pick(ALL);
        }
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char swap = chars[i];
            chars[i] = chars[j];
            chars[j] = swap;
        }
        return new String(chars);
    }

    private static char pick(String pool) {
        return pool.charAt(RANDOM.nextInt(pool.length()));
    }
}
