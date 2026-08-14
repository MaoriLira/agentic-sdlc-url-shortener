package com.example.urlshortener;

import com.example.urlshortener.api.dto.CreateUrlRequest;
import com.example.urlshortener.api.dto.StatsResponse;
import com.example.urlshortener.api.dto.UrlResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UrlShortenerIntegrationTest {

    private static final String DEMO_API_KEY = "demo-key-12345";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("urlshortener")
                    .withUsername("urlshortener")
                    .withPassword("urlshortener");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @LocalServerPort
    int port;

    // httpclient5 is on the test classpath so TestRestTemplate auto-selects
    // HttpComponentsClientHttpRequestFactory, avoiding a JDK HttpURLConnection quirk where
    // a POST with a body throws instead of surfacing a non-2xx (e.g. 401) response.
    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", DEMO_API_KEY);
        return headers;
    }

    @Test
    void fullLifecycle_createRedirectStatsDelete() {
        CreateUrlRequest createRequest = new CreateUrlRequest("https://example.com/agentic-sdlc", null, null);
        ResponseEntity<UrlResponse> createResponse = restTemplate.postForEntity(
                baseUrl() + "/api/v1/urls",
                new HttpEntity<>(createRequest, authHeaders()),
                UrlResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String shortCode = createResponse.getBody().shortCode();
        assertThat(shortCode).hasSizeGreaterThanOrEqualTo(7);

        // GET metadata
        ResponseEntity<UrlResponse> metadataResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/urls/" + shortCode,
                HttpMethod.GET, new HttpEntity<>(authHeaders()), UrlResponse.class);
        assertThat(metadataResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metadataResponse.getBody().longUrl()).isEqualTo("https://example.com/agentic-sdlc");

        // Redirect (no auth) — TestRestTemplate does not follow redirects by default,
        // so we can assert on the raw 302 + Location header.
        ResponseEntity<Void> redirectResponse = restTemplate.exchange(
                baseUrl() + "/" + shortCode, HttpMethod.GET, HttpEntity.EMPTY, Void.class);
        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation().toString())
                .isEqualTo("https://example.com/agentic-sdlc");

        // Async analytics: stats should eventually show the click
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<StatsResponse> statsResponse = restTemplate.exchange(
                    baseUrl() + "/api/v1/urls/" + shortCode + "/stats",
                    HttpMethod.GET, new HttpEntity<>(authHeaders()), StatsResponse.class);
            assertThat(statsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(statsResponse.getBody().totalClicks()).isEqualTo(1L);
        });

        // Delete
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/urls/" + shortCode,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Redirect after delete -> 410 Gone
        ResponseEntity<String> goneResponse = restTemplate.getForEntity(baseUrl() + "/" + shortCode, String.class);
        assertThat(goneResponse.getStatusCode()).isEqualTo(HttpStatus.GONE);

        // Metadata after delete -> 404 Not Found
        ResponseEntity<String> notFoundResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/urls/" + shortCode,
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void redirectingUnknownShortCode_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/does-not-exist", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createWithoutApiKey_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/x", null, null);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/urls", new HttpEntity<>(request, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createWithSsrfTargetingUrl_returns400() {
        CreateUrlRequest request = new CreateUrlRequest("http://127.0.0.1:8080/secret", null, null);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/urls", new HttpEntity<>(request, authHeaders()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWithDuplicateCustomAlias_returns409() {
        String alias = "d" + Long.toString(System.nanoTime(), 36);
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/first", alias, null);
        ResponseEntity<UrlResponse> first = restTemplate.postForEntity(
                baseUrl() + "/api/v1/urls", new HttpEntity<>(request, authHeaders()), UrlResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        CreateUrlRequest duplicate = new CreateUrlRequest("https://example.com/second", alias, null);
        ResponseEntity<String> second = restTemplate.postForEntity(
                baseUrl() + "/api/v1/urls", new HttpEntity<>(duplicate, authHeaders()), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deletedCustomAlias_canBeReused() {
        // URL-501 (R-3 fix): the unique index is now scoped to ACTIVE rows only, so a
        // deleted alias must become available again — this was impossible before V5.
        String alias = "r" + Long.toString(System.nanoTime(), 36);

        CreateUrlRequest first = new CreateUrlRequest("https://example.com/first-use", alias, null);
        ResponseEntity<UrlResponse> firstResponse = restTemplate.postForEntity(
                baseUrl() + "/api/v1/urls", new HttpEntity<>(first, authHeaders()), UrlResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/urls/" + alias, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        CreateUrlRequest second = new CreateUrlRequest("https://example.com/second-use", alias, null);
        ResponseEntity<UrlResponse> secondResponse = restTemplate.postForEntity(
                baseUrl() + "/api/v1/urls", new HttpEntity<>(second, authHeaders()), UrlResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResponse.getBody().shortCode()).isEqualTo(alias);
        assertThat(secondResponse.getBody().longUrl()).isEqualTo("https://example.com/second-use");
    }
}
