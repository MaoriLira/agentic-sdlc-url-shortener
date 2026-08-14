package com.example.urlshortener.exception;

public class ShortUrlGoneException extends RuntimeException {

    public ShortUrlGoneException(String shortCode) {
        super("Short URL has expired or was deleted: " + shortCode);
    }
}
