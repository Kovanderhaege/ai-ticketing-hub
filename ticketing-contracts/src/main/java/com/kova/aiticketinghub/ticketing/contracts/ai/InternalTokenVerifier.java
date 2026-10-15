package com.kova.aiticketinghub.ticketing.contracts.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class InternalTokenVerifier {
    private InternalTokenVerifier() {}
    public static boolean configured(String token) { return token != null && token.length() >= 32; }
    public static boolean matches(String expected, String authorization) {
        return configured(expected) && authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.substring(7).getBytes(StandardCharsets.UTF_8));
    }
}
