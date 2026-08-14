package com.example.urlshortener.api.dto;

public record ProblemResponse(String type, String title, int status, String detail, String instance) {

    public static ProblemResponse of(int status, String title, String detail, String instance) {
        return new ProblemResponse("about:blank", title, status, detail, instance);
    }
}
