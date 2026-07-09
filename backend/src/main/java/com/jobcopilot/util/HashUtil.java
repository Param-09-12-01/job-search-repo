package com.jobcopilot.util;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashing helpers, primarily for building posting de-duplication fingerprints.
 */
@UtilityClass
public class HashUtil {

    /**
     * Compute a lowercase, hex-encoded SHA-256 fingerprint of a posting's identity fields.
     * Fields are normalized (trimmed, lowercased) so trivial formatting differences collapse.
     */
    public static String fingerprint(String title, String company, String location) {
        String normalized = normalize(title) + "|" + normalize(company) + "|" + normalize(location);
        return sha256(normalized);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
