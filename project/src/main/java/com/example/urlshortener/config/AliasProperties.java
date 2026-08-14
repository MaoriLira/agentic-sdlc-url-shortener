package com.example.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "urlshortener.alias")
public record AliasProperties(List<String> reservedWords) {
}
