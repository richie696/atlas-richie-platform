package com.richie.component.storage.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SmbAutoInitBranchTest {

    @Test
    void shouldHaveConfigurationAnnotation() {
        assertThat(SmbAutoConfiguration.class.getAnnotation(Configuration.class)).isNotNull();
    }

    @Test
    void providerBeanMethod_shouldBeAnnotatedWithBean() throws NoSuchMethodException {
        Method method = SmbAutoConfiguration.class.getMethod("smbStorageEngineProvider");
        assertThat(method.getAnnotation(Bean.class)).isNotNull();
        assertThat(method.getReturnType().getSimpleName()).isEqualTo("StorageEngineProvider");
    }

    @Test
    void providerBeanMethod_shouldNotHaveConditionalOnProperty() throws NoSuchMethodException {
        Method method = SmbAutoConfiguration.class.getMethod("smbStorageEngineProvider");
        assertThat(method.getAnnotationsByType(ConditionalOnProperty.class)).isEmpty();
    }

    @Test
    void clientBeanMethod_shouldBeConditionalOnSmbEnableTrue() throws NoSuchMethodException {
        Method method = SmbAutoConfiguration.class.getMethod("cifsContext", StorageProperties.class);
        var conditions = Arrays.stream(method.getAnnotationsByType(ConditionalOnProperty.class))
                .filter(c -> "platform.component.storage.smb3".equals(c.prefix()))
                .filter(c -> Arrays.asList(c.name()).contains("enable"))
                .filter(c -> "true".equals(c.havingValue()))
                .findFirst();
        assertThat(conditions).isPresent();
    }

    @Test
    void clientBeanMethod_shouldBeConditionalOnAutoInitTrue() throws NoSuchMethodException {
        Method method = SmbAutoConfiguration.class.getMethod("cifsContext", StorageProperties.class);
        var conditions = Arrays.stream(method.getAnnotationsByType(ConditionalOnProperty.class))
                .filter(c -> "platform.component.storage".equals(c.prefix()))
                .filter(c -> Arrays.asList(c.name()).contains("auto-init"))
                .filter(c -> "true".equals(c.havingValue()))
                .findFirst();
        assertThat(conditions).isPresent();
        assertThat(conditions.get().matchIfMissing()).isTrue();
    }
}
