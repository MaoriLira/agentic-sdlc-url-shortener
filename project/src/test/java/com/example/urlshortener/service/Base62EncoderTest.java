package com.example.urlshortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    @Test
    void encodesZeroAsFirstAlphabetChar() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("0");
    }

    @Test
    void encodingIsStableAndRoundTrippableInLength() {
        // sequence-start value used in V3__short_code_sequence.sql: 62^6, first value that
        // yields a 7-character code
        assertThat(Base62Encoder.encode(56800235584L)).hasSize(7);
    }

    @Test
    void consecutiveValuesProduceDistinctCodes() {
        String a = Base62Encoder.encode(56800235584L);
        String b = Base62Encoder.encode(56800235585L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
