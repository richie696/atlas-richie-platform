/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Secret 启动信任域配置。此对象只包含连接和路由所需的非机密配置。
 */
@ConfigurationProperties(prefix = BootstrapSecretProperties.PREFIX)
public class BootstrapSecretProperties {
    public static final String PREFIX = "platform.component.secret";

    private boolean enabled;
    private boolean strictMode = true;
    private String activeProvider;
    private PropertySource propertySource = new PropertySource();
    private Map<String, Provider> providers = new LinkedHashMap<>();
    private Map<String, String> routing = new LinkedHashMap<>();
    private Resilience resilience = new Resilience();
    private Refresh refresh = new Refresh();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(String activeProvider) {
        this.activeProvider = activeProvider;
    }

    public PropertySource getPropertySource() {
        return propertySource;
    }

    public void setPropertySource(PropertySource propertySource) {
        this.propertySource = propertySource == null ? new PropertySource() : propertySource;
    }

    public Map<String, Provider> getProviders() {
        return Map.copyOf(providers);
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providers);
    }

    public Map<String, String> getRouting() {
        return Map.copyOf(routing);
    }

    public void setRouting(Map<String, String> routing) {
        this.routing = routing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(routing);
    }

    public Resilience getResilience() {
        return resilience;
    }

    public void setResilience(Resilience resilience) {
        this.resilience = resilience == null ? new Resilience() : resilience;
    }

    public Refresh getRefresh() {
        return refresh;
    }

    public void setRefresh(Refresh refresh) {
        this.refresh = refresh == null ? new Refresh() : refresh;
    }

    /**
     * PropertySource 路径与缺失策略。
     */
    public static class PropertySource {
        private String application;
        private String environment = "dev";
        private List<String> paths = new ArrayList<>(List.of("common", "components"));
        private MissingPolicy missingPolicy = MissingPolicy.FAIL;
        private boolean localFallback;
        private boolean rejectLocalDuplicates;

        public String getApplication() {
            return application;
        }

        public void setApplication(String application) {
            this.application = application;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public List<String> getPaths() {
            return List.copyOf(paths);
        }

        public void setPaths(List<String> paths) {
            this.paths = paths == null ? new ArrayList<>() : new ArrayList<>(paths);
        }

        public MissingPolicy getMissingPolicy() {
            return missingPolicy;
        }

        public void setMissingPolicy(MissingPolicy missingPolicy) {
            this.missingPolicy = missingPolicy == null ? MissingPolicy.FAIL : missingPolicy;
        }

        public boolean isLocalFallback() {
            return localFallback;
        }

        public void setLocalFallback(boolean localFallback) {
            this.localFallback = localFallback;
        }

        public boolean isRejectLocalDuplicates() {
            return rejectLocalDuplicates;
        }

        public void setRejectLocalDuplicates(boolean rejectLocalDuplicates) {
            this.rejectLocalDuplicates = rejectLocalDuplicates;
        }
    }

    /**
     * 命名 Provider 配置的公共字段，专属配置仍由 Provider 从 Environment 绑定。
     */
    public static class Provider {
        private String type;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * 启动期网络边界。
     */
    public static class Resilience {
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(5);
        private int maxAttempts = 3;

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    /**
     * 运行期完整 Bundle 轮询配置。只有全局 Secret 开启时才会创建调度器。
     */
    public static class Refresh {
        private boolean enabled = true;
        private Duration initialDelay = Duration.ofMinutes(1);
        private Duration interval = Duration.ofMinutes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }
    }

    public enum MissingPolicy {
        FAIL,
        KEEP_LAST_GOOD,
        LOCAL
    }
}
