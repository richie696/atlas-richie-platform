/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretResolver;
import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.api.SecretSnapshotListener;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.EnvelopeCrypto;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SecretCipher;
import cn.richie696.component.secret.api.crypto.SigningBackend;
import cn.richie696.component.secret.api.crypto.SigningService;
import cn.richie696.component.secret.core.DefaultSecretResolver;
import cn.richie696.component.secret.core.DefaultSecretOperations;
import cn.richie696.component.secret.core.SecretSnapshotManager;
import cn.richie696.component.secret.core.crypto.ArseEnvelopeCodec;
import cn.richie696.component.secret.core.crypto.DefaultEnvelopeCrypto;
import cn.richie696.component.secret.core.crypto.DefaultSecretCipher;
import cn.richie696.component.secret.core.crypto.DefaultSigningService;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.bootstrap.refresh.SecretRefreshParticipant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Secret 运行期核心自动配置。默认关闭时不会创建任何 Bean。
 */
@AutoConfiguration
@ConditionalOnClass(SecretResolver.class)
@ConditionalOnProperty(
        prefix = "platform.component.secret",
        name = "enabled",
        havingValue = "true")
public class SecretRuntimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecretRefreshDiagnostics secretRefreshDiagnostics() {
        return new SecretRefreshDiagnostics();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecretSnapshotManager secretSnapshotManager(
            ObjectProvider<SecretSnapshotListener> listeners,
            ApplicationEventPublisher eventPublisher,
            SecretRefreshDiagnostics diagnostics) {
        SecretSnapshotManager manager = new SecretSnapshotManager(diagnostics::recordListenerFailure);
        listeners.orderedStream().forEach(manager::addListener);
        manager.addListener(eventPublisher::publishEvent);
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    public ArseEnvelopeCodec arseEnvelopeCodec() {
        return new ArseEnvelopeCodec();
    }

    @Bean
    @ConditionalOnBean(SecretBackend.class)
    @ConditionalOnMissingBean(SecretResolver.class)
    public SecretResolver secretResolver(SecretBackend secretBackend) {
        return new DefaultSecretResolver(secretBackend);
    }

    @Bean
    @ConditionalOnBean(KeyWrappingBackend.class)
    @ConditionalOnMissingBean(EnvelopeCrypto.class)
    public EnvelopeCrypto envelopeCrypto(KeyWrappingBackend keyWrappingBackend) {
        return new DefaultEnvelopeCrypto(keyWrappingBackend);
    }

    @Bean
    @ConditionalOnBean(EnvelopeCrypto.class)
    @ConditionalOnMissingBean(SecretCipher.class)
    public SecretCipher secretCipher(
            EnvelopeCrypto envelopeCrypto,
            ArseEnvelopeCodec envelopeCodec,
            Environment environment) {
        return new DefaultSecretCipher(
                envelopeCrypto,
                envelopeCodec,
                () -> defaultCryptoContext(environment));
    }

    @Bean
    @ConditionalOnBean(SigningBackend.class)
    @ConditionalOnMissingBean(SigningService.class)
    public SigningService signingService(SigningBackend signingBackend) {
        return new DefaultSigningService(signingBackend);
    }

    @Bean
    @ConditionalOnBean({SecretResolver.class, SecretCipher.class})
    @ConditionalOnMissingBean(SecretOperations.class)
    public SecretOperations secretOperations(
            SecretResolver secretResolver,
            SecretCipher secretCipher,
            ObjectProvider<SigningService> signingServices) {
        return new DefaultSecretOperations(
                secretResolver, secretCipher, signingServices.getIfAvailable());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(SecretBootstrapState.class)
    @ConditionalOnMissingBean
    public SecretPropertySourceRefresher secretPropertySourceRefresher(
            SecretBootstrapState bootstrapState,
            ConfigurableEnvironment environment,
            ObjectProvider<SecretRefreshParticipant> participants,
            ApplicationEventPublisher eventPublisher,
            SecretRefreshDiagnostics diagnostics) {
        return new SecretPropertySourceRefresher(
                bootstrapState, environment, participants, eventPublisher, diagnostics);
    }

    private CryptoContext defaultCryptoContext(Environment environment) {
        String application = environment.getProperty("spring.application.name", "application");
        String deploymentEnvironment = environment.getProperty(
                "platform.component.secret.property-source.environment",
                "dev");
        String canonical = "application=" + application + "\nenvironment=" + deploymentEnvironment;
        return new CryptoContext(
                canonical.getBytes(StandardCharsets.UTF_8),
                Map.of("schema", "atlas-bootstrap-v1"));
    }
}
