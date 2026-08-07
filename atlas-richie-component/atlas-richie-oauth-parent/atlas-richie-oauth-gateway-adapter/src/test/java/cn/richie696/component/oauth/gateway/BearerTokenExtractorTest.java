package cn.richie696.component.oauth.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenExtractorTest {

    @Test
    void extractsOnlySingleBearerValue() {
        assertThat(BearerTokenExtractor.extract("Bearer abc.def")).isEqualTo("abc.def");
        assertThat(BearerTokenExtractor.extract("bearer abc")).isEqualTo("abc");
        assertThat(BearerTokenExtractor.extract("Basic abc")).isNull();
        assertThat(BearerTokenExtractor.extract("Bearer a b")).isNull();
        assertThat(BearerTokenExtractor.extract(null)).isNull();
    }
}
