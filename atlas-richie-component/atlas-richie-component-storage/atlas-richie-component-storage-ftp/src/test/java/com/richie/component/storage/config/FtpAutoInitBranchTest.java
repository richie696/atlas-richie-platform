package com.richie.component.storage.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FtpAutoInitBranchTest {

    @Test
    void shouldHaveConfigurationAnnotation() {
        assertThat(FtpAutoConfiguration.class.getAnnotation(Configuration.class)).isNotNull();
    }

    @Test
    void providerBeanMethod_shouldBeAnnotatedWithBean() throws NoSuchMethodException {
        Method method = FtpAutoConfiguration.class.getMethod("ftpStorageEngineProvider");
        assertThat(method.getAnnotation(Bean.class)).isNotNull();
        assertThat(method.getReturnType().getSimpleName()).isEqualTo("StorageEngineProvider");
    }

    @Test
    void providerBeanMethod_shouldNotHaveConditionalOnProperty() throws NoSuchMethodException {
        Method method = FtpAutoConfiguration.class.getMethod("ftpStorageEngineProvider");
        assertThat(method.getAnnotationsByType(ConditionalOnProperty.class)).isEmpty();
    }

    @Test
    void clientBeanMethod_shouldBeConditionalOnFtpEnableTrue() throws NoSuchMethodException {
        Method method = FtpAutoConfiguration.class.getMethod("ftpClientPool", StorageProperties.class);
        var conditions = Arrays.stream(method.getAnnotationsByType(ConditionalOnProperty.class))
                .filter(c -> "platform.component.storage.ftp".equals(c.prefix()))
                .filter(c -> Arrays.asList(c.name()).contains("enable"))
                .filter(c -> "true".equals(c.havingValue()))
                .findFirst();
        assertThat(conditions).isPresent();
    }

    @Test
    void clientBeanMethod_shouldBeConditionalOnAutoInitTrue() throws NoSuchMethodException {
        Method method = FtpAutoConfiguration.class.getMethod("ftpClientPool", StorageProperties.class);
        var conditions = Arrays.stream(method.getAnnotationsByType(ConditionalOnProperty.class))
                .filter(c -> "platform.component.storage".equals(c.prefix()))
                .filter(c -> Arrays.asList(c.name()).contains("auto-init"))
                .filter(c -> "true".equals(c.havingValue()))
                .findFirst();
        assertThat(conditions).isPresent();
        assertThat(conditions.get().matchIfMissing()).isTrue();
    }
}
