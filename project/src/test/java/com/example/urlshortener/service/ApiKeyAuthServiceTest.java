package com.example.urlshortener.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.urlshortener.domain.ApiClient;
import com.example.urlshortener.domain.ClientStatus;
import com.example.urlshortener.exception.UnauthorizedException;
import com.example.urlshortener.repository.ApiClientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies URL-601's data-masking policy: the raw API key must never appear in a log line
 * from {@link ApiKeyAuthService}, on any path. Attaches a real Logback {@link ListAppender}
 * to the class's actual logger rather than mocking SLF4J, so this catches a real log
 * statement leaking the key, not just an intent.
 */
class ApiKeyAuthServiceTest {

    private static final String RAW_API_KEY = "super-secret-demo-key-must-never-appear-in-logs";

    private ApiClientRepository apiClientRepository;
    private ApiKeyAuthService authService;
    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        apiClientRepository = mock(ApiClientRepository.class);
        authService = new ApiKeyAuthService(apiClientRepository);

        logbackLogger = (Logger) LoggerFactory.getLogger(ApiKeyAuthService.class);
        // This test doesn't boot a Spring context, so logback-spring.xml (which sets the
        // production DEBUG/INFO split) never applies here. Pin the level explicitly rather
        // than depend on whatever ambient Logback config happens to be on the test classpath.
        logbackLogger.setLevel(Level.DEBUG);
        logAppender = new ListAppender<>();
        logAppender.start();
        logbackLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(logAppender);
        logbackLogger.setLevel(null);
    }

    @Test
    void successfulAuthenticationLogsButNeverTheRawKey() {
        ApiClient client = activeClient();
        when(apiClientRepository.findByApiKeyHash(anyString())).thenReturn(Optional.of(client));

        ApiClient result = authService.authenticate(RAW_API_KEY);

        assertThat(result).isEqualTo(client);
        assertThat(logAppender.list).isNotEmpty(); // proves the success path does log something
        assertNoEventContainsRawKey();
    }

    @Test
    void missingKeyIsRejectedAndLoggedWithoutTheKey() {
        assertThatThrownBy(() -> authService.authenticate(null))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(logAppender.list).isNotEmpty();
        assertNoEventContainsRawKey();
    }

    @Test
    void unknownKeyIsRejectedAndLoggedWithoutTheKey() {
        when(apiClientRepository.findByApiKeyHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate(RAW_API_KEY))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(logAppender.list).isNotEmpty();
        assertNoEventContainsRawKey();
    }

    @Test
    void suspendedClientIsRejectedAndLoggedWithoutTheKey() {
        ApiClient client = clientWithStatus(ClientStatus.SUSPENDED);
        when(apiClientRepository.findByApiKeyHash(anyString())).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> authService.authenticate(RAW_API_KEY))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(logAppender.list).isNotEmpty();
        assertNoEventContainsRawKey();
    }

    private void assertNoEventContainsRawKey() {
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(RAW_API_KEY);
        }
    }

    private ApiClient activeClient() {
        return clientWithStatus(ClientStatus.ACTIVE);
    }

    // URL-701: builder instead of new ApiClient() + a chain of setters.
    private ApiClient clientWithStatus(ClientStatus status) {
        return ApiClient.builder()
                .name("Test Client")
                .apiKeyHash("irrelevant-hash-value")
                .status(status)
                .build();
    }
}
