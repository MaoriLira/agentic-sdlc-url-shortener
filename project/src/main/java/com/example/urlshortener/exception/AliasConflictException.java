package com.example.urlshortener.exception;

public class AliasConflictException extends RuntimeException {

    public AliasConflictException(String alias) {
        super("Alias already taken: " + alias);
    }
}
