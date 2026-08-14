package com.example.urlshortener.service;

import com.example.urlshortener.config.AliasProperties;
import com.example.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidationServiceTest {

    private UrlValidationService service;

    @BeforeEach
    void setUp() {
        service = new UrlValidationService(new AliasProperties(List.of("api", "admin", "urls")));
    }

    @Test
    void acceptsWellFormedHttpsUrl() {
        assertThatCode(() -> service.validateLongUrl("https://example.com/page?q=1"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> service.validateLongUrl("javascript:alert(1)"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsFtpScheme() {
        assertThatThrownBy(() -> service.validateLongUrl("ftp://example.com/file"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsLoopbackHost() {
        assertThatThrownBy(() -> service.validateLongUrl("http://127.0.0.1/admin"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsLinkLocalCloudMetadataHost() {
        assertThatThrownBy(() -> service.validateLongUrl("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> service.validateLongUrl("  "))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlOverMaxLength() {
        String longUrl = "https://example.com/" + "a".repeat(2048);
        assertThatThrownBy(() -> service.validateLongUrl(longUrl))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void acceptsValidAlias() {
        assertThatCode(() -> service.validateAlias("my-campaign_1"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsReservedAlias() {
        assertThatThrownBy(() -> service.validateAlias("admin"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsAliasWithIllegalCharacters() {
        assertThatThrownBy(() -> service.validateAlias("has space"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsAliasTooShort() {
        assertThatThrownBy(() -> service.validateAlias("ab"))
                .isInstanceOf(InvalidUrlException.class);
    }
}
