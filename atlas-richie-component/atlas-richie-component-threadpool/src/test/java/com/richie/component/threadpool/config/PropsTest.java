package com.richie.component.threadpool.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for configuration properties classes.
 */
class PropsTest {

    @Test
    void tomcatProps_shouldExtendTpExecutorProps() {
        TomcatProps props = new TomcatProps();
        assertThat(props).isNotNull();
    }

    @Test
    void jettyProps_shouldExtendTpExecutorProps() {
        JettyProps props = new JettyProps();
        assertThat(props).isNotNull();
    }

    @Test
    void undertowProps_shouldExtendTpExecutorProps() {
        UndertowProps props = new UndertowProps();
        assertThat(props).isNotNull();
    }

    @Test
    void zookeeperProps_shouldExtendDtpPropertiesZookeeper() {
        ZookeeperProps props = new ZookeeperProps();
        assertThat(props).isNotNull();
    }

    @Test
    void etcdProps_shouldExtendDtpPropertiesEtcd() {
        EtcdProps props = new EtcdProps();
        assertThat(props).isNotNull();
    }
}
