package cn.richie696.component.oauth.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DpopTokenExtractorTest {

    @Test
    void extractsDpopAuthorizationSchemeOnly() {
        assertThat(DpopTokenExtractor.extract("DPoP access-token")).isEqualTo("access-token");
        assertThat(DpopTokenExtractor.extract("Bearer access-token")).isNull();
        assertThat(DpopTokenExtractor.extract("DPoP access token")).isNull();
    }
}
