package com.richie.component.web.i18n.tomcat;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptLanguageTest {

    @Test
    void parse_singleLanguageTag() throws Exception {
        List<AcceptLanguage> parsed = AcceptLanguage.parse(new StringReader("zh-CN"));

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().getLocale().toLanguageTag()).isEqualTo("zh-CN");
        assertThat(parsed.getFirst().getQuality()).isEqualTo(1.0);
    }

    @Test
    void parse_multipleTagsWithQuality() throws Exception {
        List<AcceptLanguage> parsed = AcceptLanguage.parse(new StringReader("en-US,en;q=0.9,zh;q=0.8"));

        assertThat(parsed).hasSize(3);
        assertThat(parsed.get(0).getLocale().getLanguage()).isEqualTo("en");
        assertThat(parsed.get(1).getQuality()).isEqualTo(0.9);
        assertThat(parsed.get(2).getQuality()).isEqualTo(0.8);
    }

    @Test
    void parse_skipsZeroQuality() throws Exception {
        List<AcceptLanguage> parsed = AcceptLanguage.parse(new StringReader("en;q=0,fr"));

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().getLocale().getLanguage()).isEqualTo("fr");
    }
}
