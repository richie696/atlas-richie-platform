package com.richie.component.web.jetty;

import com.richie.component.web.jetty.config.JettyProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JettyPropertiesTest {

    @Test
    void defaults_loadCorrectly() {
        JettyProperties props = new JettyProperties();
        assertThat(props.getAccessLog().getDirectory()).isEqualTo("/var/log/richie/jetty");
        assertThat(props.getAccessLog().getFilenamePrefix()).isEqualTo("access");
        assertThat(props.getTraceId().getHeader()).isEqualTo("X-Trace-Id");
        assertThat(props.getMetrics().getPrefix()).isEqualTo("atlas_jetty");
    }

    @Test
    void mutable_setters() {
        JettyProperties props = new JettyProperties();
        props.getAccessLog().setEnabled(true);
        props.getAccessLog().setDirectory("/custom/path");
        assertThat(props.getAccessLog().isEnabled()).isTrue();
        assertThat(props.getAccessLog().getDirectory()).isEqualTo("/custom/path");
    }

    @Test
    void traceIdDefaults_areReasonable() {
        JettyProperties props = new JettyProperties();
        assertThat(props.getTraceId().isEnabled()).isTrue();
        assertThat(props.getTraceId().isGenerateIfMissing()).isTrue();
    }

    @Test
    void metricsPrefix_isCustomizable() {
        JettyProperties props = new JettyProperties();
        props.getMetrics().setPrefix("custom_prefix");
        assertThat(props.getMetrics().getPrefix()).isEqualTo("custom_prefix");
    }
}