/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.catalog.SecretBinding;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogSet;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在业务组件属性绑定前加载受控 Secret PropertySource。
 */
public class AtlasSecretEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String ENABLED_PROPERTY = BootstrapSecretProperties.PREFIX + ".enabled";
    static final String PROPERTY_SOURCE_PREFIX = "atlas-richie-secret";
    private static final int MAX_BUNDLE_SEGMENT_LENGTH = 128;
    private static final Set<String> FORBIDDEN_PROPERTIES = Set.of(
            "server.port",
            "spring.datasource.url",
            "platform.component.secret.enabled",
            "platform.component.oauth.enabled",
            "platform.gateway.authentication.mode",
            "management.endpoints.web.exposure.include");

    private final ConfigurableBootstrapContext bootstrapContext;
    private final SecretProviderDiscovery providerDiscovery;
    private final SecretBindingCatalogLoader catalogLoader;

    public AtlasSecretEnvironmentPostProcessor(ConfigurableBootstrapContext bootstrapContext) {
        this(bootstrapContext, new SecretProviderDiscovery(), new SecretBindingCatalogLoader());
    }

    AtlasSecretEnvironmentPostProcessor(
            ConfigurableBootstrapContext bootstrapContext,
            SecretProviderDiscovery providerDiscovery,
            SecretBindingCatalogLoader catalogLoader) {
        this.bootstrapContext = bootstrapContext;
        this.providerDiscovery = providerDiscovery;
        this.catalogLoader = catalogLoader;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, false)) {
            return;
        }

        BootstrapSecretProperties properties = bind(environment);
        validate(properties, environment);
        ClassLoader classLoader = resolveClassLoader(application);
        List<SecretBootstrapProviderFactory> factories = providerDiscovery.discover(classLoader, properties);
        SecretBootstrapProviderFactory factory = providerDiscovery.select(factories, properties);
        SecretBindingCatalogSet catalogs = catalogLoader.load(classLoader);
        SecretBootstrapRequest request = createRequest(properties, environment, catalogs);
        SecretBootstrapClient client = null;
        boolean registered = false;
        try {
            client = factory.create(
                    properties,
                    new SecretBootstrapContext(environment, classLoader));
            SecretBootstrapResult result = load(client, request);
            Map<String, Object> filtered = filterAndValidate(result.values(), catalogs, properties, environment);
            String propertySourceName = PROPERTY_SOURCE_PREFIX + "[" + result.providerId() + ":" + result.version() + "]";
            environment.getPropertySources().addFirst(new MapPropertySource(propertySourceName, filtered));
            bootstrapContext.registerIfAbsent(
                    SecretBootstrapState.class,
                    BootstrapRegistry.InstanceSupplier.of(
                            new SecretBootstrapState(
                                    properties,
                                    factory,
                                    client,
                                    result,
                                    request,
                                    catalogs,
                                    propertySourceName)));
            registered = true;
        } finally {
            if (!registered && client != null) {
                closeQuietly(client);
            }
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private BootstrapSecretProperties bind(ConfigurableEnvironment environment) {
        try {
            return Binder.get(environment)
                    .bind(BootstrapSecretProperties.PREFIX, BootstrapSecretProperties.class)
                    .orElseGet(BootstrapSecretProperties::new);
        } catch (BindException exception) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Secret bootstrap properties are invalid",
                    exception);
        }
    }

    private void validate(BootstrapSecretProperties properties, ConfigurableEnvironment environment) {
        if (!properties.isEnabled()) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Secret enabled state changed while binding bootstrap properties");
        }
        BootstrapSecretProperties.PropertySource source = properties.getPropertySource();
        if (source.getApplication() == null || source.getApplication().isBlank()) {
            source.setApplication(environment.getProperty("spring.application.name"));
        }
        if (source.getApplication() == null || source.getApplication().isBlank()) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "Secret property-source.application or spring.application.name is required");
        }
        if (source.getEnvironment() == null || source.getEnvironment().isBlank()) {
            throw new SecretConfigurationException("SEC-BOOT-003", "Secret environment must not be blank");
        }
        if (properties.isStrictMode() && source.isLocalFallback()) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "local-fallback cannot be enabled in strict-mode");
        }
        if (properties.isStrictMode()
                && source.getMissingPolicy() != BootstrapSecretProperties.MissingPolicy.FAIL) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "strict-mode requires property-source.missing-policy=fail");
        }
        if (source.getMissingPolicy() == BootstrapSecretProperties.MissingPolicy.KEEP_LAST_GOOD) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "keep-last-good is a refresh policy and cannot be used for initial bootstrap");
        }
        if (source.getMissingPolicy() == BootstrapSecretProperties.MissingPolicy.LOCAL
                && !source.isLocalFallback()) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003",
                    "missing-policy=local requires local-fallback=true");
        }
    }

    private ClassLoader resolveClassLoader(SpringApplication application) {
        if (application.getResourceLoader() != null && application.getResourceLoader().getClassLoader() != null) {
            return application.getResourceLoader().getClassLoader();
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader == null ? getClass().getClassLoader() : contextClassLoader;
    }

    private SecretBootstrapRequest createRequest(
            BootstrapSecretProperties properties,
            ConfigurableEnvironment environment,
            SecretBindingCatalogSet catalogs) {
        BootstrapSecretProperties.PropertySource source = properties.getPropertySource();
        List<String> paths = new ArrayList<>();
        for (String scope : source.getPaths()) {
            if (!isSafeBundleSegment(scope)) {
                throw new SecretConfigurationException("SEC-BOOT-003", "Invalid Secret Bundle scope");
            }
            paths.add("atlas-richie/" + source.getEnvironment() + "/" + source.getApplication() + "/" + scope);
        }
        for (String profile : environment.getActiveProfiles()) {
            if (!isSafeBundleSegment(profile)) {
                throw new SecretConfigurationException("SEC-BOOT-003", "Invalid active Secret profile");
            }
            paths.add("atlas-richie/" + source.getEnvironment() + "/" + source.getApplication() + "/" + profile);
        }
        return new SecretBootstrapRequest(
                source.getApplication(),
                source.getEnvironment(),
                paths,
                catalogs.bindings());
    }

    private boolean isSafeBundleSegment(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_BUNDLE_SEGMENT_LENGTH
                || value.contains("..") || value.contains("/")) {
            return false;
        }
        return value.chars().noneMatch(Character::isISOControl);
    }

    private SecretBootstrapResult load(SecretBootstrapClient client, SecretBootstrapRequest request) {
        try {
            SecretBootstrapResult result = client.load(request);
            if (result == null) {
                throw new SecretBootstrapException("SEC-PROVIDER-001", "Secret Provider returned no snapshot");
            }
            return result;
        } catch (SecretBootstrapException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretBootstrapException(
                    "SEC-PROVIDER-001",
                    "Secret Provider failed to load the bootstrap snapshot",
                    exception);
        }
    }

    private void closeQuietly(SecretBootstrapClient client) {
        try {
            client.close();
        } catch (RuntimeException ignored) {
            // Do not mask the sanitized bootstrap failure.
        }
    }

    private Map<String, Object> filterAndValidate(
            Map<String, Object> values,
            SecretBindingCatalogSet catalogs,
            BootstrapSecretProperties properties,
            ConfigurableEnvironment environment) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String property = entry.getKey();
            java.util.Optional<SecretBinding> binding = catalogs.propertySourceBinding(property);
            if (binding.isPresent() && !isForbidden(property)) {
                validateLength(property, entry.getValue(), binding.get());
                filtered.put(property, entry.getValue());
            }
        }
        for (SecretBinding required : catalogs.requiredBindings(environment)) {
            if (!filtered.containsKey(required.property())) {
                if (canUseLocalValue(required, properties, environment)) {
                    continue;
                }
                throw new SecretBootstrapException(
                        "SEC-STORE-001",
                        "Required Secret is missing for property " + required.property());
            }
        }
        if (properties.getPropertySource().isRejectLocalDuplicates()) {
            for (String property : filtered.keySet()) {
                if (environment.containsProperty(property)) {
                    throw new SecretConfigurationException(
                            "SEC-BOOT-003",
                            "Managed Secret is also present in a local property source: " + property);
                }
            }
        }
        return Map.copyOf(filtered);
    }

    private boolean canUseLocalValue(
            SecretBinding binding,
            BootstrapSecretProperties properties,
            ConfigurableEnvironment environment) {
        BootstrapSecretProperties.PropertySource source = properties.getPropertySource();
        return source.getMissingPolicy() == BootstrapSecretProperties.MissingPolicy.LOCAL
                && source.isLocalFallback()
                && Boolean.TRUE.equals(binding.allowLocalWhenEnabled())
                && environment.containsProperty(binding.property());
    }

    private boolean isForbidden(String property) {
        return property.startsWith(BootstrapSecretProperties.PREFIX + ".")
                || FORBIDDEN_PROPERTIES.contains(property);
    }

    private void validateLength(String property, Object value, SecretBinding binding) {
        if (binding.maxLength() == null) return;
        int length = value instanceof byte[] bytes ? bytes.length : String.valueOf(value).length();
        if (length > binding.maxLength()) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-003", "Secret exceeds declared maximum length for property " + property);
        }
    }
}
