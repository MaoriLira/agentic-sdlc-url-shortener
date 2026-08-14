package com.example.urlshortener.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * URL-701: composite JPA ID class — exactly the value-object boilerplate (equals/hashCode,
 * constructors) Lombok is built for. No getters were exposed before Lombok and none are
 * added now; JPA reads these fields via reflection.
 */
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ClickDailyRollupId implements Serializable {

    private String shortCode;
    private LocalDate clickDate;
}
