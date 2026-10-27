package com.processpuzzle.ai.assistant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The SHA-256 of a tool call's arguments, so the audit trail can match calls without holding what they said. */
final class ArgumentHash {

    private static final String SEPARATOR = "\u0000";

    private ArgumentHash() {
    }

    static String of(String... arguments) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(String.join(SEPARATOR, arguments).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256", e);
        }
    }
}
