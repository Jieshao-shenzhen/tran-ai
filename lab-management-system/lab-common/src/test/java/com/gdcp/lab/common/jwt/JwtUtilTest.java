package com.gdcp.lab.common.jwt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private static final String SECRET = "lab-secret-key-0123456789abcdef0123456789abcdef";

    @Test
    void generateAndParse_shouldRoundTripUserIdAndRole() {
        JwtUtil util = new JwtUtil(SECRET);
        String token = util.generate("1", "ADMIN");
        var claims = util.parse(token);
        assertEquals("1", claims.get("userId"));
        assertEquals("ADMIN", claims.get("role"));
    }

    @Test
    void parse_shouldRejectTemperedToken() {
        JwtUtil util = new JwtUtil(SECRET);
        String token = util.generate("1", "ADMIN") + "x";
        assertThrows(Exception.class, () -> util.parse(token));
    }
}