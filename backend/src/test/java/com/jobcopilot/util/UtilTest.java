package com.jobcopilot.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonListUtil} and {@link HashUtil}.
 */
class UtilTest {

    @Test
    void serializesAndParsesList() {
        List<String> input = List.of("Java", "Spring", "AWS");
        String json = JsonListUtil.toJson(input);
        assertThat(JsonListUtil.fromJson(json)).containsExactlyElementsOf(input);
    }

    @Test
    void emptyAndNullBecomeEmptyList() {
        assertThat(JsonListUtil.fromJson(null)).isEmpty();
        assertThat(JsonListUtil.fromJson("")).isEmpty();
        assertThat(JsonListUtil.toJson(null)).isEqualTo("[]");
    }

    @Test
    void toleratesLegacyCsv() {
        assertThat(JsonListUtil.fromJson("Java, Spring , AWS"))
                .containsExactly("Java", "Spring", "AWS");
    }

    @Test
    void fingerprintIsStableAndNormalized() {
        String a = HashUtil.fingerprint("Senior Engineer", "GoodCorp", "Berlin");
        String b = HashUtil.fingerprint("  senior engineer ", "goodcorp", "BERLIN");
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void differentContentYieldsDifferentFingerprint() {
        String a = HashUtil.fingerprint("Engineer", "A", "X");
        String b = HashUtil.fingerprint("Engineer", "B", "X");
        assertThat(a).isNotEqualTo(b);
    }
}
