package com.example.urlshortener.api;

import com.example.urlshortener.api.dto.ProblemResponse;
import com.example.urlshortener.exception.AliasConflictException;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.RateLimitExceededException;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.exception.UnauthorizedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", e.getMessage(), instance));
    }

    private ResponseEntity<ProblemResponse> problem(HttpStatus status, String title, String detail, WebRequest request) {
        String instance = request.getDescription(false).replace("uri=", "");
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponse.of(status.value(), title, detail, instance));
    }
}
