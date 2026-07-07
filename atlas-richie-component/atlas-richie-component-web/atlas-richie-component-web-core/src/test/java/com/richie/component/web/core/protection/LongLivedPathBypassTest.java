package com.richie.component.web.core.protection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongLivedPathBypassTest {

    @Test
    void matches_ssePath() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(
                List.of("/ws/**", "/sse/**", "/stream/**"));
        assertThat(bypass.matches("/sse/events")).isTrue();
        assertThat(bypass.matches("/ws/chat")).isTrue();
        assertThat(bypass.matches("/stream/logs")).isTrue();
    }

    @Test
    void matches_nestedPaths() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/sse/**"));
        assertThat(bypass.matches("/sse/api/v1/events/123")).isTrue();
    }

    @Test
    void doesNotMatch_nonListedPath() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/sse/**"));
        assertThat(bypass.matches("/api/v1/users")).isFalse();
        assertThat(bypass.matches("/rest/sse-prefix-mimic")).isFalse();
    }

    @Test
    void emptyPatterns_neverMatches() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of());
        assertThat(bypass.matches("/sse/events")).isFalse();
        assertThat(bypass.matches("/")).isFalse();
    }

    @Test
    void nullPath_doesNotMatch() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/**"));
        assertThat(bypass.matches(null)).isFalse();
    }

    @Test
    void patterns_returnsImmutableList() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/sse/**"));
        assertThat(bypass.patterns()).containsExactly("/sse/**");
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new LongLivedPathBypass(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exactMatch_withoutWildcard() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/health"));
        assertThat(bypass.matches("/health")).isTrue();
        assertThat(bypass.matches("/health/")).isFalse();
    }
}