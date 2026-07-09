package com.jobcopilot.security;

import com.jobcopilot.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenProvider}: token generation, claim extraction, type separation,
 * and validation of tampered/foreign tokens.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getSecurity().getJwt().setSecret("unit-test-secret-key-at-least-32-bytes-long-yes");
        props.getSecurity().getJwt().setIssuer("job-search-copilot-test");
        provider = new JwtTokenProvider(props);
    }

    @Test
    void generatesAndValidatesAccessToken() {
        String token = provider.generateAccessToken("admin", "ADMIN");

        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.isAccessToken(token)).isTrue();
        assertThat(provider.isRefreshToken(token)).isFalse();
        assertThat(provider.extractUsername(token)).isEqualTo("admin");
        assertThat(provider.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void refreshTokenIsDistinguishedFromAccess() {
        String refresh = provider.generateRefreshToken("admin", "ADMIN");

        assertThat(provider.isRefreshToken(refresh)).isTrue();
        assertThat(provider.isAccessToken(refresh)).isFalse();
    }

    @Test
    void rejectsTamperedToken() {
        String token = provider.generateAccessToken("admin", "ADMIN");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(provider.isValid(tampered)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        AppProperties other = new AppProperties();
        other.getSecurity().getJwt().setSecret("a-completely-different-secret-key-32-bytes-!!");
        JwtTokenProvider foreign = new JwtTokenProvider(other);
        String foreignToken = foreign.generateAccessToken("admin", "ADMIN");

        assertThat(provider.isValid(foreignToken)).isFalse();
    }

    @Test
    void rejectsTooShortSecret() {
        AppProperties props = new AppProperties();
        props.getSecurity().getJwt().setSecret("too-short");

        assertThatThrownBy(() -> new JwtTokenProvider(props))
                .isInstanceOf(IllegalStateException.class);
    }
}
