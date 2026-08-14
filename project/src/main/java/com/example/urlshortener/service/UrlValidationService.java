package com.example.urlshortener.service;

import com.example.urlshortener.config.AliasProperties;
import com.example.urlshortener.exception.InvalidUrlException;
import org.springframework.stereotype.Service;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates candidate long URLs and custom aliases (URL-107).
 * Blocks non-http(s) schemes and resolves the host to reject requests targeting
 * loopback/link-local/site-local/multicast addresses, which is the standard mitigation
 * for SSRF via a URL-shortener redirect/preview path.
 */
@Service
public class UrlValidationService {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,20}$");

    private final AliasProperties aliasProperties;

    public UrlValidationService(AliasProperties aliasProperties) {
        this.aliasProperties = aliasProperties;
    }

    public void validateLongUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("longUrl must not be blank");
        }
        if (rawUrl.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("longUrl exceeds max length of " + MAX_URL_LENGTH);
        }

        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("longUrl is not a well-formed URI");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("longUrl scheme must be http or https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("longUrl must include a host");
        }

        assertHostIsPublic(host);
    }

    public void validateAlias(String alias) {
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidUrlException(
                    "customAlias must be 3-20 chars of letters, digits, '-' or '_'");
        }
        if (aliasProperties.reservedWords().stream().anyMatch(w -> w.equalsIgnoreCase(alias))) {
            throw new InvalidUrlException("customAlias '" + alias + "' is reserved");
        }
    }

    private void assertHostIsPublic(String host) {
        String asciiHost = IDN.toASCII(host);
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(asciiHost);
        } catch (UnknownHostException e) {
            throw new InvalidUrlException("longUrl host could not be resolved");
        }

        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || address.isAnyLocalAddress()) {
                throw new InvalidUrlException(
                        "longUrl resolves to a private/internal address and is not allowed");
            }
        }
    }
}
