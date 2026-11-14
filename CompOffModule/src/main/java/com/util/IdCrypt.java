package com.util;

/**
 * Request ids (HWRID / COID) as they appear in pages, forms and email links: encrypted with the application's
 * CipherText (the same values the <ed:crypt type="EPT"> tag writes), decrypted on the server.
 * The decrypted id is still checked against the logged-in user and role; encryption only hides the raw keys.
 * CipherText keeps Cipher objects, which are not thread-safe, so each thread uses its own instance.
 */
public final class IdCrypt {

    private static final ThreadLocal<CipherText> CIPHER = new ThreadLocal<CipherText>() {
        @Override
        protected CipherText initialValue() {
            return new CipherText();
        }
    };

    private IdCrypt() {
    }

    /** Encrypted text of an id ("" for null). */
    public static String enc(Integer id) {
        return id == null ? "" : CIPHER.get().encrypt(String.valueOf(id));
    }

    /** The id behind an encrypted value; null when the value is missing, not encrypted by us, or not a positive id. */
    public static Integer dec(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 200 || !value.trim().matches("[A-Za-z0-9]+")) {
            return null;
        }
        String plain = CIPHER.get().decrypt(value.trim());
        if (plain == null || !plain.matches("[0-9]{1,9}")) {
            return null;
        }
        int id = Integer.parseInt(plain);
        return id > 0 ? id : null;
    }
}
