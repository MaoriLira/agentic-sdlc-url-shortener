package com.example.urlshortener.api;

import com.example.urlshortener.api.dto.ProblemResponse;
import com.example.urlshortener.exception.AliasConflictException;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.RateLimitExceededException;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Every handled exception logs at WARN (client errors — expected, not actionable on their
 * own) via the shared {@link #problem} helper; genuinely unexpected exceptions fall to
 * {@link #handleUnexpected}, logged at ERROR. Before URL-601, an unhandled exception produced
 * no application-level log line at all — it just fell through to Spring Boot's default error
 * response. Some handlers (auth failures, alias conflicts, rate limits, internal-target
 * rejections) also carry a richer domain-specific WARN logged upstream at the throw site
 * (see {@code ApiKeyAuthService}, {@code UrlShortenerService}, {@code RateLimiterService},
 * {@code UrlValidationService}) — that's intentional, not duplicate noise: the upstream log
 * has diagnostic context this handler doesn't (masked client IP, rejected host, etc.), and
 * this handler's log is the uniform "this endpoint returned an error" audit trail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ProblemResponse> handleInvalidUrl(InvalidUrlException e, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemResponse> handleValidation(MethodArgumentNotValidException e, WebRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail, request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemResponse> handleUnauthorized(UnauthorizedException e, WebRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage(), request);
    }

    @ExceptionHandler(ShortUrlNotFoundException.class)
    public ResponseEntity<ProblemResponse> handleNotFound(ShortUrlNotFoundException e, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage(), request);
    }

    @ExceptionHandler(ShortUrlGoneException.class)
    public ResponseEntity<ProblemResponse> handleGone(ShortUrlGoneException e, WebRequest request) {
        return problem(HttpStatus.GONE, "Gone", e.getMessage(), request);
    }

    @ExceptionHandler(AliasConflictException.class)
    public ResponseEntity<ProblemResponse> handleConflict(AliasConflictException e, WebRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemResponse> handleRateLimit(RateLimitExceededException e, WebRequest request) {
        String instance = request.getDescription(false).replace("uri=", "");
        log.warn("{} {} -> 429 Too Many Requests: {}", HttpStatus.TOO_MANY_REQUESTS.value(), instance, e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", e.getMessage(), instance));
    }

    /**
     * Catch-all for anything not covered by a specific handler above. Previously, such an
     * exception fell through to Spring Boot's default error handling with no application log
     * line — a genuine observability gap, closed here.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemResponse> handleUnexpected(Exception e, WebRequest request) {
        String instance = request.getDescription(false).replace("uri=", "");
        log.error("Unhandled exception on {}", instance, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error", "An unexpected error occurred", instance));
    }

    private ResponseEntity<ProblemResponse> problem(HttpStatus status, String title, String detail, WebRequest request) {
        String instance = request.getDescription(false).replace("uri=", "");
        log.warn("{} {} -> {} {}: {}", status.value(), instance, status.value(), title, detail);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponse.of(status.value(), title, detail, instance));
    }
}
