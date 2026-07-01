package com.richie.component.storage.local.config;

import com.richie.component.storage.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageAutoInitBranchTest {

    @Test
    void providerBeanMethod_shouldBeAnnotatedWithBean() throws NoSuchMethodException {
        Method method = LocalStorageAutoConfiguration.class.getMethod("localStorageEngineProvider");
        assertThat(method.getAnnotation(Bean.class)).isNotNull();
        assertThat(method.getReturnType().getSimpleName()).isEqualTo("StorageEngineProvider");
    }

    @Test
    void providerBeanMethod_shouldNotHaveConditionalOnProperty() throws NoSuchMethodException {
        Method method = LocalStorageAutoConfiguration.class.getMethod("localStorageEngineProvider");
        assertThat(method.getAnnotationsByType(ConditionalOnProperty.class)).isEmpty();
    }

    @Test
    void shouldHaveAutoConfigurationAnnotation() {
        assertThat(LocalStorageAutoConfiguration.class.getAnnotation(AutoConfiguration.class)).isNotNull();
    }

    @Test
    void localStorageAutoConfiguration_hasNoClientBeanMethod() {
        // 本地存储无独立客户端 Bean,仅依赖文件系统
        var clientBeanMethods = Arrays.stream(LocalStorageAutoConfiguration.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .filter(m -> m.getAnnotation(ConditionalOnProperty.class) != null)
                .toList();
        assertThat(clientBeanMethods).isEmpty();
    }
}
