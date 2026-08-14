package com.example.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void masksLastOctetOfIpv4Address() {
        assertThat(LogSanitizer.maskIp("203.0.113.42")).isEqualTo("203.0.113.xxx");
    }

    @Test
    void maskedValueDoesNotContainTheOriginalLastOctet() {
        String masked = LogSanitizer.maskIp("192.168.1.77");
        assertThat(masked).doesNotContain("77");
    }

    @Test
    void leavesNonIpv4InputUnchanged() {
        assertThat(LogSanitizer.maskIp("not-an-ip")).isEqualTo("not-an-ip");
        assertThat(LogSanitizer.maskIp("2001:db8::1")).isEqualTo("2001:db8::1");
    }

    @Test
    void handlesNullSafely() {
        assertThat(LogSanitizer.maskIp(null)).isNull();
    }
}
