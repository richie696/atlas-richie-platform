package cn.richie696.component.web.core.config.mvc;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import java.math.BigInteger;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class WebHttpMessageConvertersAutoConfigurationTest {

    @Test
    void serializesLongAndBigIntegerAsStringsThroughBoot4Customizer() throws Exception {
        var customizer = new WebHttpMessageConvertersAutoConfiguration()
                .webCoreMessageConvertersCustomizer();
        var builder = HttpMessageConverters.forServer();

        customizer.customize(builder);

        JacksonJsonHttpMessageConverter converter = StreamSupport.stream(builder.build().spliterator(), false)
                .filter(JacksonJsonHttpMessageConverter.class::isInstance)
                .map(JacksonJsonHttpMessageConverter.class::cast)
                .findFirst()
                .orElseThrow();
        String json = converter.getMapper().writeValueAsString(Map.of(
                "longId", 2252609688721604609L,
                "bigId", new BigInteger("2252609688721604609")));

        assertThat(json).contains("\"longId\":\"2252609688721604609\"");
        assertThat(json).contains("\"bigId\":\"2252609688721604609\"");
    }
}
