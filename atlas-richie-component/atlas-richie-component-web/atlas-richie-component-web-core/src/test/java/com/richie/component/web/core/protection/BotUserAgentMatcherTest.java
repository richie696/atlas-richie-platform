package com.richie.component.web.core.protection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BotUserAgentMatcherTest {

    @Test
    void exactMatch_returnsTrue() {
        BotUserAgentMatcher m = new BotUserAgentMatcher("curl/7.85.0");
        assertThat(m.matches("curl/7.85.0")).isTrue();
        assertThat(m.matches("Mozilla/5.0")).isFalse();
    }

    @Test
    void starWildcard_matchesAny() {
        BotUserAgentMatcher m = new BotUserAgentMatcher("curl/*");
        assertThat(m.matches("curl/7.85.0")).isTrue();
        assertThat(m.matches("curl/8.0.1")).isTrue();
        assertThat(m.matches("wget/1.21")).isFalse();
    }

    @Test
    void questionMark_matchesSingleChar() {
        BotUserAgentMatcher m = new BotUserAgentMatcher("curl/?.85.0");
        assertThat(m.matches("curl/7.85.0")).isTrue();
        assertThat(m.matches("curl/8.85.0")).isTrue();
        assertThat(m.matches("curl/10.85.0")).isFalse();
    }

    @Test
    void isCaseInsensitive() {
        BotUserAgentMatcher m = new BotUserAgentMatcher("CURL/*");
        assertThat(m.matches("curl/7.85.0")).isTrue();
        assertThat(m.matches("CURL/8.0.1")).isTrue();
    }

    @Test
    void regexSpecialChars_areEscaped() {
        BotUserAgentMatcher m = new BotUserAgentMatcher("bad.bot+()");
        assertThat(m.matches("bad.bot+()")).isTrue();
        assertThat(m.matches("badbot()")).isFalse();
    }

    @Test
    void pattern_returnsOriginal() {
        BotUserAgentMatcher m = new BotUserAgentMatcher("curl/*");
        assertThat(m.pattern()).isEqualTo("curl/*");
    }

    @Test
    void nullInput_isFalse() {
        BotUserAgentMatcher m = new BotUserAgentMatcher("curl/*");
        assertThat(m.matches(null)).isFalse();
    }

    @Test
    void blankPattern_compiles() {
        BotUserAgentMatcher m = new BotUserAgentMatcher("");
        assertThat(m.matches("anything")).isTrue();
        assertThat(m.matches("")).isTrue();
    }

    @Test
    void globToRegex_convertsCorrectly() {
        assertThat(BotUserAgentMatcher.globToRegex("curl/*")).isEqualTo("\\Qcurl\\E.*");
        assertThat(BotUserAgentMatcher.globToRegex("curl/?.85.0")).isEqualTo("\\Qcurl/\\E.\\Q.85.0\\E");
        assertThat(BotUserAgentMatcher.globToRegex("a.b+c")).isEqualTo("\\Qa.b+c\\E");
    }

    @Test
    void nullPattern_throws() {
        assertThatThrownBy(() -> new BotUserAgentMatcher(null))
                .isInstanceOf(NullPointerException.class);
    }
}